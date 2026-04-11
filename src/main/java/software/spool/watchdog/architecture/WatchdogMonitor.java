package software.spool.watchdog.architecture;

import software.spool.core.model.watchdog.ModuleIdentity;
import software.spool.core.model.watchdog.ModuleState;
import software.spool.core.model.watchdog.ModuleStatus;
import software.spool.core.utils.polling.CancellationToken;
import software.spool.core.utils.polling.PollingPolicy;
import software.spool.core.utils.polling.PollingScheduler;
import software.spool.watchdog.architecture.port.output.Inbox;
import software.spool.watchdog.architecture.port.output.ModuleObserver;
import software.spool.watchdog.architecture.port.output.ModuleRegistry;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class WatchdogMonitor {
    private final ModuleRegistry registry;
    private final Inbox inbox;
    private final ModuleObserver moduleObserver;
    private final PollingScheduler statusScheduler;
    private volatile CancellationToken token;

    private final Map<ModuleIdentity, Instant> downSince = new ConcurrentHashMap<>();
    private final Map<ModuleIdentity, ModuleStatus> lastReported = new ConcurrentHashMap<>();

    private static final Duration MODULE_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration ZOMBIE_TIMEOUT = Duration.ofMinutes(5);

    public WatchdogMonitor(ModuleRegistry registry, Inbox inbox, ModuleObserver moduleObserver, PollingScheduler statusScheduler) {
        this.registry = registry;
        this.inbox = inbox;
        this.moduleObserver = moduleObserver;
        this.statusScheduler = statusScheduler;
        this.token = CancellationToken.NOOP;
    }

    public void start() {
        if (token.isActive()) return;
        token = CancellationToken.create();
        statusScheduler.schedule(this::check, PollingPolicy.every(Duration.ofSeconds(1)), token);
    }

    public void stop() {
        if (token.isCancelled()) return;
        token.cancel();
        token = CancellationToken.NOOP;
    }

    private void check() {
        try {
            inbox.read().forEach(reported -> {
                lastReported.put(reported.identity(), reported.status());
                registry.find(reported.identity()).ifPresentOrElse(
                        existing -> registry.update(existing.seenNow()),
                        () -> {
                            registry.save(ModuleState.of(reported.identity()));
                            moduleObserver.onModuleStarted(reported.identity());
                        }
                );
            });
            Instant now = Instant.now();
            handleZombies();
            registry.findAll().forEach(module -> {
                Duration silence = Duration.between(module.lastSeen(), now);
                boolean timedOut   = silence.compareTo(MODULE_TIMEOUT) > 0;
                boolean isDegraded = module.status() == ModuleStatus.DEGRADED;
                if (timedOut && !isDegraded) {
                    registry.update(module.status(ModuleStatus.DEGRADED));
                    downSince.put(module.identity(), now);
                    moduleObserver.onModuleDegraded(module.identity(), silence);
                } else if (!timedOut && isDegraded) {
                    Instant wentDown = downSince.remove(module.identity());
                    Duration downtime = wentDown != null ? Duration.between(wentDown, now) : Duration.ZERO;
                    ModuleStatus recoveredAs = lastReported.getOrDefault(module.identity(), ModuleStatus.HEALTHY);
                    registry.update(module.status(recoveredAs));
                    moduleObserver.onModuleRecovered(module.identity(), downtime);
                }
            });
        } catch (Exception e) {
            System.err.println("Error during watchdog check: " + e);
        }
    }

    private void handleZombies() {
        Instant now = Instant.now();
        registry.findAll().stream()
                .filter(module -> Duration.between(module.lastSeen(), now).compareTo(ZOMBIE_TIMEOUT) > 0)
                .map(ModuleState::identity)
                .toList()
                .forEach(m -> {
                    registry.remove(m);
                    downSince.remove(m);
                    lastReported.remove(m);
                    moduleObserver.onModuleFinished(m);
                });
    }
}