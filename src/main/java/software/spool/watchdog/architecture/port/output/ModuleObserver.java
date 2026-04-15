package software.spool.watchdog.architecture.port.output;

import software.spool.core.model.watchdog.ModuleIdentity;

import java.time.Duration;

public interface ModuleObserver {
    void onModuleStarted(ModuleIdentity identity);
    void onModuleDegraded(ModuleIdentity identity, String reason);
    void onModuleFinished(ModuleIdentity identity, String reason);
    void onModuleRecovered(ModuleIdentity identity, Duration downtime);
}