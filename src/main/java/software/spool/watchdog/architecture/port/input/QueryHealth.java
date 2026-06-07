package software.spool.watchdog.architecture.port.input;

import software.spool.core.model.watchdog.ModuleState;

import java.util.Collection;

/**
 * Input port for querying the current health state of all known modules.
 */
public interface QueryHealth {
    /**
     * Returns a snapshot of the current state of every module tracked by the watchdog.
     *
     * @return collection of module states; never {@code null}, may be empty
     */
    Collection<ModuleState> query();
}