package software.spool.watchdog.application;

import software.spool.core.adapter.otel.OpenTelemetryModuleLogger;
import software.spool.core.utils.polling.ThreadedPollingScheduler;
import software.spool.watchdog.application.adapter.input.http.HTTPWatchdogServer;
import software.spool.watchdog.application.adapter.output.InMemoryInbox;
import software.spool.watchdog.application.adapter.output.InMemoryModuleRegistry;
import software.spool.watchdog.application.adapter.output.OpenTelemetryModuleObserver;
import software.spool.watchdog.architecture.Watchdog;
import software.spool.watchdog.architecture.WatchdogMonitor;
import software.spool.watchdog.architecture.WatchdogService;
import software.spool.watchdog.architecture.port.output.Inbox;
import software.spool.watchdog.architecture.port.output.ModuleObserver;
import software.spool.watchdog.architecture.port.output.ModuleRegistry;
import software.spool.watchdog.architecture.port.output.WatchdogServer;

import java.time.Duration;

public class Application {
    private final Inbox inbox;
    private final ModuleRegistry registry;
    private final WatchdogService service;
    private final int port;
    private final WatchdogServer server;
    private final ModuleObserver emitter;
    private final WatchdogMonitor monitor;
    private final Watchdog watchdog;

    public Application() {
        this.inbox = initializeInbox();
        this.registry = initializeRegistry();
        this.port = initializePort();
        this.service = initializeService();
        this.server = initializeServer();
        this.emitter = initializeEmitter();
        this.monitor = initializeMonitor();
        this.watchdog = initializeWatchdog();
    }

    private Inbox initializeInbox() {
        return new InMemoryInbox();
    }

    private Watchdog initializeWatchdog() {
        return new Watchdog(server, monitor);
    }

    private ModuleObserver initializeEmitter() {
        return new OpenTelemetryModuleObserver(new OpenTelemetryModuleLogger());
    }

    private WatchdogMonitor initializeMonitor() {
        long moduleTimeoutSec = Long.parseLong(System.getenv()
                .getOrDefault("MODULE_TIMEOUT_SECONDS", "30"));
        long zombieTimeoutSec = Long.parseLong(System.getenv()
                .getOrDefault("ZOMBIE_TIMEOUT_SECONDS", "300"));
        return new WatchdogMonitor(
                registry,
                inbox,
                emitter,
                new ThreadedPollingScheduler(),
                Duration.ofSeconds(moduleTimeoutSec),
                Duration.ofSeconds(zombieTimeoutSec)
        );
    }

    private WatchdogServer initializeServer() {
        return new HTTPWatchdogServer(service, port);
    }

    private WatchdogService initializeService() {
        return new WatchdogService(inbox, registry);
    }

    private int initializePort() {
        return Integer.parseInt(System.getenv().getOrDefault("WATCHDOG_PORT", "8090"));
    }

    private ModuleRegistry initializeRegistry() {
        return new InMemoryModuleRegistry();
    }

    public void run() {
        watchdog.start();
    }

    public void shutdown() {
        watchdog.stop();
    }
}
