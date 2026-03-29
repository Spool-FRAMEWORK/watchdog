package software.spool.watchdog.port.output;

import software.spool.watchdog.model.ModuleRegistration;
import software.spool.watchdog.model.RegisteredModule;

import java.util.Collection;
import java.util.Optional;

public interface ModuleRegistry {
    void save(ModuleRegistration registration);
    void updateLastSeen(String moduleId);
    Optional<RegisteredModule> findById(String moduleId);
    Collection<RegisteredModule> findAll();
}