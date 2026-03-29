package software.spool.watchdog;

import software.spool.watchdog.model.ModuleHealthView;
import software.spool.watchdog.model.ModuleRegistration;
import software.spool.watchdog.model.ModuleStatus;
import software.spool.watchdog.model.RegisteredModule;
import software.spool.watchdog.port.input.Heartbeat;
import software.spool.watchdog.port.input.QueryHealth;
import software.spool.watchdog.port.input.RegisterModule;
import software.spool.watchdog.port.output.ModuleRegistry;

import java.util.Collection;

public class WatchdogService implements RegisterModule, Heartbeat, QueryHealth {
    private final ModuleRegistry registry;

    public WatchdogService(ModuleRegistry registry) {
        this.registry = registry;
    }

    @Override
    public void register(ModuleRegistration registration) {
        registry.save(registration);
    }

    @Override
    public boolean beat(String moduleId, ModuleStatus status) {
        return registry.findById(moduleId).map(module -> {
            registry.updateLastSeen(moduleId);
            module.setStatus(status);
            return true;
        }).orElse(false);
    }

    @Override
    public Collection<ModuleHealthView> query() {
        return registry.findAll().stream()
                .map(m -> new ModuleHealthView(m.moduleId(), m.status(), m.lastSeen()))
                .toList();
    }
}