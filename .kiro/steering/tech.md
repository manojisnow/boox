# Technology Stack

## Backend
- **Java 21** (LTS) - Primary language
- **Spring Boot 3.5.3** - Web framework and dependency injection (Jakarta EE namespace)
- **Maven** - Build system and dependency management
- **Spring Web** - REST API development
- **Spring Data JPA** - Persistence (conversation history)
- **SQLite** (`sqlite-jdbc` + Hibernate community dialect) - Conversation history storage, file-backed
- **Spring Boot Actuator** - Application monitoring
- **RestTemplate** - HTTP client for the Ollama API
- **Jackson** - JSON serialization/deserialization

## Frontend
- **React 19** - UI framework
- **JavaScript/JSX** - Primary language
- **Vite 8** - Build tooling and dev server
- **Axios 1.x** - HTTP client
- **CSS3** - Styling

## External Services
- **Ollama** - Local AI model hosting
- **DuckDuckGo Instant Answer API** - Web search tool

## Development Tools
- **Docker & Docker Compose** - Containerization and orchestration
- **Checkstyle** - Code style enforcement (Google Java Style)
- **PMD** - Static code analysis
- **SpotBugs** - Bug detection
- **Spotless** - Code formatting
- **JaCoCo** - Code coverage (≥90% enforced)
- **JUnit 5** - Unit testing
- **Mockito** - Mocking framework
- **Testcontainers** - Integration testing
- **Vitest** - Frontend testing

## Common Commands

### Development Setup
```bash
# Full stack with Docker (recommended)
docker compose up -d

# Backend only
cd backend/chatapp
mvn spring-boot:run

# Frontend only
cd frontend
npm install
npm run dev
```

### Build & Test
```bash
# Backend: build + all quality gates + tests
cd backend
mvn clean verify

# Frontend: build
cd frontend
npm run build

# Frontend: tests
cd frontend
npm test
```

### Docker Operations
```bash
# Build and start services
docker compose up -d

# View logs
docker compose logs -f

# Download an AI model
docker exec <ollama_container_id> ollama pull phi4-mini
```
