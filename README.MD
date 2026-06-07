# Spool — Watchdog

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://www.apache.org/licenses/LICENSE-2.0)
[![Java](https://img.shields.io/badge/Java-21%2B-orange.svg)](https://openjdk.org/)
[![GHCR](https://img.shields.io/badge/GHCR-ghcr.io%2Fspool--framework%2Fwatchdog-2496ED.svg)](https://ghcr.io/spool-framework/watchdog)

**Watchdog** is the **Spool** standalone monitoring service that **tracks heartbeats from active modules, detects degraded instances, and provides real-time health visibility** across the entire pipeline.

> The Watchdog uses a **push model**: modules proactively send heartbeats to it. It does not restart modules — that is the orchestrator's responsibility (Kubernetes, Docker Swarm, ECS…).

---

## Table of Contents

- [What it does](#what-it-does)
- [Quick Start](#quick-start)
- [Configuration](#configuration)
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
| `SERVICE_NAME` | OpenTelemetry identifier for this service. | `spool-watchdog` |
| `OTEL_LOGS_ENDPOINT` | OTLP endpoint for log export. | `http://host.docker.internal:3100/otlp/v1/logs` |
| `OTEL_TRACES_ENDPOINT` | OTLP endpoint for trace export. | `http://host.docker.internal:4318/v1/traces` |

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

The Watchdog has native OpenTelemetry integration. Point `OTEL_TRACES_ENDPOINT` and `OTEL_LOGS_ENDPOINT` to your collector and it will automatically emit traces and logs for:

- Module registrations and heartbeats.
- Degradation events.
- Zombie evictions.

If the OTEL endpoints are unreachable, the Watchdog continues working normally — telemetry is silently dropped.

---

## License

Distributed under the [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0).
