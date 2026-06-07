package software.spool.watchdog.architecture;

import software.spool.core.utils.polling.CancellationToken;
import software.spool.watchdog.architecture.port.output.WatchdogServer;

/**
 * Top-level orchestrator that starts and stops the watchdog subsystem.
 * <p>
 * Coordinates the {@link WatchdogServer} (network layer) and {@link WatchdogMonitor}
 * (background monitoring loop) as a single lifecycle unit. Idempotent: calling
 * {@link #start} on an already-running instance or {@link #stop} on a stopped one is a no-op.
 */
public class Watchdog {
    private final WatchdogServer server;
    private final WatchdogMonitor monitor;
    private volatile CancellationToken token;


    public Watchdog(WatchdogServer server, WatchdogMonitor monitor) {
        this.server = server;
        this.monitor = monitor;
        this.token = CancellationToken.NOOP;
    }

    public void start() {
        if (token.isActive()) return;
        token = CancellationToken.create();
        this.server.start();
        this.monitor.start();
    }

    public void stop() {
        if (token.isCancelled()) return;
        token.cancel();
        token = CancellationToken.NOOP;
        monitor.stop();
        server.stop();
    }
}
