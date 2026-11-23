# Multi-stage build for the Render cloud demo.
# Stage 1 builds the Spring Boot jar with the "demo" profile (H2 included).
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY backend/pom.xml backend/pom.xml
RUN mvn -f backend/pom.xml -B -q dependency:go-offline
COPY backend/src backend/src
RUN mvn -f backend/pom.xml -B -q -Pdemo clean package -DskipTests

# Stage 2 runs only the jar on a small JRE image.
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/backend/target/bugtrace-backend-1.0.0.jar app.jar
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "java -jar /app/app.jar --spring.profiles.active=demo --server.port=${PORT:-8080}"]
