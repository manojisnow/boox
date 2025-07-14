# Stage 1: Build the React frontend
# We build the frontend first so its static assets are ready for the backend.
FROM node:20-slim AS builder-frontend
WORKDIR /app
COPY frontend/package*.json ./
RUN npm ci
COPY frontend/ ./
ENV NODE_OPTIONS=--openssl-legacy-provider
RUN npm run build

# Stage 2: Build the Spring Boot backend and package frontend assets into the JAR
FROM maven:3.9.10-amazoncorretto-17 AS builder-backend
WORKDIR /app
# Copy the entire project context to leverage build caching
COPY . .
# Copy the built frontend assets from the previous stage
# into the location Spring Boot automatically serves static resources from.
COPY --from=builder-frontend /app/build ./backend/chatapp/src/main/resources/static
# Build the specific 'chatapp' module. Maven will now package the static files into the final JAR.
RUN mvn -f backend/pom.xml -pl chatapp -am clean package -DskipTests

# Stage 3: Final, lean, and secure production image
FROM openjdk:17-slim
WORKDIR /app

# Copy the final, self-contained executable JAR
COPY --from=builder-backend /app/backend/chatapp/target/chatapp-1.0-SNAPSHOT.jar app.jar

# Environment variables
# CORS is no longer needed since the frontend and backend are served from the same origin.
# The entire application will be available at http://localhost:8080/
ENV PORT=8080 \
    OLLAMA_BASE_URL=http://host.docker.internal:11434 \
    OLLAMA_MODEL=llama2 \
    OLLAMA_API_TEMPERATURE=0.7 \
    SPRING_PROFILES_ACTIVE=docker

# Expose the single port for the Spring Boot application
EXPOSE 8080

# The command is now simple, direct, and manages a single process.
CMD ["java", "-jar", "app.jar"]