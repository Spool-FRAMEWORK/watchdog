package software.spool.watchdog.architecture.port.output;

import software.spool.core.model.watchdog.ModuleState;

import java.util.List;

/**
 * Output port that acts as a transient buffer between heartbeat reception and monitor processing.
 * <p>
 * {@link #write} is called by the service layer on every heartbeat; {@link #read} drains
 * the buffer and is called periodically by {@code WatchdogMonitor}.
 */
public interface Inbox {
    /** Enqueues a module state update. */
    void write(ModuleState state);

    /**
     * Drains and returns all pending state updates since the last call.
     *
     * @return ordered list of pending states; never {@code null}
     */
    List<ModuleState> read();
}
