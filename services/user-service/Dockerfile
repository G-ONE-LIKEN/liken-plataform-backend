# ── Stage 1: build ──────────────────────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-21-alpine AS build

WORKDIR /build

# Copiamos primero el pom.xml para aprovechar el caché de dependencias
COPY pom.xml .
RUN mvn dependency:go-offline -q

# Copiamos el código fuente
COPY src ./src

# PASO CRÍTICO: Ejecutar los tests aquí.
# Si estos tests fallan, el build se detiene aquí y Docker no sigue al stage 2.
RUN mvn test

# Si los tests pasan, entonces empaquetamos (sin repetir los tests)
RUN mvn package -DskipTests -q

# ── Stage 2: run ─────────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

RUN addgroup -S appgroup && adduser -S appuser -G appgroup

# Asegúrate de que el nombre del jar coincida con el <finalName> en tu pom.xml (pusiste "app")
COPY --from=build /build/target/app.jar app.jar
RUN chown appuser:appgroup app.jar

# NOTA: En Docker, esta carpeta SI existe. El problema era en local.
# Aquí la creamos para que la aplicación tenga permisos de escritura en producción.
RUN mkdir -p /app/logs && chown -R appuser:appgroup /app/logs

USER appuser

ENV PORT=8080

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD wget -qO- http://localhost:${PORT}/actuator/health || exit 1

ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:+UseSerialGC", \
  "-XX:TieredStopAtLevel=1", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-Dserver.port=${PORT:-8080}", \
  "-jar", "app.jar"]