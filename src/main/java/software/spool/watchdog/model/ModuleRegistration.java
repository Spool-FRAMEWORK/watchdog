package software.spool.watchdog.model;

import java.time.Duration;

public record ModuleRegistration(
        String moduleId,
        Duration heartbeatInterval,
        Duration timeout,
        String startCommand,
        String workingDir) {}