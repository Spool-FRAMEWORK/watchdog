package software.spool.watchdog.architecture.model;

import software.spool.core.model.watchdog.ModuleStatus;

import java.time.Instant;

public final class RegisteredModule {
    private final ModuleRegistration registration;
    private volatile Instant lastSeen;
    private volatile ModuleStatus status;

    public RegisteredModule(ModuleRegistration registration) {
        this.registration = registration;
        this.lastSeen = Instant.now();
        this.status = ModuleStatus.HEALTHY;
    }

    public ModuleRegistration registration() { return registration; }
    public String moduleId() { return registration.moduleId(); }
    public java.time.Duration timeout() { return registration.timeout(); }
    public Instant lastSeen() { return lastSeen; }
    public ModuleStatus status() { return status; }

    public synchronized void updateLastSeen() { this.lastSeen = Instant.now(); }
    public synchronized void setStatus(ModuleStatus s) { this.status = s; }
}