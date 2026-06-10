package software.spool.watchdog.architecture;

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
        registry.save(new ModuleState(identity, Instant.now().minusSeconds(10), ModuleStatus.HEALTHY));
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

    private static ModuleObserver noopObserver() {
        return new ModuleObserver() {
            @Override public void onModuleStarted(ModuleIdentity id) {}
            @Override public void onModuleDegraded(ModuleIdentity id, String r) {}
            @Override public void onModuleRecovered(ModuleIdentity id, Duration d) {}
            @Override public void onModuleFinished(ModuleIdentity id, String r) {}
        };
    }
}
