package software.spool.watchdog.architecture;

import software.spool.core.model.watchdog.ModuleIdentity;
import software.spool.core.model.watchdog.ModuleState;
import software.spool.core.model.watchdog.ModuleStatus;
import software.spool.core.port.metrics.MetricsRegistry;
import software.spool.core.port.metrics.SpoolMetrics;
import software.spool.watchdog.architecture.port.input.Heartbeat;
import software.spool.watchdog.architecture.port.input.QueryHealth;
import software.spool.watchdog.architecture.port.output.Inbox;
import software.spool.watchdog.architecture.port.output.ModuleRegistry;

import java.util.Collection;
import java.util.Map;

public class WatchdogService implements Heartbeat, QueryHealth {
    private final ModuleRegistry registry;
    private final Inbox inbox;
    private final MetricsRegistry.CounterMetric heartbeats;

    public WatchdogService(Inbox inbox, ModuleRegistry registry, MetricsRegistry.CounterMetric heartbeats) {
        this.inbox = inbox;
        this.registry = registry;
        this.heartbeats = heartbeats;
    }

    @Override
    public void beat(ModuleIdentity identity, ModuleStatus status) {
        heartbeats.increment(Map.of(SpoolMetrics.Attributes.MODULE, identity.toString()));
        inbox.write(ModuleState.of(identity).status(status));
    }

    @Override
    public Collection<ModuleState> query() {
        return registry.findAll().stream()
                .toList();
    }
}
