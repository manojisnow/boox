# Spring Boot Backend for Chat Application

This directory contains the **Spring Boot** backend for the chat app: a single Maven module (`chatapp`) under a parent POM that enforces code quality across it.

[← Back to Project Overview](../README.md)

---

## Features
- REST API for chat, tool calling, and conversation management
- Integration with Ollama for AI responses (model-agnostic — works with any model you've pulled)
- Web search tool via DuckDuckGo Instant Answer API
- **Persistent chat context** — conversations are stored in SQLite (via Spring Data JPA) and survive restarts; an in-memory implementation also exists and is used in unit tests
- **Context window management** — long conversations are token-budgeted, with older turns folded into a running summary rather than sent to the model (or dropped) in full every turn
- Input validation and global exception handling
- SLF4J logging throughout the stack
- Configurable CORS and AI parameters
- **Code quality enforced with Checkstyle, PMD, Spotless, and SpotBugs**

## Backend Structure
```
backend/
  README.md                # This file
  pom.xml                  # Parent POM (BOM import + code quality plugins)
  chatapp/                 # The Spring Boot application — main code, tests, and config
```

### Key packages (`chatapp/src/main/java/com/example/chatapp/`)
- `controller/` — REST endpoints (`ChatController`, `StreamController`, `ConversationController`) and request/response DTOs
- `service/` — `ChatService`, orchestrates engines and context
- `engine/` — `ChatEngine`/`OllamaChatEngine` (Ollama integration + tool loop), `ChatContextService` (context abstraction), `ContextWindowManager` (token-budget + summarization logic)
- `persistence/` — JPA entities (`Conversation`, `ChatMessageEntity`), repositories, `JpaChatContextService` (the default context implementation), `ConversationService` (backs the conversation-management API)
- `tool/` — `Tool` interface, `ToolRegistry`, `WebSearchTool`

## Setup & Development
1. **Install dependencies & build:**
   ```sh
   cd backend
   mvn clean verify
   ```
2. **Ensure Ollama is running:**
   - Install Ollama from [ollama.ai/download](https://ollama.ai/download)
   - Pull a model: `ollama pull phi4-mini`
   - Start the server: `ollama serve`
   - The default configuration expects Ollama at `http://localhost:11434`

3. **Run the backend app:**
   ```sh
   cd chatapp
   mvn spring-boot:run
   ```
4. **Configuration** — edit `chatapp/src/main/resources/application.properties`:
   ```properties
   # Ollama
   ollama.api.url=http://localhost:11434
   ollama.model=phi4-mini
   ollama.api.temperature=0.7

   # Context window management
   ollama.context.max-tokens=3000
   ollama.context.summary.enabled=true

   # Persistence (SQLite; the file's parent directory is created automatically)
   spring.datasource.url=jdbc:sqlite:./data/boox.db

   # CORS and other properties...
   ```
   Most of these are also overridable via environment variables — see the [root README](../README.md#manual-configuration).

## REST API
| Endpoint | Purpose |
|---|---|
| `GET /api/chat/servers` | List configured chat engines (e.g. `ollama`) |
| `GET /api/chat/models?server=` | List models available on a server |
| `POST /api/chat/send` | Send a message (non-streaming) |
| `POST /api/chat/stream` | Send a message, receive an SSE stream of tokens + tool events |
| `POST /api/chat/reset-context` | Clear a conversation's messages |
| `GET /api/chat/tools` | List registered tools |
| `GET /api/conversations` | List saved conversations, newest first |
| `GET /api/conversations/{id}/messages` | Get a conversation's messages (for resuming) |
| `PATCH /api/conversations/{id}` | Rename a conversation |
| `DELETE /api/conversations/{id}` | Delete a conversation |

## Code Quality & Formatting
All bound to the `verify` phase (so `mvn clean verify` runs everything below), or run individually:
- **Checkstyle** (Google Java Style): `mvn checkstyle:check`
- **PMD** (static analysis): `mvn pmd:check`
- **Spotless** (auto-formatting): `mvn spotless:apply`
- **SpotBugs** (bug detection): `mvn spotbugs:check`
- **JaCoCo** (coverage, ≥90% enforced): `mvn jacoco:report`

## Testing
```sh
cd backend/chatapp
mvn test              # unit + integration tests (JUnit 5, Mockito, Testcontainers)
```
Coverage report: `chatapp/target/site/jacoco/index.html` after running tests.

## Related
- **Frontend:** [../frontend/README.md](../frontend/README.md)
- **Project overview:** [../README.md](../README.md)

## Contributing
- Please open issues or pull requests for improvements or bug fixes.

## Prerequisites
- Java 21 (LTS)
- Maven 3.8+
- Docker (for Testcontainers)

> Runs on Spring Boot 3.x (Jakarta EE namespace) with Java 21.
