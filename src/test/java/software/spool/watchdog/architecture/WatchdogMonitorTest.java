package software.spool.watchdog.architecture;

import java.util.Optional;
import software.spool.core.port.health.PieceHealth;
import software.spool.core.port.health.HealthStatus;
import org.junit.jupiter.api.Test;
import software.spool.core.model.watchdog.ModuleIdentity;
import software.spool.core.model.watchdog.ModuleState;
import software.spool.core.model.watchdog.ModuleStatus;
import software.spool.core.port.metrics.MetricsRegistry;
import software.spool.watchdog.application.adapter.output.InMemoryInbox;
import software.spool.watchdog.application.adapter.output.InMemoryModuleRegistry;
import software.spool.watchdog.architecture.port.output.ModuleObserver;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class WatchdogMonitorTest {
    private static final PieceHealth LAKE_DOWN = new PieceHealth("lake", "S3", HealthStatus.UNHEALTHY,
            Optional.of("the bucket does not exist"), Instant.parse("2026-11-22T10:00:00Z"), true);

    @Test
    void check_newHeartbeat_registersModule() {
        InMemoryModuleRegistry registry = new InMemoryModuleRegistry();
        InMemoryInbox inbox = new InMemoryInbox();
        ModuleIdentity identity = ModuleIdentity.of("module-1");
        inbox.write(ModuleState.of(identity));

        WatchdogMonitor monitor = new WatchdogMonitor(
            registry, inbox, noopObserver(),
            (task, policy, token) -> task.run(),
            Duration.ofSeconds(30), Duration.ofMinutes(5), MetricsRegistry.NOOP
        );
        monitor.start();

        assertThat(registry.find(identity)).isPresent();
    }

    @Test
    void check_moduleTimedOut_notifiesObserver() {
        InMemoryModuleRegistry registry = new InMemoryModuleRegistry();
        InMemoryInbox inbox = new InMemoryInbox();
        ModuleIdentity identity = ModuleIdentity.of("module-2");
        registry.save(new ModuleState(identity, Instant.now().minusSeconds(10), ModuleStatus.HEALTHY, List.of()));
        List<ModuleIdentity> degraded = new ArrayList<>();
        ModuleObserver observer = new ModuleObserver() {
            @Override public void onModuleStarted(ModuleIdentity id) {}
            @Override public void onModuleDegraded(ModuleIdentity id, String r) { degraded.add(id); }
            @Override public void onModuleRecovered(ModuleIdentity id, Duration d) {}
            @Override public void onModuleFinished(ModuleIdentity id, String r) {}
        };

        WatchdogMonitor monitor = new WatchdogMonitor(
            registry, inbox, observer,
            (task, policy, token) -> task.run(),
            Duration.ofMillis(1), Duration.ofDays(1), MetricsRegistry.NOOP
        );
        monitor.start();

        assertThat(degraded).containsExactly(identity);
    }

    @Test
    void check_heartbeatOfANewModule_keepsTheStatusAndThePiecesItReported() {
        InMemoryModuleRegistry registry = new InMemoryModuleRegistry();
        InMemoryInbox inbox = new InMemoryInbox();
        ModuleIdentity identity = ModuleIdentity.of("module-3");
        inbox.write(ModuleState.of(identity).status(ModuleStatus.UNHEALTHY).pieces(List.of(LAKE_DOWN)));

        monitorOf(registry, inbox, noopObserver()).start();

        assertThat(registry.find(identity)).hasValueSatisfying(state -> {
            assertThat(state.status()).isEqualTo(ModuleStatus.UNHEALTHY);
            assertThat(state.pieces()).containsExactly(LAKE_DOWN);
        });
    }

    @Test
    void check_heartbeatOfAKnownModule_keepsTheStatusAndThePiecesItReported() {
        InMemoryModuleRegistry registry = new InMemoryModuleRegistry();
        InMemoryInbox inbox = new InMemoryInbox();
        ModuleIdentity identity = ModuleIdentity.of("module-4");
        registry.save(ModuleState.of(identity));
        inbox.write(ModuleState.of(identity).status(ModuleStatus.UNHEALTHY).pieces(List.of(LAKE_DOWN)));

        monitorOf(registry, inbox, noopObserver()).start();

        assertThat(registry.find(identity)).hasValueSatisfying(state -> {
            assertThat(state.status()).isEqualTo(ModuleStatus.UNHEALTHY);
            assertThat(state.pieces()).containsExactly(LAKE_DOWN);
        });
    }

    @Test
    void check_moduleThatReportsItselfDegraded_isNotTakenForOneThatRecovered() {
        InMemoryModuleRegistry registry = new InMemoryModuleRegistry();
        InMemoryInbox inbox = new InMemoryInbox();
        ModuleIdentity identity = ModuleIdentity.of("module-5");
        registry.save(ModuleState.of(identity));
        inbox.write(ModuleState.of(identity).status(ModuleStatus.DEGRADED));
        List<ModuleIdentity> recovered = new ArrayList<>();
        ModuleObserver observer = new ModuleObserver() {
            @Override public void onModuleStarted(ModuleIdentity id) {}
            @Override public void onModuleDegraded(ModuleIdentity id, String r) {}
            @Override public void onModuleRecovered(ModuleIdentity id, Duration d) { recovered.add(id); }
            @Override public void onModuleFinished(ModuleIdentity id, String r) {}
        };

        monitorOf(registry, inbox, observer).start();

        assertThat(recovered).isEmpty();
        assertThat(registry.find(identity)).hasValueSatisfying(state -> assertThat(state.status()).isEqualTo(ModuleStatus.DEGRADED));
    }

    @Test
    void check_moduleThatReportsADifferentStatus_notifiesTheChangeWithItsPieces() {
        InMemoryModuleRegistry registry = new InMemoryModuleRegistry();
        InMemoryInbox inbox = new InMemoryInbox();
        ModuleIdentity identity = ModuleIdentity.of("module-4");
        List<String> changes = new ArrayList<>();
        WatchdogMonitor monitor = monitorOf(registry, inbox, recordingChanges(changes));

        inbox.write(ModuleState.of(identity));
        checkOnce(monitor);
        inbox.write(ModuleState.of(identity).status(ModuleStatus.UNHEALTHY).pieces(List.of(LAKE_DOWN)));
        checkOnce(monitor);

        assertThat(changes).containsExactly("module-4 HEALTHY->UNHEALTHY [lake]");
    }

    @Test
    void check_moduleThatReportsTheSameStatusAgain_notifiesItOnce() {
        InMemoryModuleRegistry registry = new InMemoryModuleRegistry();
        InMemoryInbox inbox = new InMemoryInbox();
        ModuleIdentity identity = ModuleIdentity.of("module-5");
        List<String> changes = new ArrayList<>();
        WatchdogMonitor monitor = monitorOf(registry, inbox, recordingChanges(changes));

        inbox.write(ModuleState.of(identity).status(ModuleStatus.UNHEALTHY).pieces(List.of(LAKE_DOWN)));
        checkOnce(monitor);
        inbox.write(ModuleState.of(identity).status(ModuleStatus.UNHEALTHY).pieces(List.of(LAKE_DOWN)));
        checkOnce(monitor);

        assertThat(changes).containsExactly("module-5 HEALTHY->UNHEALTHY [lake]");
    }

    @Test
    void check_newModuleThatReportsItIsHealthy_notifiesNoChange() {
        InMemoryModuleRegistry registry = new InMemoryModuleRegistry();
        InMemoryInbox inbox = new InMemoryInbox();
        List<String> changes = new ArrayList<>();

        inbox.write(ModuleState.of(ModuleIdentity.of("module-6")));
        checkOnce(monitorOf(registry, inbox, recordingChanges(changes)));

        assertThat(changes).isEmpty();
    }

    private static void checkOnce(WatchdogMonitor monitor) {
        monitor.stop();
        monitor.start();
    }

    private static ModuleObserver recordingChanges(List<String> changes) {
        return new ModuleObserver() {
            @Override public void onModuleStarted(ModuleIdentity id) {}
            @Override public void onModuleDegraded(ModuleIdentity id, String r) {}
            @Override public void onModuleRecovered(ModuleIdentity id, Duration d) {}
            @Override public void onModuleFinished(ModuleIdentity id, String r) {}
            @Override public void onModuleStatusChanged(ModuleIdentity id, ModuleStatus from, ModuleStatus to, List<PieceHealth> pieces) {
                changes.add(id.moduleId() + " " + from + "->" + to + " " + pieces.stream().map(PieceHealth::piece).toList());
            }
        };
    }

    private static WatchdogMonitor monitorOf(InMemoryModuleRegistry registry, InMemoryInbox inbox, ModuleObserver observer) {
        return new WatchdogMonitor(registry, inbox, observer, (task, policy, token) -> task.run(),
                Duration.ofSeconds(30), Duration.ofMinutes(5), MetricsRegistry.NOOP);
    }

    private static ModuleObserver noopObserver() {
        return new ModuleObserver() {
            @Override public void onModuleStarted(ModuleIdentity id) {}
            @Override public void onModuleDegraded(ModuleIdentity id, String r) {}
            @Override public void onModuleRecovered(ModuleIdentity id, Duration d) {}
            @Override public void onModuleFinished(ModuleIdentity id, String r) {}
        };
    }
}
