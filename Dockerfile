# 1. Loyihani yig'ish (Build) bosqichi
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests

# 2. Loyihani ishga tushirish (Run) bosqichi
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
COPY --from=build /app/target/demo18-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]