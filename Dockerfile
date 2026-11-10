# 1. Loyihani yig'ish (Build) bosqichi
FROM maven:3.8.5-openjdk-21 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests

# 2. Loyihani ishga tushirish (Run) bosqichi
FROM openjdk:17-jdk-slim
WORKDIR /app
# "demo18-0.0.1-SNAPSHOT.jar" o'rniga pom.xml'dagi nomingiz bo'lishi kerak
COPY --from=build /app/target/demo18-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]