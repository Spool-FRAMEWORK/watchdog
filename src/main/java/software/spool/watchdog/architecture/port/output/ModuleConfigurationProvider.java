package software.spool.watchdog.architecture.port.output;

import software.spool.watchdog.architecture.model.ModuleConfiguration;

import java.util.Optional;

public interface ModuleConfigurationProvider {
    Optional<ModuleConfiguration> findConfiguration(String moduleId);
}