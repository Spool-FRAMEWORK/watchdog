package software.spool.watchdog.architecture.port.output;

import software.spool.core.adapter.logging.LoggerFactory;
import software.spool.core.model.watchdog.ModuleIdentity;
import software.spool.core.model.watchdog.ModuleStatus;
import software.spool.core.port.health.PieceHealth;
import software.spool.core.port.logging.Logger;

import java.time.Duration;
import java.util.List;
import java.util.function.Consumer;

/**
 * Several observers seen as one: each event reaches every observer, in order, and an observer that fails does not keep
 * the event from the rest.
 */
public final class ModuleObservers implements ModuleObserver {
    private static final Logger LOG = LoggerFactory.getLogger(ModuleObservers.class);

    private final List<ModuleObserver> observers;

    private ModuleObservers(List<ModuleObserver> observers) {
        this.observers = observers;
    }

    public static ModuleObserver of(ModuleObserver... observers) {
        return new ModuleObservers(List.of(observers));
    }

    @Override
    public void onModuleStarted(ModuleIdentity identity) {
        each(observer -> observer.onModuleStarted(identity));
    }

    @Override
    public void onModuleDegraded(ModuleIdentity identity, String reason) {
        each(observer -> observer.onModuleDegraded(identity, reason));
    }

    @Override
    public void onModuleRecovered(ModuleIdentity identity, Duration downtime) {
        each(observer -> observer.onModuleRecovered(identity, downtime));
    }

    @Override
    public void onModuleFinished(ModuleIdentity identity, String reason) {
        each(observer -> observer.onModuleFinished(identity, reason));
    }

    @Override
    public void onModuleStatusChanged(ModuleIdentity identity, ModuleStatus from, ModuleStatus to, List<PieceHealth> pieces) {
        each(observer -> observer.onModuleStatusChanged(identity, from, to, pieces));
    }

    private void each(Consumer<ModuleObserver> event) {
        for (ModuleObserver observer : observers) {
            try {
                event.accept(observer);
            } catch (RuntimeException e) {
                LOG.warn("The module observer {} failed: {}", observer.getClass().getSimpleName(), e.getMessage());
            }
        }
    }
}
