package software.spool.watchdog.architecture;

import software.spool.core.model.watchdog.ModuleIdentity;
import software.spool.core.model.watchdog.ModuleState;
import software.spool.core.model.watchdog.ModuleStatus;
import software.spool.watchdog.architecture.port.input.Heartbeat;
import software.spool.watchdog.architecture.port.input.QueryHealth;
import software.spool.watchdog.architecture.port.output.Inbox;
import software.spool.watchdog.architecture.port.output.ModuleRegistry;

import java.util.Collection;

public class WatchdogService implements Heartbeat, QueryHealth {
    private final ModuleRegistry registry;
    private final Inbox inbox;

    public WatchdogService(Inbox inbox, ModuleRegistry registry) {
        this.inbox = inbox;
        this.registry = registry;
    }

    @Override
    public void beat(ModuleIdentity identity, ModuleStatus status) {
        inbox.write(ModuleState.of(identity).status(status));
    }

    @Override
    public Collection<ModuleState> query() {
        return registry.findAll().stream()
                .toList();
    }
}