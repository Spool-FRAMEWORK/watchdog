package software.spool.watchdog.application.adapter.output;

import software.spool.core.model.watchdog.ModuleIdentity;
import software.spool.core.model.watchdog.ModuleState;
import software.spool.watchdog.architecture.port.output.ModuleRegistry;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryModuleRegistry implements ModuleRegistry {
    private final Map<ModuleIdentity, ModuleState> modules = new ConcurrentHashMap<>();

    @Override
    public void remove(ModuleIdentity identity) {
        modules.remove(identity);
    }

    @Override
    public void save(ModuleState moduleState) {
        modules.putIfAbsent(moduleState.identity(), moduleState);
    }

    @Override
    public void update(ModuleState moduleState) {
        modules.put(moduleState.identity(), moduleState);
    }

    @Override
    public Optional<ModuleState> find(ModuleIdentity identity) {
        return Optional.ofNullable(modules.get(identity));
    }

    @Override
    public Collection<ModuleState> findAll() {
        return modules.values();
    }
}