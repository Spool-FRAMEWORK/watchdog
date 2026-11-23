package software.spool.watchdog.application;

import software.spool.core.adapter.otel.OpenTelemetryMetricsRegistry;
import software.spool.core.adapter.otel.OpenTelemetryModuleLogger;
import software.spool.core.port.metrics.MetricsRegistry;
import software.spool.core.port.metrics.SpoolMetrics;
import software.spool.core.utils.polling.ThreadedPollingScheduler;
import software.spool.watchdog.application.adapter.input.http.HTTPWatchdogServer;
import software.spool.watchdog.application.adapter.output.InMemoryInbox;
import software.spool.watchdog.application.adapter.output.InMemoryModuleRegistry;
import software.spool.watchdog.application.adapter.output.OpenTelemetryModuleObserver;
import software.spool.watchdog.application.adapter.output.WebhookModuleObserver;
import software.spool.watchdog.architecture.Watchdog;
import software.spool.watchdog.architecture.WatchdogMonitor;
import software.spool.watchdog.architecture.WatchdogService;
import software.spool.watchdog.architecture.port.output.Inbox;
import software.spool.watchdog.architecture.port.output.ModuleObserver;
import software.spool.watchdog.architecture.port.output.ModuleObservers;
import software.spool.watchdog.architecture.port.output.ModuleRegistry;
import software.spool.watchdog.architecture.port.output.WatchdogServer;

import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;

public class Application {
    static final String ALERT_WEBHOOK_URL = "WATCHDOG_ALERT_WEBHOOK_URL";

    private final Inbox inbox;
    private final ModuleRegistry registry;
    private final WatchdogService service;
    private final int port;
    private final WatchdogServer server;
    private final ModuleObserver emitter;
    private final WatchdogMonitor monitor;
    private final Watchdog watchdog;
    private final Map<String, String> environment;

    /** A watchdog configured by the given environment variables, such as {@code WATCHDOG_PORT}. */
    public Application(Map<String, String> environment) {
        this.environment = Map.copyOf(environment);
        MetricsRegistry metrics = new OpenTelemetryMetricsRegistry();
        MetricsRegistry.CounterMetric heartbeats = metrics.counter(SpoolMetrics.Watchdog.HEARTBEATS_TOTAL, SpoolMetrics.Watchdog.HEARTBEATS_TOTAL_DESC, "1");
        this.inbox = initializeInbox();
        this.registry = initializeRegistry();
        this.port = initializePort();
        this.service = initializeService(heartbeats);
        this.server = initializeServer();
        this.emitter = initializeEmitter();
        this.monitor = initializeMonitor(metrics);
        this.watchdog = initializeWatchdog();
    }

    private Inbox initializeInbox() {
        return new InMemoryInbox();
    }

    private Watchdog initializeWatchdog() {
        return new Watchdog(server, monitor);
    }

    private ModuleObserver initializeEmitter() {
        ModuleObserver log = new OpenTelemetryModuleObserver(new OpenTelemetryModuleLogger());
        return Optional.ofNullable(environment.get(ALERT_WEBHOOK_URL))
                .filter(url -> !url.isBlank())
                .<ModuleObserver>map(url -> ModuleObservers.of(log, new WebhookModuleObserver(URI.create(url))))
                .orElse(log);
    }

    private WatchdogMonitor initializeMonitor(MetricsRegistry metrics) {
        long moduleTimeoutSec = Long.parseLong(environment.getOrDefault("MODULE_TIMEOUT_SECONDS", "30"));
        long zombieTimeoutSec = Long.parseLong(environment.getOrDefault("ZOMBIE_TIMEOUT_SECONDS", "300"));
        return new WatchdogMonitor(
                registry,
                inbox,
                emitter,
                new ThreadedPollingScheduler(),
                Duration.ofSeconds(moduleTimeoutSec),
                Duration.ofSeconds(zombieTimeoutSec),
                metrics
        );
    }

    private WatchdogServer initializeServer() {
        return new HTTPWatchdogServer(service, port);
    }

    private WatchdogService initializeService(MetricsRegistry.CounterMetric heartbeats) {
        return new WatchdogService(inbox, registry, heartbeats);
    }

    private int initializePort() {
        return Integer.parseInt(environment.getOrDefault("WATCHDOG_PORT", "8090"));
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
