package software.spool.watchdog.application.adapter.output;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.spool.core.adapter.jackson.PayloadDeserializerFactory;
import software.spool.core.model.watchdog.ModuleIdentity;
import software.spool.core.model.watchdog.ModuleStatus;
import software.spool.core.port.health.HealthStatus;
import software.spool.core.port.health.PieceHealth;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class WebhookModuleObserverTest {
    private static final ModuleIdentity MODULE = ModuleIdentity.of("orders-crawler");

    private HttpServer webhook;
    private final LinkedBlockingQueue<String> received = new LinkedBlockingQueue<>();
    private volatile int answer = 200;

    @BeforeEach
    void startTheWebhook() throws IOException {
        webhook = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        webhook.createContext("/alerts", exchange -> {
            received.add(new String(exchange.getRequestBody().readAllBytes()));
            exchange.sendResponseHeaders(answer, -1);
            exchange.close();
        });
        webhook.start();
    }

    @AfterEach
    void stopTheWebhook() {
        webhook.stop(0);
    }

    @Test
    void onModuleDegraded_sendsTheEventTheModuleAndTheReason() throws InterruptedException {
        observer().onModuleDegraded(MODULE, "Module degraded due to timeout");

        Map<String, Object> alert = nextAlert();
        assertThat(alert).containsEntry("event", "MODULE_DEGRADED").containsEntry("module", "orders-crawler")
                .containsEntry("reason", "Module degraded due to timeout");
        assertThat((String) alert.get("text")).contains("orders-crawler").contains("degraded");
    }

    @Test
    void onModuleRecovered_sendsTheSecondsTheModuleWasDown() throws InterruptedException {
        observer().onModuleRecovered(MODULE, Duration.ofMillis(2500));

        Map<String, Object> alert = nextAlert();
        assertThat(alert).containsEntry("event", "MODULE_RECOVERED").containsEntry("downtimeSeconds", 2);
        assertThat((String) alert.get("text")).contains("orders-crawler").contains("2 s");
    }

    @Test
    void onModuleFinished_sendsTheReason() throws InterruptedException {
        observer().onModuleFinished(MODULE, "Zombie detected");

        assertThat(nextAlert()).containsEntry("event", "MODULE_FINISHED").containsEntry("reason", "Zombie detected");
    }

    @Test
    @SuppressWarnings("unchecked")
    void onModuleStatusChanged_sendsBothStatusesAndThePiecesThatAreNotHealthy() throws InterruptedException {
        PieceHealth lake = new PieceHealth("lake", "FILE_SYSTEM", HealthStatus.UNHEALTHY,
                Optional.of("The folder /lake does not exist"), Instant.now(), true);
        PieceHealth bus = new PieceHealth("bus", "IN_MEMORY", HealthStatus.HEALTHY, Optional.empty(), Instant.now(), false);

        observer().onModuleStatusChanged(MODULE, ModuleStatus.HEALTHY, ModuleStatus.UNHEALTHY, List.of(lake, bus));

        Map<String, Object> alert = nextAlert();
        assertThat(alert).containsEntry("event", "MODULE_STATUS_CHANGED").containsEntry("from", "HEALTHY")
                .containsEntry("to", "UNHEALTHY");
        assertThat((List<Map<String, Object>>) alert.get("pieces")).singleElement().satisfies(piece ->
                assertThat(piece).containsEntry("piece", "lake").containsEntry("status", "UNHEALTHY")
                        .containsEntry("reason", "The folder /lake does not exist"));
        assertThat((String) alert.get("text")).contains("lake").contains("The folder /lake does not exist");
    }

    @Test
    void anAlertTheWebhookRefuses_doesNotReachWhoReportedTheEvent() throws InterruptedException {
        answer = 500;

        assertThatCode(() -> observer().onModuleDegraded(MODULE, "timeout")).doesNotThrowAnyException();
        assertThat(received.poll(5, TimeUnit.SECONDS)).isNotNull();
    }

    @Test
    void anAlertToAWebhookThatIsNotThere_doesNotReachWhoReportedTheEvent() {
        WebhookModuleObserver nowhere = new WebhookModuleObserver(URI.create("http://localhost:1/alerts"));

        assertThatCode(() -> nowhere.onModuleDegraded(MODULE, "timeout")).doesNotThrowAnyException();
    }

    private WebhookModuleObserver observer() {
        return new WebhookModuleObserver(URI.create("http://localhost:" + webhook.getAddress().getPort() + "/alerts"));
    }

    private Map<String, Object> nextAlert() throws InterruptedException {
        String body = received.poll(5, TimeUnit.SECONDS);
        assertThat(body).as("an alert reached the webhook").isNotNull();
        return PayloadDeserializerFactory.json().asMap().deserialize(body.getBytes());
    }
}
