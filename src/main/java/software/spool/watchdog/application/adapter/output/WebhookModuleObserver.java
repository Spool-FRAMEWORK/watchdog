package software.spool.watchdog.application.adapter.output;

import software.spool.core.adapter.jackson.RecordSerializerFactory;
import software.spool.core.adapter.logging.LoggerFactory;
import software.spool.core.model.watchdog.ModuleIdentity;
import software.spool.core.model.watchdog.ModuleStatus;
import software.spool.core.port.health.HealthStatus;
import software.spool.core.port.health.PieceHealth;
import software.spool.core.port.logging.Logger;
import software.spool.watchdog.architecture.port.output.ModuleObserver;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Sends an alert to a webhook when a module degrades, recovers, is retired or reports another status. The body is a
 * JSON object whose {@code text} a person can read and a Slack webhook takes as it is, with the event, the module and
 * the details as fields. The alert is sent without waiting for the answer; one that fails or takes longer than
 * {@link #TIMEOUT} is written to the log, without the address of the webhook, and is not sent again. Starting a module
 * is not alerted.
 */
public final class WebhookModuleObserver implements ModuleObserver {
    public static final Duration TIMEOUT = Duration.ofSeconds(5);

    private static final Logger LOG = LoggerFactory.getLogger(WebhookModuleObserver.class);
    private static final int FIRST_ERROR_STATUS = 300;

    private final URI webhook;
    private final HttpClient client;

    public WebhookModuleObserver(URI webhook) {
        this.webhook = webhook;
        this.client = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
    }

    @Override
    public void onModuleStarted(ModuleIdentity identity) {
    }

    @Override
    public void onModuleDegraded(ModuleIdentity identity, String reason) {
        Map<String, Object> alert = alert("MODULE_DEGRADED", identity,
                "Spool module " + identity.moduleId() + " is degraded: " + reason);
        alert.put("reason", reason);
        send(alert);
    }

    @Override
    public void onModuleRecovered(ModuleIdentity identity, Duration downtime) {
        Map<String, Object> alert = alert("MODULE_RECOVERED", identity,
                "Spool module " + identity.moduleId() + " recovered after " + downtime.toSeconds() + " s down");
        alert.put("downtimeSeconds", downtime.toSeconds());
        send(alert);
    }

    @Override
    public void onModuleFinished(ModuleIdentity identity, String reason) {
        Map<String, Object> alert = alert("MODULE_FINISHED", identity,
                "Spool module " + identity.moduleId() + " was retired: " + reason);
        alert.put("reason", reason);
        send(alert);
    }

    @Override
    public void onModuleStatusChanged(ModuleIdentity identity, ModuleStatus from, ModuleStatus to, List<PieceHealth> pieces) {
        List<PieceHealth> notHealthy = pieces.stream().filter(piece -> piece.status() != HealthStatus.HEALTHY).toList();
        String failing = notHealthy.stream()
                .map(piece -> piece.piece() + ": " + piece.reason().orElse(piece.status().name()))
                .collect(Collectors.joining("; "));
        Map<String, Object> alert = alert("MODULE_STATUS_CHANGED", identity,
                "Spool module " + identity.moduleId() + " reports " + to + " (was " + from + ")"
                        + (failing.isEmpty() ? "" : ". " + failing));
        alert.put("from", from.name());
        alert.put("to", to.name());
        alert.put("pieces", notHealthy.stream().map(WebhookModuleObserver::piece).toList());
        send(alert);
    }

    private static Map<String, Object> alert(String event, ModuleIdentity identity, String text) {
        Map<String, Object> alert = new LinkedHashMap<>();
        alert.put("text", text);
        alert.put("event", event);
        alert.put("module", identity.moduleId());
        return alert;
    }

    private static Map<String, Object> piece(PieceHealth piece) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("piece", piece.piece());
        fields.put("type", piece.type());
        fields.put("status", piece.status().name());
        piece.reason().ifPresent(reason -> fields.put("reason", reason));
        return fields;
    }

    private void send(Map<String, Object> alert) {
        Object event = alert.get("event");
        Object module = alert.get("module");
        try {
            HttpRequest request = HttpRequest.newBuilder(webhook)
                    .timeout(TIMEOUT)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(RecordSerializerFactory.map().serialize(alert)))
                    .build();
            client.sendAsync(request, HttpResponse.BodyHandlers.discarding()).whenComplete((response, failure) -> {
                if (failure != null) {
                    LOG.warn("The alert {} for module {} could not be sent to the webhook: {}", event, module,
                            failure.getClass().getSimpleName());
                } else if (response.statusCode() >= FIRST_ERROR_STATUS) {
                    LOG.warn("The webhook refused the alert {} for module {} with HTTP {}", event, module,
                            response.statusCode());
                }
            });
        } catch (RuntimeException e) {
            LOG.warn("The alert {} for module {} could not be sent to the webhook: {}", event, module,
                    e.getClass().getSimpleName());
        }
    }
}
