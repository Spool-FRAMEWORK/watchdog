package software.spool.watchdog.application;

import java.io.IOException;

public class Main {
    public static void main(String[] args) throws InterruptedException, IOException {
        Application application = new Application();
        application.run();
        Runtime.getRuntime().addShutdownHook(new Thread(application::shutdown, "shutdown-hook"));
        Thread.currentThread().join();
    }
}
