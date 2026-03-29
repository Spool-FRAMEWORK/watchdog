package software.spool.watchdog.port.input;

import software.spool.watchdog.model.ModuleRegistration;

public interface RegisterModule {
    void register(ModuleRegistration registration);
}