package software.spool.watchdog.architecture.port.input;

import software.spool.core.model.watchdog.ModuleStatus;

public interface Heartbeat {
    boolean beat(String moduleId, ModuleStatus status);
}