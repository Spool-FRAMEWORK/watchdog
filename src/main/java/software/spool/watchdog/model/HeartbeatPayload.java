package software.spool.watchdog.model;

public record HeartbeatPayload(String moduleId, ModuleStatus status) {
}