package software.spool.watchdog.port.input;

import software.spool.watchdog.model.ModuleStatus;

public interface Heartbeat {
    boolean beat(String moduleId, ModuleStatus status);
}