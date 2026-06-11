# services/invest-dividend-service/Dockerfile
FROM maven:3.9-eclipse-temurin-21-alpine AS build

WORKDIR /workspace

COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src

RUN mvn -B -q -DskipTests package

FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

COPY --from=build /workspace/target/app.jar /app/app.jar

EXPOSE 8083

ENTRYPOINT ["java",\
  "-Xms64m",\
  "-Xmx256m",\
  "-XX:+UseSerialGC",\
  "-XX:TieredStopAtLevel=1",\
  "-XX:+ExitOnOutOfMemoryError",\
  "-Xshare:auto",\
  "-Djava.security.egd=file:/dev/./urandom",\
  "-jar",\
  "/app/app.jar"]