package software.spool.watchdog.architecture.port.input;

import software.spool.core.model.watchdog.ModuleIdentity;
import software.spool.core.model.watchdog.ModuleStatus;

public interface Heartbeat {
    void beat(ModuleIdentity identity, ModuleStatus status);
}