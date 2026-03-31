package software.spool.watchdog.application.adapter.output;

import software.spool.watchdog.architecture.model.ModuleRegistration;
import software.spool.watchdog.architecture.model.RegisteredModule;
import software.spool.watchdog.architecture.port.output.ModuleRegistry;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryModuleRegistry implements ModuleRegistry {
    private final Map<String, RegisteredModule> modules = new ConcurrentHashMap<>();

    @Override
    public void handleDownModule() {
        Instant threshold = Instant.now().minus(Duration.ofMinutes(5));
        modules.entrySet().stream()
                .filter(e -> e.getValue().lastSeen().isBefore(threshold))
                .map(Map.Entry::getKey)
                .forEach(modules::remove);
    }

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