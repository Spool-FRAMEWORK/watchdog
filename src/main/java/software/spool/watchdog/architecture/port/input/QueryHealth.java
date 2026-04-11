package software.spool.watchdog.architecture.port.input;

import software.spool.core.model.watchdog.ModuleState;

import java.util.Collection;

public interface QueryHealth {
    Collection<ModuleState> query();
}