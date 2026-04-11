package software.spool.watchdog.architecture.port.output;

import software.spool.core.model.watchdog.ModuleIdentity;
import software.spool.core.model.watchdog.ModuleState;

import java.util.Collection;
import java.util.Optional;

public interface ModuleRegistry {
    void remove(ModuleIdentity identity);
    void save(ModuleState moduleState);
    void update(ModuleState moduleState);
    Optional<ModuleState> find(ModuleIdentity identity);
    Collection<ModuleState> findAll();
}