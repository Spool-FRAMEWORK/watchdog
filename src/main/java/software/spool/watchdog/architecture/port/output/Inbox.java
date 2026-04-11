package software.spool.watchdog.architecture.port.output;

import software.spool.core.model.watchdog.ModuleState;

import java.util.List;

public interface Inbox {
    void write(ModuleState state);
    List<ModuleState> read();
}
