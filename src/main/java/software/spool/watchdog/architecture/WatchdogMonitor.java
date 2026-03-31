package software.spool.watchdog.architecture;

import software.spool.core.model.watchdog.ModuleStatus;
import software.spool.core.utils.polling.CancellationToken;
import software.spool.core.utils.polling.PollingPolicy;
import software.spool.core.utils.polling.PollingScheduler;
import software.spool.watchdog.architecture.port.output.AlertEmitter;
import software.spool.watchdog.architecture.port.output.ModuleRegistry;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class WatchdogMonitor {
    private final ModuleRegistry registry;
    private final AlertEmitter alertEmitter;
    private final PollingScheduler statusScheduler;
    private final PollingScheduler zombieScheduler;
    private volatile CancellationToken token;
    private final Map<String, Instant> downSince = new ConcurrentHashMap<>();

    public WatchdogMonitor(ModuleRegistry registry, AlertEmitter alertEmitter, PollingScheduler statusScheduler, PollingScheduler zombieScheduler) {
        this.registry = registry;
        this.alertEmitter = alertEmitter;
        this.statusScheduler = statusScheduler;
        this.zombieScheduler = zombieScheduler;
        this.token = CancellationToken.NOOP;
    }

    public void start() {
        if (token.isActive()) return;
        statusScheduler.schedule(this::check, PollingPolicy.every(Duration.ofSeconds(1)), token);
        zombieScheduler.schedule(registry::handleDownModule, PollingPolicy.every(Duration.ofMinutes(5)), token);
    }

    public void stop() {
        if (token.isCancelled()) return;
        token.cancel();
        token = CancellationToken.NOOP;
    }

    private void check() {
        try {
            Instant now = Instant.now();
            registry.findAll().forEach(module -> {
                Duration silence = Duration.between(module.lastSeen(), now);
                if (silence.compareTo(module.timeout()) > 0) {
                    if (module.status() != ModuleStatus.UNHEALTHY) {
                        module.setStatus(ModuleStatus.UNHEALTHY);
                        downSince.put(module.moduleId(), now);
                        alertEmitter.moduleDown(module.registration(), silence);
                    }
                } else if (module.status() == ModuleStatus.UNHEALTHY) {
                    Instant wentDown = downSince.remove(module.moduleId());
                    Duration downtime = wentDown != null ? Duration.between(wentDown, now) : Duration.ZERO;
                    module.setStatus(ModuleStatus.HEALTHY);
                    alertEmitter.moduleRecovered(module.registration(), downtime);
                }
            });
        } catch (Exception e) {
            System.out.println("Error during watchdog check: " + e.getMessage());
        }
    }
}