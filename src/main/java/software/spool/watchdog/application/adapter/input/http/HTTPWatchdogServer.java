package software.spool.watchdog.application.adapter.input.http;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import software.spool.core.adapter.health.TracedHttpHandler;
import software.spool.core.adapter.jackson.PayloadDeserializerFactory;
import software.spool.core.adapter.jackson.RecordSerializerFactory;
import software.spool.core.model.watchdog.HeartbeatPayload;
import software.spool.watchdog.architecture.WatchdogService;
import software.spool.watchdog.architecture.port.output.WatchdogServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

public class HTTPWatchdogServer implements WatchdogServer {
    private final HttpServer server;
    private final WatchdogService service;
    private final int port;

    public HTTPWatchdogServer(WatchdogService service, int port) throws IOException {
        this.service = service;
        this.port = port;
        this.server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/heartbeat", new TracedHttpHandler(this::handleHeartbeat));
        server.createContext("/health", new TracedHttpHandler(this::handleHealth));
        server.setExecutor(Executors.newCachedThreadPool());
    }

    @Override
    public void start() {
        server.start();
        System.out.println("HTTP Watchdog Server started on port " + port);
    }

    @Override
    public void stop() {
        server.stop(0);
    }

    private void handleHeartbeat(HttpExchange exchange) throws IOException {
        if (rejectIfNotMethod(exchange, "POST")) return;
        try {
            HeartbeatPayload payload = PayloadDeserializerFactory.json()
                    .as(HeartbeatPayload.class)
                    .deserialize(HttpUtils.readBody(exchange));
            service.beat(payload.identity(), payload.status());
            HttpUtils.sendNoContent(exchange);
        } catch (Exception e) {
            HttpUtils.sendError(exchange, 500, e.getMessage());
        }
    }

    private void handleHealth(HttpExchange exchange) throws IOException {
        if (rejectIfNotMethod(exchange, "GET")) return;
        try {
            String responseBody = RecordSerializerFactory.record().serialize(service.query());
            HttpUtils.sendJson(exchange, 200, responseBody);
        } catch (Exception e) {
            HttpUtils.sendError(exchange, 500, e.getMessage());
        }
    }

    private boolean rejectIfNotMethod(HttpExchange exchange, String expectedMethod) throws IOException {
        if (!expectedMethod.equalsIgnoreCase(exchange.getRequestMethod())) {
            HttpUtils.sendError(exchange, 405, "Method Not Allowed");
            return true;
        }
        return false;
    }
}