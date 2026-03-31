package software.spool.watchdog.application;

public class Main {
    public static void main(String[] args) throws InterruptedException {
        Application application = new Application();
        application.run();
        Runtime.getRuntime().addShutdownHook(new Thread(application::shutdown, "shutdown-hook"));
        Thread.currentThread().join();
    }
}
