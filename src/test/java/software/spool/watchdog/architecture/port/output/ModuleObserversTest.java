package software.spool.watchdog.architecture.port.output;

import org.junit.jupiter.api.Test;
import software.spool.core.model.watchdog.ModuleIdentity;
import software.spool.core.model.watchdog.ModuleStatus;
import software.spool.core.port.health.PieceHealth;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ModuleObserversTest {
    private static final ModuleIdentity MODULE = ModuleIdentity.of("orders-crawler");

    @Test
    void of_passesEveryEventToEachObserver() {
        List<String> first = new ArrayList<>();
        List<String> second = new ArrayList<>();
        ModuleObserver both = ModuleObservers.of(recording(first), recording(second));

        both.onModuleStarted(MODULE);
        both.onModuleDegraded(MODULE, "timeout");
        both.onModuleRecovered(MODULE, Duration.ofSeconds(2));
        both.onModuleFinished(MODULE, "zombie");
        both.onModuleStatusChanged(MODULE, ModuleStatus.HEALTHY, ModuleStatus.UNHEALTHY, List.of());

        List<String> expected = List.of("started", "degraded timeout", "recovered PT2S", "finished zombie",
                "changed HEALTHY->UNHEALTHY");
        assertThat(first).isEqualTo(expected);
        assertThat(second).isEqualTo(expected);
    }

    @Test
    void of_whenAnObserverFails_stillPassesTheEventToTheOthers() {
        List<String> second = new ArrayList<>();
        ModuleObserver failing = new ModuleObserver() {
            @Override public void onModuleStarted(ModuleIdentity id) {}
            @Override public void onModuleDegraded(ModuleIdentity id, String r) { throw new IllegalStateException("down"); }
            @Override public void onModuleRecovered(ModuleIdentity id, Duration d) {}
            @Override public void onModuleFinished(ModuleIdentity id, String r) {}
        };

        ModuleObservers.of(failing, recording(second)).onModuleDegraded(MODULE, "timeout");

        assertThat(second).containsExactly("degraded timeout");
    }

    private static ModuleObserver recording(List<String> events) {
        return new ModuleObserver() {
            @Override public void onModuleStarted(ModuleIdentity id) { events.add("started"); }
            @Override public void onModuleDegraded(ModuleIdentity id, String r) { events.add("degraded " + r); }
            @Override public void onModuleRecovered(ModuleIdentity id, Duration d) { events.add("recovered " + d); }
            @Override public void onModuleFinished(ModuleIdentity id, String r) { events.add("finished " + r); }
            @Override public void onModuleStatusChanged(ModuleIdentity id, ModuleStatus from, ModuleStatus to, List<PieceHealth> pieces) {
                events.add("changed " + from + "->" + to);
            }
        };
    }
}
