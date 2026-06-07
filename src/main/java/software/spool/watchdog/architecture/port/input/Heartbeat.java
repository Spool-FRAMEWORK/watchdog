package software.spool.watchdog.architecture.port.input;

import software.spool.core.model.watchdog.ModuleIdentity;
import software.spool.core.model.watchdog.ModuleStatus;

/**
 * Input port for receiving module heartbeats.
 * <p>
 * Each module in the framework must periodically call {@link #beat} to signal
 * that it is alive and report its current operational status.
 */
public interface Heartbeat {
    /**
     * Records a heartbeat from the given module.
     *
     * @param identity the unique identity of the reporting module
     * @param status   the current operational status of the module
     */
    void beat(ModuleIdentity identity, ModuleStatus status);
}