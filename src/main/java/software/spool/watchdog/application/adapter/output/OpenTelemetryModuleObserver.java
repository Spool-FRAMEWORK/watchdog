package software.spool.watchdog.application.adapter.output;

import software.spool.core.adapter.logging.LoggerFactory;
import software.spool.core.model.watchdog.ModuleIdentity;
import software.spool.core.model.watchdog.ModuleStatus;
import software.spool.core.port.health.HealthStatus;
import software.spool.core.port.health.PieceHealth;
import software.spool.core.port.logging.Logger;
import software.spool.core.port.watchdog.ModuleLogger;
import software.spool.watchdog.architecture.port.output.ModuleObserver;

import java.time.Duration;
import java.util.List;

public class OpenTelemetryModuleObserver implements ModuleObserver {
    private static final Logger LOG = LoggerFactory.getLogger(OpenTelemetryModuleObserver.class);
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
    public void onModuleStatusChanged(ModuleIdentity identity, ModuleStatus from, ModuleStatus to, List<PieceHealth> pieces) {
        LOG.warn("Module {} reports {} instead of {}. Pieces not healthy: {}", identity.moduleId(), to, from,
                pieces.stream().filter(piece -> piece.status() != HealthStatus.HEALTHY)
                        .map(piece -> piece.piece() + " (" + piece.reason().orElse(piece.status().name()) + ")").toList());
    }

    @Override
    public void onModuleRecovered(ModuleIdentity identity, Duration downtime) {
        logger.moduleRecovered(identity, downtime);
    }
}