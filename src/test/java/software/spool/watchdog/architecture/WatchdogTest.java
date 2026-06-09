package software.spool.watchdog.architecture;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.spool.core.model.watchdog.ModuleIdentity;
import software.spool.core.port.metrics.MetricsRegistry;
import software.spool.watchdog.application.adapter.output.InMemoryInbox;
import software.spool.watchdog.application.adapter.output.InMemoryModuleRegistry;
import software.spool.watchdog.architecture.port.output.ModuleObserver;
import software.spool.watchdog.architecture.port.output.WatchdogServer;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class WatchdogTest {

    private AtomicInteger serverStarts;
    private AtomicInteger serverStops;
    private WatchdogServer server;
    private Watchdog watchdog;

    @BeforeEach
    void setUp() {
        serverStarts = new AtomicInteger();
        serverStops = new AtomicInteger();
        server = new WatchdogServer() {
            @Override public void start() { serverStarts.incrementAndGet(); }
            @Override public void stop() { serverStops.incrementAndGet(); }
        };
        watchdog = new Watchdog(server, noopMonitor());
    }

    @Test
    void start_startsServer() {
        watchdog.start();
        assertThat(serverStarts.get()).isEqualTo(1);
    }

    @Test
    void start_idempotent_doesNotStartTwice() {
        watchdog.start();
        watchdog.start();
        assertThat(serverStarts.get()).isEqualTo(1);
    }

    @Test
    void stop_afterStart_stopsServer() {
        watchdog.start();
        watchdog.stop();
        assertThat(serverStops.get()).isEqualTo(1);
    }

    private static WatchdogMonitor noopMonitor() {
        ModuleObserver noopObserver = new ModuleObserver() {
            @Override public void onModuleStarted(ModuleIdentity id) {}
            @Override public void onModuleDegraded(ModuleIdentity id, String r) {}
            @Override public void onModuleRecovered(ModuleIdentity id, Duration d) {}
            @Override public void onModuleFinished(ModuleIdentity id, String r) {}
        };
        return new WatchdogMonitor(
            new InMemoryModuleRegistry(), new InMemoryInbox(), noopObserver,
            (task, policy, token) -> {},
            Duration.ofSeconds(30), Duration.ofMinutes(5), MetricsRegistry.NOOP
        );
    }
}
