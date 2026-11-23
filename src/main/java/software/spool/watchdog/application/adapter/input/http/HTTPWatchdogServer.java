package software.spool.watchdog.application.adapter.input.http;

import software.spool.core.adapter.jackson.PayloadDeserializerFactory;
import io.javalin.Javalin;
import io.javalin.plugin.bundled.CorsPluginConfig;
import software.spool.core.adapter.jackson.RecordSerializerFactory;
import software.spool.core.adapter.logging.LoggerFactory;
import software.spool.core.model.watchdog.HeartbeatPayload;
import software.spool.core.port.logging.Logger;
import software.spool.watchdog.architecture.WatchdogService;
import software.spool.watchdog.architecture.port.output.WatchdogServer;

import java.nio.charset.StandardCharsets;

public class HTTPWatchdogServer implements WatchdogServer {
    private static final Logger LOG = LoggerFactory.getLogger(HTTPWatchdogServer.class);
    private final Javalin app;
    private final int port;

    public HTTPWatchdogServer(WatchdogService service, int port) {
        this.port = port;
        this.app = Javalin.create(config -> {
            config.bundledPlugins.enableCors(cors -> {
                cors.addRule(CorsPluginConfig.CorsRule::anyHost);
            });
        });

        app.post("/heartbeat", ctx -> {
            try {
                HeartbeatPayload payload = PayloadDeserializerFactory.json().as(HeartbeatPayload.class).deserialize(ctx.bodyAsBytes());
                service.beat(payload.identity(), payload.status(), payload.pieces());
                ctx.status(204);
            } catch (Exception e) {
                ctx.status(500).result("Error processing heartbeat: "+ e.getMessage());
            }
        });

        app.get("/health", ctx -> {
            try {
                ctx.json(new String(RecordSerializerFactory.record().serialize(service.query()), StandardCharsets.UTF_8));
            } catch (Exception e) {
                ctx.status(500).result("Error querying health: " + e.getMessage());
            }
        });
    }

    @Override
    public void start() {
        app.start(port);
        LOG.info("Watchdog HTTP server started on port {}", app.port());
    }

    @Override
    public void stop() { app.stop(); }
}
