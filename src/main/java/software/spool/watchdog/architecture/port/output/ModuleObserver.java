package software.spool.watchdog.architecture.port.output;

import software.spool.core.model.watchdog.ModuleIdentity;
import software.spool.core.model.watchdog.ModuleStatus;
import software.spool.core.port.health.PieceHealth;

import java.time.Duration;
import java.util.List;

/**
 * Output port for reacting to module lifecycle events detected by the monitor.
 * <p>
 * Implement this interface to emit logs, metrics, alerts, or any side-effect
 * when a module changes state.
 */
public interface ModuleObserver {
    /** Called the first time a heartbeat is received from {@code identity}. */
    void onModuleStarted(ModuleIdentity identity);

    /** Called when a module stops sending heartbeats beyond the configured timeout. */
    void onModuleDegraded(ModuleIdentity identity, String reason);

    /**
     * Called when a degraded module resumes sending heartbeats.
     *
     * @param downtime total time the module was in a degraded state
     */
    void onModuleRecovered(ModuleIdentity identity, Duration downtime);

    /** Called when a module is evicted from the registry after exceeding the zombie timeout. */
    void onModuleFinished(ModuleIdentity identity, String reason);

    /**
     * Called when a module reports a status other than the one it reported before; a new module counts as healthy
     * before its first report. The pieces are the ones it reported with the new status.
     */
    default void onModuleStatusChanged(ModuleIdentity identity, ModuleStatus from, ModuleStatus to, List<PieceHealth> pieces) {
    }
}