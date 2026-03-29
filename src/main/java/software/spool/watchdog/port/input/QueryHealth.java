package software.spool.watchdog.port.input;

import software.spool.watchdog.model.ModuleHealthView;
import java.util.Collection;

public interface QueryHealth {
    Collection<ModuleHealthView> query();
}