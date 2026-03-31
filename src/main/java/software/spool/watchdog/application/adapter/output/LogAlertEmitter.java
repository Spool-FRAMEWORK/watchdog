package software.spool.watchdog.application.adapter.output;

import software.spool.watchdog.architecture.model.ModuleRegistration;
import software.spool.watchdog.architecture.port.output.AlertEmitter;

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