package software.spool.watchdog.model;

import java.time.Instant;

public record ModuleHealthView(
    String moduleId,
    ModuleStatus status,
    Instant lastSeen
) {}