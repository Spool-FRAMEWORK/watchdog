package software.spool.watchdog.architecture.port.input;

import java.util.List;
import software.spool.core.port.health.PieceHealth;
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
     * @param pieces   how each piece of infrastructure the module uses is doing
     */
    void beat(ModuleIdentity identity, ModuleStatus status, List<PieceHealth> pieces);
}