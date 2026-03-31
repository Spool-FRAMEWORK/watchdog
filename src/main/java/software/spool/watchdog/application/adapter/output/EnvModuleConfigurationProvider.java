package software.spool.watchdog.application.adapter.output;

import software.spool.watchdog.architecture.model.ModuleConfiguration;
import software.spool.watchdog.architecture.port.output.ModuleConfigurationProvider;

import java.util.Optional;

public class EnvModuleConfigurationProvider implements ModuleConfigurationProvider {

    @Override
    public Optional<ModuleConfiguration> findConfiguration(String moduleId) {
        String id = moduleId.toUpperCase();
        String cmd = System.getenv(id + "_START_COMMAND");
        String dir = System.getenv(id + "_WORKING_DIR");
        if (cmd == null) return Optional.empty();
        return Optional.of(new ModuleConfiguration(cmd, dir != null ? dir : "."));
    }
}