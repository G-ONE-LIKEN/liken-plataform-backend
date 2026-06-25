# ── Stage 1: Build de la app Java ─────────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-21-alpine AS build
WORKDIR /workspace
COPY services/blockchain-service/pom.xml .
RUN mvn -B -q dependency:go-offline
COPY services/blockchain-service/src ./src
# Corremos los tests en el build: si fallan, la imagen no se construye.
RUN mvn -B test
RUN mvn -B -q -DskipTests package

# ── Stage 2: Foundry Oficial (reemplaza stage intermedio manual) ──────────
# Usar la imagen oficial que ya viene con forge y cast compilados y listos
FROM ghcr.io/foundry-rs/foundry:latest AS foundry

# ── Stage 3: Runtime Final ───────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Instalar bash, git y agregar gcompat por si el binario de forge lo requiere en Alpine
RUN apk add --no-cache bash git gcompat

RUN addgroup -S appgroup && adduser -S appuser -G appgroup

# Copiar forge directamente desde la imagen oficial (esta en /usr/local/bin)
COPY --from=foundry /usr/local/bin/forge /usr/local/bin/forge
COPY --from=foundry /usr/local/bin/cast /usr/local/bin/cast

COPY --from=build /workspace/target/app.jar app.jar
RUN chown appuser:appgroup app.jar

# Asegurar la carpeta de logs para este servicio tambien
RUN mkdir -p /app/logs && chown -R appuser:appgroup /app/logs

USER appuser
ENV PORT=8085
EXPOSE 8085

HEALTHCHECK --interval=30s --timeout=5s --start-period=90s --retries=3 \
    CMD wget -qO- http://localhost:${PORT}/health || exit 1

ENTRYPOINT ["java", \
    "-XX:+UseContainerSupport", \
    "-XX:+UseSerialGC", \
    "-XX:TieredStopAtLevel=1", \
    "-XX:+ExitOnOutOfMemoryError", \
    "-Xshare:auto", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-Dserver.port=${PORT:-8085}", \
    "-jar", "app.jar"]