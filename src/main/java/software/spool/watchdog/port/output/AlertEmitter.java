package software.spool.watchdog.port.output;

import software.spool.watchdog.model.ModuleRegistration;
import java.time.Duration;

public interface AlertEmitter {
    void moduleDown(ModuleRegistration module, Duration silence);
    void moduleRecovered(ModuleRegistration module, Duration downtime);
}