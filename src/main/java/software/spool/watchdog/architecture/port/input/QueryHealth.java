package software.spool.watchdog.architecture.port.input;

import software.spool.core.model.watchdog.ModuleHealthView;

import java.util.Collection;

public interface QueryHealth {
    Collection<ModuleHealthView> query();
}