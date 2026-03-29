package software.spool.watchdog.adapter.output;

import software.spool.watchdog.model.ModuleRegistration;
import software.spool.watchdog.model.RegisteredModule;
import software.spool.watchdog.port.output.ModuleRegistry;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryModuleRegistry implements ModuleRegistry {
    private final Map<String, RegisteredModule> modules = new ConcurrentHashMap<>();

    @Override
    public void save(ModuleRegistration registration) {
        modules.computeIfAbsent(registration.moduleId(), id -> new RegisteredModule(registration));
    }

    @Override
    public void updateLastSeen(String moduleId) {
        Optional.ofNullable(modules.get(moduleId)).ifPresent(RegisteredModule::updateLastSeen);
    }

    @Override
    public Optional<RegisteredModule> findById(String moduleId) {
        return Optional.ofNullable(modules.get(moduleId));
    }

    @Override
    public Collection<RegisteredModule> findAll() {
        return modules.values();
    }
}