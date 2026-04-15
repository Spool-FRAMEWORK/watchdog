package software.spool.watchdog.application.adapter.output;

import software.spool.core.model.watchdog.ModuleIdentity;
import software.spool.core.port.watchdog.ModuleLogger;
import software.spool.watchdog.architecture.port.output.ModuleObserver;

import java.time.Duration;

public class OpenTelemetryModuleObserver implements ModuleObserver {
    private final ModuleLogger logger;

    public OpenTelemetryModuleObserver(ModuleLogger logger) {
        this.logger = logger;
    }

    @Override
    public void onModuleStarted(ModuleIdentity identity) {
        logger.moduleStarted(identity);
    }

    @Override
    public void onModuleDegraded(ModuleIdentity identity, String reason) {
        logger.moduleDegraded(identity, reason);
    }

    @Override
    public void onModuleFinished(ModuleIdentity identity, String reason) {
        logger.moduleStopped(identity, reason);
    }

    @Override
    public void onModuleRecovered(ModuleIdentity identity, Duration downtime) {
        logger.moduleRecovered(identity, downtime);
    }
}