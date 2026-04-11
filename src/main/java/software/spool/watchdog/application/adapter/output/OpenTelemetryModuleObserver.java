package software.spool.watchdog.application.adapter.output;

import software.spool.core.model.watchdog.ModuleIdentity;
import software.spool.watchdog.architecture.port.output.ModuleObserver;

import java.time.Duration;

public class OTELModuleObserver implements ModuleObserver {
    @Override
    public void onModuleDown(ModuleIdentity identity, Duration silence) {
        System.out.println(String.format("MODULE DOWN: {} <UNK> silence={}", identity.moduleId(), silence));
    }

    @Override
    public void onModuleRecovered(ModuleIdentity identity, Duration downtime) {
        System.out.println(String.format("MODULE RECOVERED: {} <UNK> downtime={}", identity.moduleId(), downtime));
    }
}