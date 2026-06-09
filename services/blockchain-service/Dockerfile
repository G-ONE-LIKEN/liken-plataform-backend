# =============================================================================
# Multi-stage build del Blockchain Service.
# Stage 1: compila con Maven 3.9 + Eclipse Temurin 21.
# Stage 2: runtime con JRE 21 (alpine no soporta arm64 oficial; usamos Temurin).
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
COPY --from=build /workspace/target/app.jar /app/app.jar
COPY contracts /contracts
EXPOSE 8085
ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=70.0", \
  "-XX:+UseSerialGC", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar"]
