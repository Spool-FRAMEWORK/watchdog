package software.spool.watchdog.architecture.port.input;

import software.spool.core.model.watchdog.ModuleIdentity;

public interface RegisterModule {
    void register(ModuleIdentity identity);
}