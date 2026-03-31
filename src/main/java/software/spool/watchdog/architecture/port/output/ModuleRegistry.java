package software.spool.watchdog.architecture.port.output;

import software.spool.watchdog.architecture.model.ModuleRegistration;
import software.spool.watchdog.architecture.model.RegisteredModule;

import java.util.Collection;
import java.util.Optional;

public interface ModuleRegistry {
    void handleDownModule();
    void save(ModuleRegistration registration);
    void updateLastSeen(String moduleId);
    Optional<RegisteredModule> findById(String moduleId);
    Collection<RegisteredModule> findAll();
}