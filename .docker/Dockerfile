FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

RUN addgroup -S watchdog && adduser -S watchdog -G watchdog

COPY /target/watchdog-1.0.0.jar app.jar

USER watchdog

ENV WATCHDOG_PORT=8090
EXPOSE ${WATCHDOG_PORT}

ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]