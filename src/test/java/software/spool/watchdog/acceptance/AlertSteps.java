package software.spool.watchdog.acceptance;

import com.sun.net.httpserver.HttpServer;
import io.cucumber.java.After;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import software.spool.core.adapter.jackson.PayloadDeserializerFactory;
import software.spool.watchdog.application.Application;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;

public class AlertSteps {
    private static final String ALERTS_PATH = "/alerts";
    private static final Duration WAIT_FOR_AN_ALERT = Duration.ofSeconds(10);
    private static final Duration QUIET_AFTER_THE_LAST_BEAT = Duration.ofMillis(500);
    private static final Duration WAIT_FOR_A_BEAT = Duration.ofSeconds(5);
    private static final Duration POLL_INTERVAL = Duration.ofMillis(100);

    private final HttpClient client = HttpClient.newHttpClient();
    private final List<Map<String, Object>> alerts = new CopyOnWriteArrayList<>();
    private final Map<String, String> environment = new HashMap<>();
    private HttpServer webhook;
    private volatile int answer = 200;
    private int port;
    private Optional<Application> watchdog = Optional.empty();
    private String lastHealth = "";

    @Given("a webhook that records the alerts it receives")
    public void aWebhookThatRecordsTheAlerts() throws IOException {
        webhook = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        webhook.createContext(ALERTS_PATH, exchange -> {
            alerts.add(PayloadDeserializerFactory.json().asMap().deserialize(exchange.getRequestBody().readAllBytes()));
            exchange.sendResponseHeaders(answer, -1);
            exchange.close();
        });
        webhook.start();
    }

