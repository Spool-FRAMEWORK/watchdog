package software.spool.watchdog.architecture;

import software.spool.core.model.watchdog.ModuleIdentity;
import software.spool.core.model.watchdog.ModuleState;
import software.spool.core.model.watchdog.ModuleStatus;
import software.spool.core.port.metrics.MetricsRegistry;
import software.spool.core.port.metrics.SpoolMetrics;
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

/**
 * Background monitor that runs a periodic health-check loop (every second) to detect
 * two failure modes:
 * <ul>
 *   <li><b>Timeout</b> — a module that stops sending heartbeats for longer than
 *       {@code moduleTimeout} is marked {@code DEGRADED} and triggers
 *       {@link ModuleObserver#onModuleDegraded}.</li>
 *   <li><b>Zombie</b> — a module that remains silent beyond {@code zombieTimeout} is
 *       permanently removed from the registry via {@link ModuleObserver#onModuleFinished}.</li>
 * </ul>
 * Both thresholds are configurable via environment variables
 * ({@code MODULE_TIMEOUT_SECONDS}, {@code ZOMBIE_TIMEOUT_SECONDS}).
 */
public class WatchdogMonitor {
    private final ModuleRegistry registry;
    private final Inbox inbox;
    private final ModuleObserver moduleObserver;
    private final PollingScheduler statusScheduler;
    private volatile CancellationToken token;
    private final Map<ModuleIdentity, Instant> downSince = new ConcurrentHashMap<>();
    private final Map<ModuleIdentity, ModuleStatus> lastReported = new ConcurrentHashMap<>();
    private final Duration moduleTimeout;
    private final Duration zombieTimeout;
    private final MetricsRegistry.CounterMetric timeouts;
    private final MetricsRegistry.CounterMetric zombies;
    private final MetricsRegistry.TimerMetric checkTimer;
    private final MetricsRegistry.CounterMetric moduleStarted;
    private final MetricsRegistry.CounterMetric moduleStopped;
    private final MetricsRegistry.CounterMetric moduleDegraded;
    private final MetricsRegistry.GaugeMetric modulesActive;
    private final MetricsRegistry.TimerMetric downtimeDuration;
    private final MetricsRegistry.LongHistogramMetric healthyRatio;

