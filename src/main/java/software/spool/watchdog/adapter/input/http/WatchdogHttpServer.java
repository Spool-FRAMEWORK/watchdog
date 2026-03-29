package software.spool.watchdog.adapter.input.http;

import io.javalin.Javalin;
import software.spool.core.adapter.jackson.PayloadDeserializerFactory;
import software.spool.core.adapter.jackson.RecordSerializerFactory;
import software.spool.watchdog.model.HeartbeatPayload;
import software.spool.watchdog.model.ModuleRegistration;
import software.spool.watchdog.port.input.Heartbeat;
import software.spool.watchdog.port.input.QueryHealth;
import software.spool.watchdog.port.input.RegisterModule;

public class WatchdogHttpServer {
    private final Javalin app;

    public WatchdogHttpServer(RegisterModule registerModule, Heartbeat heartbeat,
                              QueryHealth queryHealth, int port) {
        this.app = Javalin.create(config -> {});

        app.post("/register", ctx -> {
            try {
                ModuleRegistration registration = PayloadDeserializerFactory.json()
                        .as(ModuleRegistration.class).deserialize(ctx.body());
                registerModule.register(registration);
                ctx.status(201);
            } catch (IllegalArgumentException e) {
                ctx.status(400).result("Bad request: " + e.getMessage());
            } catch (Exception e) {
                System.out.println("Error registering module: " + e.getMessage());
                ctx.status(500).result("Internal server error");
            }
        });

        app.post("/heartbeat", ctx -> {
            try {
                HeartbeatPayload payload = ctx.bodyAsClass(HeartbeatPayload.class);
                boolean found = heartbeat.beat(payload.moduleId(), payload.status());
                if (!found) {
                    ctx.status(404).result("Module not registered: " + payload.moduleId());
                } else {
                    ctx.status(204);
                }
            } catch (Exception e) {
                System.out.println("Error processing heartbeat: "+ e.getMessage());
                ctx.status(500).result("Internal server error");
            }
        });

        app.get("/health", ctx -> {
            try {
                ctx.json(RecordSerializerFactory.record().serialize(queryHealth.query()));
            } catch (Exception e) {
                System.out.println("Error querying health: " + e.getMessage());
                ctx.status(500).result("Internal server error");
            }
        });

        app.start(port);
    }

    public void stop() { app.stop(); }
}