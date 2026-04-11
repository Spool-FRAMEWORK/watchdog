package software.spool.watchdog.architecture.port.output;

import software.spool.watchdog.architecture.model.ModuleRegistration;
import java.time.Duration;

public interface AlertEmitter {
    void moduleDown(ModuleRegistration module, Duration silence);
    void moduleRecovered(ModuleRegistration module, Duration downtime);
}