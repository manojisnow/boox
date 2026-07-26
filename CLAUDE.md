# CLAUDE.md — Boox Chat Application

## Project Overview

Boox is a full-stack web application providing an interactive chat UI powered by local AI models via [Ollama](https://ollama.com). No external AI API dependencies — runs entirely locally.

**Architecture:** React SPA frontend + Spring Boot backend + Ollama AI engine (all containerizable)

---

## Tech Stack

| Layer | Technology |
|---|---|
| Frontend | React 19, Axios, Vite |
| Backend | Spring Boot 3.5.3, Java 21, Maven |
| AI Engine | Ollama (phi4-mini default) |
| HTTP Client | Spring RestTemplate |
| Containerization | Docker, Docker Compose |

---

## Build & Run

### Docker Compose (simplest)
```bash
docker compose up -d
# Pull a model into the ollama container:
docker exec <ollama_container_id> ollama pull phi4-mini
# App available at http://localhost:8080
```

### Local Development
```bash
# Terminal 1 — Ollama
ollama serve && ollama pull phi4-mini

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
  controller/
    ChatController.java          # Chat REST endpoints
    StreamController.java        # SSE streaming endpoint
    ConversationController.java  # Conversation list/resume/rename/delete
    CorsConfig.java               # CORS configuration
    GlobalExceptionHandler.java   # Centralized error handling
  service/ChatService.java     # Business logic
  engine/
    OllamaChatEngine.java        # Ollama API integration + tool loop
    ChatContextService.java      # Context abstraction (interface)
    ContextWindowManager.java    # Token-budget window + summarization split
  persistence/
    JpaChatContextService.java   # Default (SQLite-backed) context implementation
    InMemoryChatContextService.java  # Non-persistent fallback, used in unit tests
    ConversationService.java     # Backs ConversationController
    Conversation.java, ChatMessageEntity.java  # JPA entities

frontend/src/
  components/                  # ChatBox, Message, ToolCall, ConversationSidebar
  services/api.js              # Axios HTTP client
  App.jsx                      # Root component; owns active conversation + sidebar
```

---

## Configuration

Key properties (`backend/chatapp/src/main/resources/application.properties`):

```properties
server.port=8080
ollama.api.url=http://localhost:11434
ollama.model=phi4-mini
ollama.api.temperature=0.7
chat.cors.allowed-origins=http://localhost:3000
spring.datasource.url=jdbc:sqlite:./data/boox.db
ollama.context.max-tokens=3000
```

Environment variable overrides (Docker/runtime):
- `OLLAMA_MODEL` — model name (e.g., `llama2`, `codellama`)
- `OLLAMA_API_URL` — Ollama server URL
- `OLLAMA_API_TEMPERATURE` — creativity 0–1 (default 0.7)
- `CORS_ALLOWED_ORIGINS` — allowed frontend origins
- `PORT` — backend port (default 8080)
- `BOOX_DB_PATH` — SQLite database file path (default `/app/data/boox.db` in Docker)
- `OLLAMA_CONTEXT_MAX_TOKENS` / `OLLAMA_CONTEXT_SUMMARY_ENABLED` / `OLLAMA_CONTEXT_NUM_CTX` — context-window tuning

---

## Important Notes

- **Java 21 LTS required** — runs on Spring Boot 3.x (Jakarta EE namespace)
- **Node 20+ required** for frontend builds
- **Maven 3.8+ required** for backend
- **90% code coverage** enforced via JaCoCo — tests must be written for new backend code
- **Google Java Format** enforced via Spotless — always run `mvn spotless:apply` before committing Java changes
- **Docker deployment serves frontend from backend** — React build is embedded in the Spring Boot JAR as static resources
- **Ollama container needs 8GB memory** allocation (4GB reservation) — set in docker-compose.yml
- **Persistent chat context** — conversation history is stored in SQLite (`JpaChatContextService`, the default) and survives restarts; mount `/app/data` as a volume in Docker. `InMemoryChatContextService` still exists as a non-persistent fallback, used in unit tests
- **Context window management** — long conversations are token-budgeted; older turns are summarized rather than sent to the model (or dropped) in full every turn — see `ContextWindowManager`
- **Image input** — up to 4 images/message, base64 over the wire, persisted with the conversation; gated client-side by the selected model's `vision` capability (from Ollama's `/api/tags`)
- **Container runs as a non-root user** in the production image

---

## GitHub Actions CI

Workflows are in `.github/workflows/`. CI runs Maven build + quality checks + tests on push/PR.
