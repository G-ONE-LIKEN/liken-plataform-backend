# api-gateway/Dockerfile
# Build stage
FROM maven:3.9.6-eclipse-temurin-21 AS builder

WORKDIR /app

COPY pom.xml .
RUN mvn dependency:go-offline -q

COPY src ./src

RUN mvn package -DskipTests -q

# Run stage
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

COPY --from=builder /app/target/api-gateway.jar app.jar

EXPOSE 8090

ENTRYPOINT ["java",\
  "-Xms64m",\
  "-Xmx256m",\
  "-XX:+UseSerialGC",\
  "-XX:TieredStopAtLevel=1",\
  "-XX:+ExitOnOutOfMemoryError",\
  "-Xshare:auto",\
  "-Djava.security.egd=file:/dev/./urandom",\
  "-jar",\
  "app.jar"]