package software.spool.watchdog.architecture;

import software.spool.core.model.watchdog.ModuleHealthView;
import software.spool.core.model.watchdog.ModuleIdentity;
import software.spool.core.model.watchdog.ModuleStatus;
import software.spool.watchdog.architecture.model.ModuleConfiguration;
import software.spool.watchdog.architecture.model.ModuleRegistration;
import software.spool.watchdog.architecture.port.input.Heartbeat;
import software.spool.watchdog.architecture.port.input.QueryHealth;
import software.spool.watchdog.architecture.port.input.RegisterModule;
import software.spool.watchdog.architecture.port.output.ModuleConfigurationProvider;
import software.spool.watchdog.architecture.port.output.ModuleRegistry;

import java.util.Collection;

public class WatchdogService implements RegisterModule, Heartbeat, QueryHealth {
    private final ModuleRegistry registry;
    private final ModuleConfigurationProvider configProvider;

    public WatchdogService(ModuleRegistry registry, ModuleConfigurationProvider configProvider) {
        this.registry = registry;
        this.configProvider = configProvider;
    }

    @Override
    public void register(ModuleIdentity identity) {
        ModuleConfiguration configuration = configProvider.findConfiguration(identity.moduleId())
                .orElse(new ModuleConfiguration("", ""));
        registry.save(ModuleRegistration.from(identity, configuration.startCommand(), configuration.workingDir()));
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