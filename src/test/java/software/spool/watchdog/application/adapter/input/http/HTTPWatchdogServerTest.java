package software.spool.watchdog.application.adapter.input.http;

import software.spool.core.model.watchdog.ModuleStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.spool.watchdog.application.adapter.output.InMemoryInbox;
import software.spool.watchdog.application.adapter.output.InMemoryModuleRegistry;
import software.spool.watchdog.architecture.WatchdogService;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

class HTTPWatchdogServerTest {

    private HTTPWatchdogServer server;
    private int port;
    private InMemoryInbox inbox;
    private HttpClient client;

    @BeforeEach
    void setUp() throws IOException {
        port = freePort();
        inbox = new InMemoryInbox();
        WatchdogService service = new WatchdogService(
            inbox, new InMemoryModuleRegistry(), (v, a) -> {}
        );
        server = new HTTPWatchdogServer(service, port);
        server.start();
        client = HttpClient.newHttpClient();
    }

    @AfterEach
    void tearDown() {
        server.stop();
    }

    @Test
    void healthEndpoint_returnsOk() throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:" + port + "/health"))
            .GET()
            .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(200);
    }

    @Test
    void heartbeatEndpoint_validPayload_returnsNoContent() throws IOException, InterruptedException {
        String body = "{\"identity\":{\"moduleId\":\"svc-1\"},\"status\":\"HEALTHY\"}";
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:" + port + "/heartbeat"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(204);
    }

    @Test
    void heartbeatEndpoint_payloadWithPieces_keepsThePiecesOfTheModule() throws IOException, InterruptedException {
        String body = "{\"identity\":{\"moduleId\":\"svc-1\"},\"status\":\"UNHEALTHY\",\"pieces\":[{\"piece\":\"lake\","
                + "\"type\":\"S3\",\"status\":\"UNHEALTHY\",\"reason\":\"the bucket does not exist\","
                + "\"checkedAt\":\"2026-11-22T10:00:00Z\",\"hasCheck\":true}]}";
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:" + port + "/heartbeat"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(204);
        assertThat(inbox.read()).singleElement().satisfies(state -> {
            assertThat(state.status()).isEqualTo(ModuleStatus.UNHEALTHY);
            assertThat(state.pieces()).singleElement().satisfies(piece -> {
                assertThat(piece.piece()).isEqualTo("lake");
                assertThat(piece.reason()).contains("the bucket does not exist");
            });
        });
    }

    private static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
