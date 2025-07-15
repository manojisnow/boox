# Boox - AI Chat Application

A modern chat application built with React and Spring Boot that uses Ollama for local AI models.

## Quick Start with Docker Compose

The fastest way to get started is using Docker Compose, which sets up everything automatically:

```bash
# Start the entire stack
docker compose up -d

# Watch the setup progress
docker compose logs -f
```

The application will be available at:
- Frontend: http://localhost:3000
- Backend: http://localhost:8080
- Ollama API: http://localhost:11434

That's it! Everything is set up and ready to use.

## Prerequisites

For quick start:
- Docker and Docker Compose (that's all!)

For local development:
- Java 17
- Node.js 20+
- Maven
- [Ollama](https://ollama.ai/) with your preferred model (default: llama2)

## Development Options

### Option 1: Docker Compose (Recommended)

This is the simplest and most reliable way to run the entire application. It uses the production-grade `Dockerfile` to build a single, self-contained image where the Spring Boot backend serves both the API and the compiled React frontend.

1.  **Ensure Docker Desktop is running.**

2.  **From the project root, run:**

```bash
# Start everything with Docker Compose
docker compose up -d

# View logs
docker compose logs -f
```

After the containers are up and running, you'll need to download the llama3.2 model:

```bash
# Get the Ollama container ID
docker compose ps

# Download the llama3.2 model
docker exec <ollama_container_id> ollama pull llama3.2
```

The Ollama model will be cached in a Docker volume and won't need to be downloaded again.

### Option 2: Local Development

1. Start Ollama server and pull the model:
```bash
ollama serve
ollama pull llama2
```

2. Start the backend:
```bash
cd backend/app
mvn spring-boot:run
```

3. Start the frontend:
```bash
cd frontend
npm install
npm start
```

### Option 3: Docker + Local Ollama

If you prefer to run Ollama locally (useful if you use Ollama for other projects) but want to containerize the Boox application:

1. Start Ollama locally and pull the model:
```bash
ollama serve
ollama pull llama2
```

2. Run the Boox container with host network access:
```bash
docker run -d --name boox_app \
  -p 3000:3000 \
  -p 8080:8080 \
  -e OLLAMA_API_URL=http://host.docker.internal:11434 \
  -e OLLAMA_MODEL=llama2 \
  boox
```

This setup is particularly useful if you:
- Already have Ollama running locally
- Use Ollama with other applications
- Want to manage Ollama models separately
- Need to switch between different Ollama versions

### Option 4: Hybrid Setup (Local App + Docker Ollama)

If you want to run the application locally but use Ollama in Docker:

```bash
# Start only Ollama
docker compose up -d ollama

# Then run backend and frontend locally as in Option 2
```

## Configuration

### Docker Compose Environment Variables

Create a `.env` file to customize the setup:

```env
OLLAMA_MODEL=codellama      # Use a different model
OLLAMA_API_TEMPERATURE=0.5  # Adjust temperature
```

### Manual Configuration

When running services separately, you can configure:

```bash
docker run -d --name boox_app \
  -p 3000:3000 \
  -p 8080:8080 \
  -e OLLAMA_API_URL=http://ollama:11434 \
  -e OLLAMA_MODEL=llama2 \
  -e OLLAMA_API_TEMPERATURE=0.7 \
  boox
```

Available variables:
- `OLLAMA_API_URL`: Ollama server URL
- `OLLAMA_MODEL`: AI model to use (default: llama2)
- `OLLAMA_API_TEMPERATURE`: Model temperature (default: 0.7)
- `PORT`: Backend port (default: 8080)
- `CORS_ALLOWED_ORIGINS`: CORS origins (default: http://localhost:3000)

## Project Structure

```
boox/
├── backend/           # Spring Boot backend
│   └── chatapp/          # Main application module
├── frontend/         # React frontend
├── scripts/          # Utility scripts
└── docker-compose.yml # Complete development environment
```

## Features

- Real-time chat interface
- Integration with Ollama models
- Configurable model parameters
- Modern React UI
- RESTful Spring Boot backend
- Zero-configuration Docker setup
- Development hot-reload

## Development Notes

- Frontend runs in development mode with hot-reload
- Backend uses Spring Boot with embedded Tomcat
- Docker Compose provides complete environment
- Ollama models are cached in Docker volume
- All services are configured to work together automatically

## Contributing

1. Fork the repository
2. Create your feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add some amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

