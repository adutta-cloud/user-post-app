# Stage 1: Build
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src

# Build JAR (Skip tests to avoid compilation issues)
RUN mvn clean package -Dmaven.test.skip=true

# Stage 2: Run
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
# Copy the specific file we named in POM
COPY --from=build /app/target/app-fat.jar app.jar
EXPOSE 8888
CMD ["java", "-jar", "app.jar"]
