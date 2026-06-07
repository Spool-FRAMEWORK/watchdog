FROM eclipse-temurin:21-jre-alpine
RUN apk add --no-cache curl wget

WORKDIR /app

RUN addgroup -S spool && adduser -S watchdog -G spool
USER watchdog

COPY target/watchdog.jar app.jar

ENV SERVICE_NAME=spool-watchdog
ENV OTEL_TRACES_ENDPOINT=http://host.docker.internal:4318/v1/traces
ENV OTEL_LOGS_ENDPOINT=http://host.docker.internal:3100/otlp/v1/logs
ENV OTEL_METRICS_ENDPOINT=http://host.docker.internal:4320/v1/metrics
ENV MODULE_TIMEOUT_SECONDS=30
ENV ZOMBIE_TIMEOUT_SECONDS=300
ENV WATCHDOG_PORT=8090

EXPOSE 8090

HEALTHCHECK --interval=30s --timeout=5s --start-period=45s --retries=3 \
  CMD wget -qO- http://localhost:8090/health || exit 1

ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]