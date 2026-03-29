package software.spool.watchdog.port.output;

import software.spool.watchdog.model.ModuleRegistration;
import java.time.Duration;

public interface RecoveryAction {
    void recover(ModuleRegistration module, Duration silence);
}