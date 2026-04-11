package software.spool.watchdog.application.adapter.output;

import software.spool.core.model.watchdog.ModuleIdentity;
import software.spool.core.model.watchdog.ModuleState;
import software.spool.watchdog.architecture.port.output.Inbox;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class InMemoryInbox implements Inbox {
    private final ConcurrentMap<ModuleIdentity, ModuleState> states;

    public InMemoryInbox() {
        states = new ConcurrentHashMap<>();
    }

    @Override
    public void write(ModuleState state) {
        states.put(state.identity(), state);
    }

    @Override
    public List<ModuleState> read() {
        List<ModuleState> snapshot = List.copyOf(states.values());
        states.clear();
        return snapshot;
    }
}
