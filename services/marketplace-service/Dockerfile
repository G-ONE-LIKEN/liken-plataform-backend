# services/marketplace-service/Dockerfile
FROM maven:3.9-eclipse-temurin-21-alpine AS build
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -q
COPY src ./src
RUN mvn package -DskipTests -q

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/target/app.jar app.jar
EXPOSE 8086
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