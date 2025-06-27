# Use Maven base image
FROM maven:3.9.5-openjdk-17-slim as build

# Set working directory
WORKDIR /app

# Copy pom.xml and download dependencies
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy source code
COPY src ./src

# Build the application
RUN mvn clean package -DskipTests

# Use a smaller production image
FROM openjdk:17-slim

# Set working directory
WORKDIR /app

# Copy the built JAR from the build stage
COPY --from=build /app/target/chatapp-1.0-SNAPSHOT.jar .

# Expose the application port
EXPOSE 8080

# Start the application
CMD ["java", "-jar", "chatapp-1.0-SNAPSHOT.jar"]
