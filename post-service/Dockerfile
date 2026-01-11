# Stage 1: Build the JAR
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
# Build the application, skipping tests to speed it up
RUN mvn clean package -DskipTests

# Stage 2
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
COPY --from=build /app/target/*-fat.jar app.jar
EXPOSE 8889
CMD ["java", "-jar", "app.jar"]
