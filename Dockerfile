# Use node as base image and add Java
FROM node:20-slim

# Install OpenJDK-17
RUN apt-get update && \
    apt-get install -y openjdk-17-jdk maven && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/*

# Set working directory
WORKDIR /app

# Copy package.json and install dependencies
COPY frontend/package*.json frontend/
RUN cd frontend && npm install

# Copy backend pom files and download dependencies
COPY backend/pom.xml backend/
COPY backend/app/pom.xml backend/app/
RUN cd backend && mvn -f app/pom.xml dependency:go-offline -B

# Copy source code
COPY frontend frontend/
COPY backend/app backend/app/

# Build backend
RUN cd backend && mvn -f app/pom.xml clean package -DskipTests

# Copy start script
COPY scripts/start.sh ./
RUN chmod +x start.sh

# Environment variables
ENV NODE_OPTIONS=--openssl-legacy-provider \
    PORT=8080 \
    CORS_ALLOWED_ORIGINS=http://localhost:3000 \
    OLLAMA_BASE_URL=http://host.docker.internal:11434 \
    OLLAMA_MODEL=llama2 \
    OLLAMA_API_TEMPERATURE=0.7 \
    SPRING_PROFILES_ACTIVE=docker

# Expose ports
EXPOSE 3000 8080

# Start the application
CMD ["./start.sh"] 