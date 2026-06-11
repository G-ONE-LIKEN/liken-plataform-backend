# services/blockchain-service/Dockerfile
FROM maven:3.9-eclipse-temurin-21-alpine AS build
WORKDIR /workspace
COPY services/blockchain-service/pom.xml .
RUN mvn -B -q dependency:go-offline
COPY services/blockchain-service/src ./src
RUN mvn -B -q -DskipTests package

# Stage intermedio: descarga e instala foundry (aislado, no ensucia el runtime)
FROM alpine:3.19 AS foundry
RUN apk add --no-cache curl bash git
RUN curl -L https://foundry.paradigm.xyz | bash \
	&& /root/.foundry/bin/foundryup
# Verifica que forge quedó bien
RUN /root/.foundry/bin/forge --version

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN apk add --no-cache bash git
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
# Copia forge desde el stage foundry
COPY --from=foundry /root/.foundry/bin/forge /usr/local/bin/forge
COPY --from=build /workspace/target/app.jar app.jar
RUN chown appuser:appgroup app.jar
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