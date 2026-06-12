# =============================================================================
# Multi-stage build del Blockchain Service.
# Stage 1: compila con Maven 3.9 + Eclipse Temurin 21.
# Stage 2: runtime con JRE 21 + Foundry + workspace de contratos.
#
# IMPORTANTE: el contexto de build es la RAIZ del repo (no este directorio),
# para poder copiar contracts/ dentro de la imagen. La publicacion on-chain
# ejecuta `forge script` contra /contracts, que en docker-compose llegaba por
# volumen pero en GKE debe estar horneado en la imagen.
#   docker build -f services/blockchain-service/Dockerfile .
# =============================================================================
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY services/blockchain-service/pom.xml .
RUN mvn -B -q dependency:go-offline
COPY services/blockchain-service/src ./src
RUN mvn -B -q -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN apt-get update \
    && apt-get install -y --no-install-recommends bash curl ca-certificates git \
    && rm -rf /var/lib/apt/lists/* \
    && curl -L https://foundry.paradigm.xyz | bash \
    && /root/.foundry/bin/foundryup \
    && ln -s /root/.foundry/bin/forge /usr/local/bin/forge

# Workspace Foundry (src + script + lib vendoreadas + foundry.toml).
# out/cache/broadcast quedan excluidos por .dockerignore; forge compila
# en runtime dentro del pod la primera vez que publica.
COPY contracts /contracts

COPY --from=build /workspace/target/app.jar /app/app.jar
EXPOSE 8085
ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-jar", "/app/app.jar"]
