# CLAUDE.md — Boox Chat Application

## Project Overview

Boox is a full-stack web application providing an interactive chat UI powered by local AI models via [Ollama](https://ollama.com). No external AI API dependencies — runs entirely locally.

**Architecture:** React SPA frontend + Spring Boot backend + Ollama AI engine (all containerizable)

---

## Tech Stack

| Layer | Technology |
|---|---|
| Frontend | React 17, Axios, react-scripts |
| Backend | Spring Boot 2.6.6, Java 17, Maven |
| AI Engine | Ollama (llama3.2 default) |
| HTTP Client | Spring Cloud OpenFeign |
| Containerization | Docker, Docker Compose |

---

## Build & Run

### Docker Compose (simplest)
```bash
docker compose up -d
# Pull a model into the ollama container:
docker exec <ollama_container_id> ollama pull llama3.2
# App available at http://localhost:8080
```

### Local Development
```bash
# Terminal 1 — Ollama
ollama serve && ollama pull llama3.2

# Terminal 2 — Backend (port 8080)
cd backend/chatapp && mvn spring-boot:run

# Terminal 3 — Frontend (port 3000)
cd frontend && npm install && npm start
```

### Docker Build (multi-stage)
```bash
docker build -t boox .
```

---

## Testing

```bash
# Run unit tests
cd backend/chatapp && mvn test

# Run with coverage report (90% minimum required)
mvn test -Djacoco

# Frontend tests
cd frontend && npm test
```

---

## Code Quality (enforced at parent POM level)

```bash
# Auto-format code (run before committing)
mvn spotless:apply

# Check all quality gates
mvn checkstyle:check   # Google Java Style
mvn pmd:check          # Code analysis
mvn spotbugs:check     # Bug detection

# Run everything (build + quality + tests)
mvn clean verify
```

> All quality tools are configured in `backend/pom.xml` and apply to all submodules.

---

## Key Source Locations

```
backend/chatapp/src/main/java/com/example/chatapp/
  ChatController.java          # REST endpoints
  ChatService.java             # Business logic
  OllamaChatEngine.java        # Ollama API integration
  InMemoryChatContextService.java  # Thread-safe conversation context
  CorsConfig.java              # CORS configuration
  GlobalExceptionHandler.java  # Centralized error handling

frontend/src/
  components/                  # ChatBox, Message, Sidebar
  services/api.js              # Axios HTTP client
  App.jsx                      # Root component
```

---

## Configuration

Key properties (`backend/chatapp/src/main/resources/application.properties`):

```properties
server.port=8080
ollama.api.url=http://localhost:11434
ollama.model=llama3.2
ollama.api.temperature=0.7
chat.cors.allowed-origins=http://localhost:3000
```

Environment variable overrides (Docker/runtime):
- `OLLAMA_MODEL` — model name (e.g., `llama2`, `codellama`)
- `OLLAMA_API_URL` — Ollama server URL
- `OLLAMA_API_TEMPERATURE` — creativity 0–1 (default 0.7)
- `CORS_ALLOWED_ORIGINS` — allowed frontend origins
- `PORT` — backend port (default 8080)

---

## Important Notes

- **Java 17 LTS required** — Java 21+ deferred until PMD 7.x compatibility
- **Node 20+ required** for frontend builds
- **Maven 3.8+ required** for backend
- **90% code coverage** enforced via JaCoCo — tests must be written for new backend code
- **Google Java Format** enforced via Spotless — always run `mvn spotless:apply` before committing Java changes
- **Docker deployment serves frontend from backend** — React build is embedded in the Spring Boot JAR as static resources
- **Ollama container needs 8GB memory** allocation (4GB reservation) — set in docker-compose.yml
- **In-memory chat context** — conversation history is not persisted across restarts

---

## GitHub Actions CI

Workflows are in `.github/workflows/`. CI runs Maven build + quality checks + tests on push/PR.
