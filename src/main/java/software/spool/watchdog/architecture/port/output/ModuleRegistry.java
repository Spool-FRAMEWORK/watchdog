package software.spool.watchdog.architecture.port.output;

import software.spool.core.model.watchdog.ModuleIdentity;
import software.spool.core.model.watchdog.ModuleState;

import java.util.Collection;
import java.util.Optional;

/**
 * Output port for persisting and querying the live state of registered modules.
 * <p>
 * The watchdog maintains one {@link ModuleState} entry per known module. Entries are
 * created on first heartbeat, updated on subsequent beats, and removed when a module
 * is classified as a zombie.
 */
public interface ModuleRegistry {
    void remove(ModuleIdentity identity);
    void save(ModuleState moduleState);
    void update(ModuleState moduleState);
    Optional<ModuleState> find(ModuleIdentity identity);
    Collection<ModuleState> findAll();
}