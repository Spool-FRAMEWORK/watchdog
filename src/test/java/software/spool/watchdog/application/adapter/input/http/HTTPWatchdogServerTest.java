package software.spool.watchdog.application.adapter.input.http;

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
    private HttpClient client;

    @BeforeEach
    void setUp() throws IOException {
        port = freePort();
        WatchdogService service = new WatchdogService(
            new InMemoryInbox(), new InMemoryModuleRegistry(), (v, a) -> {}
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

    private static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
