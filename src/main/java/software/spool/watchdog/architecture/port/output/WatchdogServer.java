package software.spool.watchdog.architecture.port.output;

/**
 * Output port representing the network server that exposes the watchdog API.
 * <p>
 * The concrete implementation ({@code HTTPWatchdogServer}) binds an HTTP server
 * with {@code POST /heartbeat} and {@code GET /health} endpoints.
 */
public interface WatchdogServer {
    void start();
    void stop();
}
