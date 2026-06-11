# services/auth-service/Dockerfile
# ── Stage 1: build ──────────────────────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-21-alpine AS build

WORKDIR /build

COPY pom.xml .
RUN mvn dependency:go-offline -q

COPY src ./src
RUN mvn package -DskipTests -q

# ── Stage 2: run ─────────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

RUN addgroup -S appgroup && adduser -S appuser -G appgroup

COPY --from=build /build/target/app.jar app.jar
RUN chown appuser:appgroup app.jar

USER appuser

ENV PORT=8081

EXPOSE 8081

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD wget -qO- http://localhost:${PORT}/actuator/health || exit 1

ENTRYPOINT ["java",\
  "-Xms32m",\
  "-Xmx192m",\
  "-XX:+UseSerialGC",\
  "-XX:TieredStopAtLevel=1",\
  "-XX:+ExitOnOutOfMemoryError",\
  "-Xshare:auto",\
  "-Djava.security.egd=file:/dev/./urandom",\
  "-jar",\
  "app.jar"]