    public WatchdogMonitor(ModuleRegistry registry, Inbox inbox, ModuleObserver moduleObserver, PollingScheduler statusScheduler, Duration moduleTimeout, Duration zombieTimeout, MetricsRegistry metrics) {
        this.registry = registry;
        this.inbox = inbox;
        this.moduleObserver = moduleObserver;
        this.statusScheduler = statusScheduler;
        this.moduleTimeout = moduleTimeout;
        this.zombieTimeout = zombieTimeout;
        this.token = CancellationToken.NOOP;
        this.timeouts = metrics.counter(SpoolMetrics.Watchdog.TIMEOUTS_TOTAL, SpoolMetrics.Watchdog.TIMEOUTS_TOTAL_DESC, "1");
        this.zombies = metrics.counter(SpoolMetrics.Watchdog.ZOMBIES_TOTAL, SpoolMetrics.Watchdog.ZOMBIES_TOTAL_DESC, "1");
        this.checkTimer = metrics.timer(SpoolMetrics.Watchdog.CHECK_DURATION, SpoolMetrics.Watchdog.CHECK_DURATION_DESC, "ms");
        this.moduleStarted = metrics.counter(SpoolMetrics.Module.STARTED_TOTAL, SpoolMetrics.Module.STARTED_TOTAL_DESC, "1");
        this.moduleStopped = metrics.counter(SpoolMetrics.Module.STOPPED_TOTAL, SpoolMetrics.Module.STOPPED_TOTAL_DESC, "1");
        this.moduleDegraded = metrics.counter(SpoolMetrics.Module.DEGRADED_TOTAL, SpoolMetrics.Module.DEGRADED_TOTAL_DESC, "1");
        this.modulesActive = metrics.gauge(SpoolMetrics.Module.ACTIVE, SpoolMetrics.Module.ACTIVE_DESC, "1");
        this.downtimeDuration = metrics.timer(SpoolMetrics.Watchdog.DOWNTIME_DURATION, SpoolMetrics.Watchdog.DOWNTIME_DURATION_DESC, "ms");
        this.healthyRatio = metrics.histogram(SpoolMetrics.Watchdog.HEALTHY_RATIO, SpoolMetrics.Watchdog.HEALTHY_RATIO_DESC, "1");
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
        long start = System.nanoTime();
        try {
            inbox.read().forEach(reported -> {
                lastReported.put(reported.identity(), reported.status());
                registry.find(reported.identity()).ifPresentOrElse(
                        existing -> registry.update(existing.seenNow()),
                        () -> {
                            registry.save(ModuleState.of(reported.identity()));
                            moduleObserver.onModuleStarted(reported.identity());
                            moduleStarted.increment(Map.of(SpoolMetrics.Attributes.MODULE, reported.identity().toString()));
                            modulesActive.increment(Map.of(SpoolMetrics.Attributes.MODULE, reported.identity().toString()));
                        }
                );
            });
            Instant now = Instant.now();
            handleZombies();
            long[] counts = {0L, 0L};
            registry.findAll().forEach(module -> {
                counts[1]++;
                Duration silence = Duration.between(module.lastSeen(), now);
                boolean timedOut   = silence.compareTo(moduleTimeout) > 0;
                boolean isDegraded = module.status() == ModuleStatus.DEGRADED;
                if (module.status() == ModuleStatus.HEALTHY) counts[0]++;
                if (timedOut && !isDegraded) {
                    registry.update(module.status(ModuleStatus.DEGRADED));
                    downSince.put(module.identity(), now);
                    moduleObserver.onModuleDegraded(module.identity(), "Module degraded due to timeout");
                    timeouts.increment(Map.of(SpoolMetrics.Attributes.MODULE, module.identity().toString()));
                    moduleDegraded.increment(Map.of(SpoolMetrics.Attributes.MODULE, module.identity().toString()));
                } else if (!timedOut && isDegraded) {
                    Instant wentDown = downSince.remove(module.identity());
                    Duration downtime = wentDown != null ? Duration.between(wentDown, now) : Duration.ZERO;
                    ModuleStatus recoveredAs = lastReported.getOrDefault(module.identity(), ModuleStatus.HEALTHY);
                    registry.update(module.status(recoveredAs));
                    moduleObserver.onModuleRecovered(module.identity(), downtime);
                    downtimeDuration.record(downtime.toMillis(), Map.of(SpoolMetrics.Attributes.MODULE, module.identity().toString()));
                }
            });
            if (counts[1] > 0) {
                healthyRatio.record(counts[0] * 100 / counts[1], Map.of());
            }
        } catch (Exception e) {
            System.err.println("Error during watchdog check: " + e);
        } finally {
            checkTimer.record((System.nanoTime() - start) / 1_000_000, Map.of());
        }
    }

    private void handleZombies() {
        Instant now = Instant.now();
        registry.findAll().stream()
                .filter(module -> Duration.between(module.lastSeen(), now).compareTo(zombieTimeout) > 0)
                .map(ModuleState::identity)
                .toList()
                .forEach(m -> {
                    registry.remove(m);
                    downSince.remove(m);
                    lastReported.remove(m);
                    moduleObserver.onModuleFinished(m, "Zombie detected");
                    zombies.increment(Map.of(SpoolMetrics.Attributes.MODULE, m.toString()));
                    moduleStopped.increment(Map.of(SpoolMetrics.Attributes.MODULE, m.toString()));
                    modulesActive.decrement(Map.of(SpoolMetrics.Attributes.MODULE, m.toString()));
                });
    }
}
