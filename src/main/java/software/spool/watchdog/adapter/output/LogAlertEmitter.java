package software.spool.watchdog.adapter.output;

import software.spool.watchdog.model.ModuleRegistration;
import software.spool.watchdog.port.output.AlertEmitter;

import java.time.Duration;

public class LogAlertEmitter implements AlertEmitter {
    @Override
    public void moduleDown(ModuleRegistration module, Duration silence) {
        System.out.println(String.format("MODULE DOWN: {} <UNK> silence={}", module.moduleId(), silence));
    }

    @Override
    public void moduleRecovered(ModuleRegistration module, Duration downtime) {
        System.out.println(String.format("MODULE RECOVERED: {} <UNK> downtime={}", module.moduleId(), downtime));
    }
}