# Stage 1: Build the Java server
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:resolve -q
COPY src/ src/
COPY res/ res/
COPY db/ db/
COPY application.properties .
RUN mvn package -DskipTests -q

# Stage 2: Runtime
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /app/target/loginloadbal.jar app.jar
COPY res/ res/
COPY db/ db/
COPY application.properties .
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
