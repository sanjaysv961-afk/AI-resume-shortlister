# Build stage using Maven and Java 17
FROM maven:3.8.5-openjdk-17 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests

# Run stage using a lightweight Java 17 image
FROM eclipse-temurin:17-jdk
WORKDIR /app
COPY --from=build /app/target/resume-shortlister-1.0.0.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]