package software.spool.watchdog.application.adapter.input.http;

import io.javalin.Javalin;
import software.spool.core.adapter.jackson.RecordSerializerFactory;
import software.spool.core.model.watchdog.HeartbeatPayload;
import software.spool.watchdog.architecture.WatchdogService;
import software.spool.watchdog.architecture.port.output.WatchdogServer;

public class HTTPWatchdogServer implements WatchdogServer {
    private final Javalin app;
    private final int port;

    public HTTPWatchdogServer(WatchdogService service, int port) {
        this.port = port;
        this.app = Javalin.create(config -> {});

        app.post("/heartbeat", ctx -> {
            try {
                HeartbeatPayload payload = ctx.bodyAsClass(HeartbeatPayload.class);
                service.beat(payload.identity(), payload.status());
                ctx.status(204);
            } catch (Exception e) {
                ctx.status(500).result("Error processing heartbeat: "+ e.getMessage());
            }
        });

        app.get("/health", ctx -> {
            try {
                ctx.json(RecordSerializerFactory.record().serialize(service.query()));
            } catch (Exception e) {
                ctx.status(500).result("Error querying health: " + e.getMessage());
            }
        });
    }

    @Override
    public void start() {
        app.start(port);
        System.out.println("HTTP Watchdog Server started on port " + app.port());
    }

    @Override
    public void stop() { app.stop(); }
}
