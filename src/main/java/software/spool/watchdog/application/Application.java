package software.spool.watchdog.application;

import software.spool.core.utils.polling.PollingScheduler;
import software.spool.core.utils.polling.ThreadedPollingScheduler;
import software.spool.watchdog.application.adapter.input.http.HTTPWatchdogServer;
import software.spool.watchdog.application.adapter.output.EnvModuleConfigurationProvider;
import software.spool.watchdog.application.adapter.output.InMemoryModuleRegistry;
import software.spool.watchdog.application.adapter.output.LogAlertEmitter;
import software.spool.watchdog.architecture.Watchdog;
import software.spool.watchdog.architecture.WatchdogMonitor;
import software.spool.watchdog.architecture.WatchdogService;
import software.spool.watchdog.architecture.port.output.AlertEmitter;
import software.spool.watchdog.architecture.port.output.ModuleConfigurationProvider;
import software.spool.watchdog.architecture.port.output.ModuleRegistry;
import software.spool.watchdog.architecture.port.output.WatchdogServer;

public class Application {
    private final ModuleRegistry registry;
    private final ModuleConfigurationProvider configurationProvider;
    private final WatchdogService service;
    private final int port;
    private final WatchdogServer server;
    private final AlertEmitter emitter;
    private final WatchdogMonitor monitor;
    private final Watchdog watchdog;

    public Application() {
        this.registry = initializeRegistry();
        this.port = initializePort();
        this.configurationProvider = initializeConfigurationProvider();
        this.service = initializeService();
        this.server = initializeServer();
        this.emitter = initializeEmitter();
        this.monitor = initializeMonitor();
        this.watchdog = initializeWatchdog();
    }

    private Watchdog initializeWatchdog() {
        return new Watchdog(server, monitor);
    }

    private ModuleConfigurationProvider initializeConfigurationProvider() {
        return new EnvModuleConfigurationProvider();
    }

    private AlertEmitter initializeEmitter() {
        return new LogAlertEmitter();
    }

    private WatchdogMonitor initializeMonitor() {
        return new WatchdogMonitor(registry, emitter, createPollingScheduler(), createPollingScheduler());
    }

    private PollingScheduler createPollingScheduler() {
        return new ThreadedPollingScheduler();
    }

    private WatchdogServer initializeServer() {
        return new HTTPWatchdogServer(service, port);
    }

    private WatchdogService initializeService() {
        return new WatchdogService(registry, configurationProvider);
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
