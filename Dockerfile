# Stage 1: Build backend
FROM maven:3.9.5-openjdk-17-slim AS builder-backend
WORKDIR /app/backend
COPY backend/pom.xml .
COPY backend/chatapp/pom.xml chatapp/
COPY backend/src ./src
RUN mvn -f chatapp/pom.xml clean package -DskipTests

# Stage 2: Build frontend
FROM node:20-slim AS builder-frontend
WORKDIR /app/frontend
COPY frontend/package*.json .
RUN npm ci
COPY frontend/src ./src
COPY frontend/public ./public
RUN npm run build

# Stage 3: Final image
FROM openjdk:17-slim
WORKDIR /app

# Copy built artifacts
COPY --from=builder-backend /app/backend/chatapp/target/chatapp-1.0-SNAPSHOT.jar .
COPY --from=builder-frontend /app/frontend/build ./frontend/build

# Copy start script and set permissions
COPY scripts/start-combined.sh .
RUN chmod +x start-combined.sh

# Environment variables
ENV PORT=8080     CORS_ALLOWED_ORIGINS=http://localhost:3000     OLLAMA_BASE_URL=http://host.docker.internal:11434     OLLAMA_MODEL=llama2     OLLAMA_API_TEMPERATURE=0.7     SPRING_PROFILES_ACTIVE=docker

# Expose ports
EXPOSE 3000 8080

# Start the application
CMD ["./start-combined.sh"]