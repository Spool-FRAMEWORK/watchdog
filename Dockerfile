FROM eclipse-temurin:21-jre-alpine
RUN apk add --no-cache curl wget

WORKDIR /app

RUN addgroup -S spool && adduser -S feeder -G spool
USER feeder

COPY target/sec-feeder.jar app.jar

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=45s --retries=3 \
  CMD wget -qO- http://localhost:8080/spool/health || exit 1

ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]