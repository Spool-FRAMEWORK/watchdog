# Spool — Watchdog

[![License](https://img.shields.io/badge/License-AGPL%203.0-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-21%2B-orange.svg)](https://openjdk.org/)
[![GHCR](https://img.shields.io/badge/GHCR-ghcr.io%2Fspool--framework%2Fwatchdog-2496ED.svg)](https://ghcr.io/spool-framework/watchdog)

**Watchdog** is the **Spool** standalone monitoring service that **tracks heartbeats from active modules, detects degraded instances, and provides real-time health visibility** across the entire pipeline.

> The Watchdog uses a **push model**: modules proactively send heartbeats to it. It does not restart modules — that is the orchestrator's responsibility (Kubernetes, Docker Swarm, ECS…).

---

## Table of Contents

- [What it does](#what-it-does)
- [Quick Start](#quick-start)
- [Configuration](#configuration)
- [Alerts](#alerts)
- [HTTP API](#http-api)
- [Observability (OpenTelemetry)](#observability-opentelemetry)
- [License](#license)

---

## What it does

```
Module A  ──► POST /heartbeat ──► Registry ──► status: HEALTHY
Module B  ──►
Module C  (silent > MODULE_TIMEOUT_SECONDS)  ──► status: DEGRADED
Module D  (silent > ZOMBIE_TIMEOUT_SECONDS)  ──► evicted from registry

GET /health ──► snapshot of all modules and their current status
```

1. **Registration** — When a module starts, it sends its first heartbeat. The Watchdog adds it to the registry.
2. **Monitoring** — Each subsequent heartbeat resets the module's last-seen timestamp.
3. **Degradation** — If a module goes silent past `MODULE_TIMEOUT_SECONDS`, it is marked `DEGRADED`.
4. **Zombie cleanup** — If silence persists past `ZOMBIE_TIMEOUT_SECONDS`, the module is removed entirely.
5. **Alerts** — Each of these changes, and every change in the status a module reports, is written to the log and, when `WATCHDOG_ALERT_WEBHOOK_URL` is set, sent to a webhook (see [Alerts](#alerts)).

---

## Quick Start

The Docker image is published to GitHub Container Registry:

```
ghcr.io/spool-framework/watchdog:latest
```

### Docker CLI

```bash
docker run -d \
    --name spool-watchdog \
    -p 8090:8090 \
    -e MODULE_TIMEOUT_SECONDS=30 \
    -e ZOMBIE_TIMEOUT_SECONDS=300 \
    ghcr.io/spool-framework/watchdog:latest
```

### Docker Compose (Recommended)

```yaml
services:
    watchdog:
        image: ghcr.io/spool-framework/watchdog:latest
        container_name: spool-watchdog
        ports:
          - "8090:8090"
        environment:
            - MODULE_TIMEOUT_SECONDS=30
            - ZOMBIE_TIMEOUT_SECONDS=300
        healthcheck:
            test: ["CMD", "wget", "-qO-", "http://localhost:8090/health"]
            interval: 30s
            timeout: 5s
            retries: 3
```

```bash
docker compose up -d watchdog
```

---

## Configuration

All configuration is done via environment variables. No code changes required.

| Variable | Description | Default |
|---|---|---|
| `WATCHDOG_PORT` | Port where the HTTP server listens. | `8090` |
| `MODULE_TIMEOUT_SECONDS` | Seconds without a heartbeat before a module is marked `DEGRADED`. | `30` |
| `ZOMBIE_TIMEOUT_SECONDS` | Seconds without a heartbeat before a module is removed permanently. | `300` |
| `WATCHDOG_ALERT_WEBHOOK_URL` | Webhook that receives an alert when a module fails or comes back. It usually holds a token: keep it in a secret, never in the code. Without it, nothing is sent. | none |
| `SERVICE_NAME` | OpenTelemetry service name shown in traces and metrics. | `spool-watchdog` |
| `OTEL_TRACES_ENDPOINT` | OTLP HTTP endpoint for trace export. | `http://host.docker.internal:4318/v1/traces` |
| `OTEL_LOGS_ENDPOINT` | OTLP HTTP endpoint for log export. | `http://host.docker.internal:3100/otlp/v1/logs` |
| `OTEL_METRICS_ENDPOINT` | OTLP HTTP endpoint for metric export. | `http://host.docker.internal:4320/v1/metrics` |

---

## Alerts

With `WATCHDOG_ALERT_WEBHOOK_URL` set, the watchdog sends a `POST` with a JSON body to that address on each of
these events. The log keeps every event as well.

| `event` | When | Extra fields |
|---|---|---|
| `MODULE_DEGRADED` | A module is silent past `MODULE_TIMEOUT_SECONDS` | `reason` |
| `MODULE_RECOVERED` | A degraded module beats again | `downtimeSeconds` |
| `MODULE_FINISHED` | A module is silent past `ZOMBIE_TIMEOUT_SECONDS` and is removed | `reason` |
| `MODULE_STATUS_CHANGED` | A module reports a status other than the one before (a new module counts as `HEALTHY`) | `from`, `to`, `pieces` |

A module starting is not alerted, and the same status reported again is alerted once. The `text` field reads as a
sentence, so a Slack incoming webhook takes the body as it is:

```json
{
  "text": "Spool module orders-crawler reports UNHEALTHY (was HEALTHY). lake: The folder /lake does not exist",
  "event": "MODULE_STATUS_CHANGED",
  "module": "orders-crawler",
  "from": "HEALTHY",
  "to": "UNHEALTHY",
  "pieces": [{"piece": "lake", "type": "FILE_SYSTEM", "status": "UNHEALTHY", "reason": "The folder /lake does not exist"}]
}
```

`pieces` lists only the pieces that are not healthy. An alert is sent without waiting for the answer: one that fails
or takes longer than 5 seconds is written to the log as a warning, without the address of the webhook, and is not sent
again. It never stops the watchdog.

```bash
docker run -d -p 8090:8090 \
    -e WATCHDOG_ALERT_WEBHOOK_URL="$SLACK_WEBHOOK_URL" \
    ghcr.io/spool-framework/watchdog:latest
```

---

## HTTP API

### Send a Heartbeat

Called by Spool modules on startup and periodically while running. When using `CrawlerBuilderFactory.watchdog(url, moduleId)` (or the equivalent in Janitor/Ingester), this is done automatically.

```http
POST /heartbeat
Content-Type: application/json

{
    "identity": {
        "name": "my-crawler",
        "instanceId": "crawler-node-01"
    },
    "status": "HEALTHY"
}
```

Response: `204 No Content`

---

### Query Global Health

Returns a snapshot of all registered modules and their current status.

```http
GET /health
```

```json
[
    {
        "identity": { "name": "my-crawler", "instanceId": "crawler-node-01" },
        "status": "HEALTHY",
        "lastSeen": "2026-06-06T10:30:00Z"
    },
    {
        "identity": { "name": "my-ingester", "instanceId": "ingester-node-02" },
        "status": "DEGRADED",
        "lastSeen": "2026-06-06T10:29:15Z"
    }
]
```

---

## Observability (OpenTelemetry)

The Watchdog has native OpenTelemetry integration and emits three signal types:

- **Metrics** — heartbeat rate, active modules, health ratio, timeouts, zombies, degradation duration. Sent to `OTEL_METRICS_ENDPOINT` (OTel Collector → Prometheus).
- **Traces** — module lifecycle events. Sent to `OTEL_TRACES_ENDPOINT` (Tempo).
- **Logs** — structured lifecycle log entries. Sent to `OTEL_LOGS_ENDPOINT` (Loki).

If any endpoint is unreachable, the Watchdog continues working normally — that signal is silently dropped.

---

## License

Distributed under the [GNU Affero General Public License v3.0](LICENSE).
