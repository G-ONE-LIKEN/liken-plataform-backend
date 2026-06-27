# =============================================================================
# Multi-stage build del Blockchain Service.
# Stage 1: compila con Maven 3.9 + Eclipse Temurin 21.
# Stage 2: imagen oficial de Foundry (forge + cast).
# Stage 3: runtime JRE 21 jammy con forge copiado + workspace de contratos.
#
# IMPORTANTE: el contexto de build es la RAIZ del repo (no este directorio),
# para poder copiar contracts/ dentro de la imagen. La publicacion on-chain
# ejecuta `forge script` contra /contracts, que en docker-compose llegaba por
# volumen pero en GKE debe estar horneado en la imagen.
#   docker build -f services/blockchain-service/Dockerfile .
# =============================================================================
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
# Debian-based (no Alpine): forge esta compilado contra glibc y falla en musl
# con "Error relocating: fcntl64/_Unwind_* symbol not found", incluso con
# gcompat instalado. La imagen jammy trae glibc nativa.
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

# bash y git son necesarios para que forge corra los scripts de deploy
RUN apt-get update && apt-get install -y --no-install-recommends bash git ca-certificates \
    && rm -rf /var/lib/apt/lists/*

# --create-home: forge necesita HOME para su cache global (~/.foundry/cache).
# Sin el home dir, falla con "Permission denied (os error 13)" sin mas detalle.
RUN groupadd -r appgroup && useradd -r -g appgroup --create-home --home-dir /home/appuser appuser

# Copiar forge directamente desde la imagen oficial (esta en /usr/local/bin)
COPY --from=foundry /usr/local/bin/forge /usr/local/bin/forge
COPY --from=foundry /usr/local/bin/cast /usr/local/bin/cast

COPY --from=build /workspace/target/app.jar app.jar
RUN chown appuser:appgroup app.jar

# Workspace Foundry (src + script + lib vendoreadas + foundry.toml).
# out/cache/broadcast quedan excluidos por .dockerignore; forge compila
# en runtime dentro del pod la primera vez que publica.
COPY --chown=appuser:appgroup contracts/ /app/contracts/

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