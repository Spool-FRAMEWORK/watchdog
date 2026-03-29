package software.spool.watchdog;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.spool.watchdog.model.ModuleStatus;
import software.spool.watchdog.port.output.AlertEmitter;
import software.spool.watchdog.port.output.ModuleRegistry;
import software.spool.watchdog.port.output.RecoveryAction;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class WatchdogMonitor {
    private static final Logger log = LoggerFactory.getLogger(WatchdogMonitor.class);

    private final ModuleRegistry registry;
    private final RecoveryAction recoveryAction;
    private final AlertEmitter alertEmitter;
    private final ScheduledExecutorService scheduler;
    private final Map<String, Instant> downSince = new ConcurrentHashMap<>();

    public WatchdogMonitor(ModuleRegistry registry,
                           RecoveryAction recoveryAction,
                           AlertEmitter alertEmitter) {
        this.registry = registry;
        this.recoveryAction = recoveryAction;
        this.alertEmitter = alertEmitter;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "watchdog-monitor");
            t.setDaemon(true);
            return t;
        });
    }

    public void start() {
        scheduler.scheduleAtFixedRate(this::check, 0, 1, TimeUnit.SECONDS);
    }

    public void stop() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) scheduler.shutdownNow();
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
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
                        recoveryAction.recover(module.registration(), silence);
                    }
                } else if (module.status() == ModuleStatus.UNHEALTHY) {
                    Instant wentDown = downSince.remove(module.moduleId());
                    Duration downtime = wentDown != null ? Duration.between(wentDown, now) : Duration.ZERO;
                    module.setStatus(ModuleStatus.HEALTHY);
                    alertEmitter.moduleRecovered(module.registration(), downtime);
                }
            });
        } catch (Exception e) {
            log.error("Error during watchdog check", e);
        }
    }
}