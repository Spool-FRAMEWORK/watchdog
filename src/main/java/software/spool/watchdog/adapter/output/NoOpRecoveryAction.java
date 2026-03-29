package software.spool.watchdog.adapter.output;

import software.spool.watchdog.model.ModuleRegistration;
import software.spool.watchdog.port.output.RecoveryAction;

import java.time.Duration;

public class NoOpRecoveryAction implements RecoveryAction {

    @Override
    public void recover(ModuleRegistration module, Duration silence) {
    }
}