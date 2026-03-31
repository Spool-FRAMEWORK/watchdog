package software.spool.watchdog.architecture.model;

import software.spool.core.model.watchdog.ModuleIdentity;

import java.time.Duration;

public record ModuleRegistration(
        String moduleId,
        Duration heartbeatInterval,
        Duration timeout,
        String startCommand,
        String workingDir
) {
    public static ModuleRegistration from(ModuleIdentity identity, String startCommand, String workingDir) {
        return new ModuleRegistration(
                identity.moduleId(),
                identity.heartbeatInterval(),
                identity.timeout(),
                startCommand,
                workingDir
        );
    }
}