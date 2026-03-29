package software.spool.watchdog.adapter.output;

import software.spool.watchdog.model.ModuleRegistration;
import software.spool.watchdog.port.output.RecoveryAction;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;

public class ProcessRecoveryAction implements RecoveryAction {

    @Override
    public void recover(ModuleRegistration module, Duration silence) {
        try {
            List<String> cmd = Arrays.asList("/bin/sh", "-c", module.startCommand());
            new ProcessBuilder(cmd)
                    .directory(new File(module.workingDir()))
                    .inheritIO()
                    .start();
        } catch (IOException e) {
            throw new RuntimeException("Unable to restart module " + module.moduleId(), e);
        }
    }
}