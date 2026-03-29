package software.spool.watchdog;

import software.spool.watchdog.adapter.input.http.WatchdogHttpServer;
import software.spool.watchdog.adapter.output.*;

public class Main {
    public static void main(String[] args) throws InterruptedException {
        int port = Integer.parseInt(System.getenv().getOrDefault("WATCHDOG_PORT", "8090"));

        InMemoryModuleRegistry registry   = new InMemoryModuleRegistry();
        LogAlertEmitter alertEmitter      = new LogAlertEmitter();
        NoOpRecoveryAction recoveryAction = new NoOpRecoveryAction();

        WatchdogService service = new WatchdogService(registry);

        WatchdogHttpServer httpServer = new WatchdogHttpServer(service, service, service, port);
        WatchdogMonitor monitor       = new WatchdogMonitor(registry, recoveryAction, alertEmitter);
        monitor.start();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Shutting down watchdog...");
            monitor.stop();
            httpServer.stop();
        }, "shutdown-hook"));

        System.out.println("Watchdog running on: " + port);
        Thread.currentThread().join();
    }
}