    @Given("the watchdog alerts that webhook, with a module timeout of {int}s and a zombie timeout of {int}s")
    public void theWatchdogAlertsThatWebhook(int moduleTimeout, int zombieTimeout) throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }
        environment.put("WATCHDOG_PORT", String.valueOf(port));
        environment.put("MODULE_TIMEOUT_SECONDS", String.valueOf(moduleTimeout));
        environment.put("ZOMBIE_TIMEOUT_SECONDS", String.valueOf(zombieTimeout));
        environment.put("WATCHDOG_ALERT_WEBHOOK_URL", "http://localhost:" + webhook.getAddress().getPort() + ALERTS_PATH);
    }

    @Given("the watchdog has no alert webhook")
    public void theWatchdogHasNoAlertWebhook() {
        environment.remove("WATCHDOG_ALERT_WEBHOOK_URL");
    }

    @Given("the webhook answers {int} to every alert")
    public void theWebhookAnswers(int status) {
        answer = status;
    }

    @Given("the module {string} beats as {word}")
    public void theModuleBeats(String module, String status) {
        beat("{\"identity\":{\"moduleId\":\"" + module + "\"},\"status\":\"" + status + "\",\"pieces\":[]}");
    }

    @Given("the module {string} beats as {word} with the piece {string} {word} because {string}")
    public void theModuleBeatsWithAPiece(String module, String status, String piece, String pieceStatus, String reason) {
        beat("{\"identity\":{\"moduleId\":\"" + module + "\"},\"status\":\"" + status + "\",\"pieces\":[{\"piece\":\""
                + piece + "\",\"type\":\"FILE_SYSTEM\",\"status\":\"" + pieceStatus + "\",\"reason\":\"" + reason
                + "\",\"checkedAt\":\"" + Instant.now() + "\",\"hasCheck\":true}]}");
    }

    @Given("the module {string} stops beating for {int}s")
    public void theModuleStopsBeating(String module, int seconds) throws InterruptedException {
        Thread.sleep(Duration.ofSeconds(seconds).toMillis());
    }

    @Then("the webhook receives a {string} alert for {string}")
    public void theWebhookReceivesAnAlert(String event, String module) {
        alertThat(event, module, alert -> true);
    }

    @Then("the webhook receives a {string} alert for {string} from {word} to {word}")
    public void theWebhookReceivesAStatusChange(String event, String module, String from, String to) {
        alertThat(event, module, alert -> from.equals(alert.get("from")) && to.equals(alert.get("to")));
    }

    @Then("the webhook receives {int} {string} alert(s) for {string}")
    public void theWebhookReceivesAlerts(int count, String event, String module) throws InterruptedException {
        Thread.sleep(QUIET_AFTER_THE_LAST_BEAT.toMillis());
        assertThat(alerts.stream().filter(alert -> event.equals(alert.get("event")) && module.equals(alert.get("module"))))
                .hasSize(count);
    }

    @Then("the webhook receives no alert")
    public void theWebhookReceivesNoAlert() throws InterruptedException {
        Thread.sleep(QUIET_AFTER_THE_LAST_BEAT.toMillis());
        assertThat(alerts).isEmpty();
    }

    @Then("the text of the alert names {string}")
    public void theTextNames(String module) {
        assertThat(alerts.getLast().get("text")).asString().contains(module);
    }

    @Then("the alert says the module was down for at least {int}s")
    public void theAlertSaysTheModuleWasDown(int seconds) {
        assertThat(((Number) alerts.getLast().get("downtimeSeconds")).longValue()).isGreaterThanOrEqualTo(seconds);
    }

    @Then("the alert names the piece {string} and the reason {string}")
    public void theAlertNamesThePiece(String piece, String reason) {
        assertThat(alerts.getLast().get("pieces")).asString().contains(piece).contains(reason);
        assertThat(alerts.getLast().get("text")).asString().contains(piece).contains(reason);
    }

    @Then("the health of the watchdog lists the module {string} as {word}")
    public void theHealthListsTheModule(String module, String status) throws Exception {
        Instant deadline = Instant.now().plus(WAIT_FOR_AN_ALERT);
        while (!status.equals(statusOf(module)) && Instant.now().isBefore(deadline)) {
            Thread.sleep(POLL_INTERVAL.toMillis());
        }
        assertThat(statusOf(module)).as("the module %s in the health of the watchdog: %s", module, lastHealth).isEqualTo(status);
    }

    @After
    public void stopEverything() {
        watchdog.ifPresent(Application::shutdown);
        if (webhook != null) webhook.stop(0);
    }

    private void alertThat(String event, String module, Predicate<Map<String, Object>> matches) {
        Instant deadline = Instant.now().plus(WAIT_FOR_AN_ALERT);
        while (Instant.now().isBefore(deadline)) {
            Optional<Map<String, Object>> found = alerts.stream()
                    .filter(alert -> event.equals(alert.get("event")) && module.equals(alert.get("module")))
                    .filter(matches)
                    .reduce((first, second) -> second);
            if (found.isPresent()) {
                alerts.remove(found.get());
                alerts.add(found.get());
                return;
            }
            sleep();
        }
        assertThat(alerts).as("an alert %s for %s", event, module).anySatisfy(alert -> {
            assertThat(alert).containsEntry("event", event).containsEntry("module", module);
            assertThat(matches.test(alert)).isTrue();
        });
    }

    private void beat(String body) {
        startedWatchdog();
        String module = PayloadDeserializerFactory.json().asMap().deserialize(body.getBytes(StandardCharsets.UTF_8))
                .get("identity").toString();
        Optional<Object> seenBefore = lastSeenOf(module);
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/heartbeat"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        try {
            assertThat(client.send(request, HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(204);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
        Instant deadline = Instant.now().plus(WAIT_FOR_A_BEAT);
        while (lastSeenOf(module).equals(seenBefore) && Instant.now().isBefore(deadline)) {
            sleep();
        }
    }

    @SuppressWarnings("unchecked")
    private Optional<Object> lastSeenOf(String identity) {
        try {
            return modules().stream()
                    .filter(state -> identity.equals(state.get("identity").toString()))
                    .map(state -> state.get("lastSeen"))
                    .findFirst();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private void startedWatchdog() {
        if (watchdog.isPresent()) return;
        Application started = new Application(environment);
        started.run();
        watchdog = Optional.of(started);
    }

    @SuppressWarnings("unchecked")
    private String statusOf(String module) throws Exception {
        return modules().stream()
                .filter(state -> module.equals(((Map<String, Object>) state.get("identity")).get("moduleId")))
                .map(state -> String.valueOf(state.get("status")))
                .findFirst().orElse("missing");
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> modules() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/health")).GET().build();
        String body = client.send(request, HttpResponse.BodyHandlers.ofString()).body();
        lastHealth = body;
        String json = body.startsWith("\"")
                ? PayloadDeserializerFactory.json().as(String.class).deserialize(body.getBytes(StandardCharsets.UTF_8))
                : body;
        return PayloadDeserializerFactory.json().as(List.class).deserialize(json.getBytes(StandardCharsets.UTF_8));
    }

    private static void sleep() {
        try {
            Thread.sleep(POLL_INTERVAL.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
