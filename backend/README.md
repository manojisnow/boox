# Spring Boot Backend for Chat Application

This directory contains the **Spring Boot** backend for the chat app. It is now organized as a multi-module Maven project, with strict code quality enforcement and clear separation of test scopes.

[← Back to Project Overview](../README.md)

---

## Features
- REST API for chat and context management
- Integration with Ollama (Llama model) for AI responses
- (Planned) Web search via DuckDuckGo
- In-memory chat context (thread-safe)
- Input validation and global exception handling
- SLF4J logging throughout the stack
- Configurable CORS and AI parameters
- **Code quality enforced with Checkstyle, PMD, Spotless, and SpotBugs (parent-level)**
- **Test separation:** Unit, integration, and contract tests are isolated in dedicated submodules

## Backend Structure
```
backend/
  README.md                # This file
  pom.xml                  # Parent POM (enforces code quality for all submodules)
  app/                     # Main Spring Boot application code and unit tests
  integration-test/        # Integration tests (Testcontainers, etc.)
  contract-test/           # Contract tests (Spring Cloud Contract)
```

### Submodules
- **app/**: Main application code, configuration, and unit tests (with JaCoCo coverage)
- **integration-test/**: Integration tests (e.g., with Testcontainers)
- **contract-test/**: Contract tests and contract definitions

## Setup & Development
1. **Install dependencies & build all modules:**
   ```sh
   mvn clean verify
   ```
2. **Ensure Ollama is running:**
   - Install Ollama from [ollama.ai/download](https://ollama.ai/download)
   - Pull your preferred model: `ollama pull llama2`
   - Start the server: `ollama serve`
   - The default configuration expects Ollama at `http://localhost:11434`

3. **Run the backend app:**
   ```sh
   cd app
   mvn spring-boot:run
   ```
4. **Configuration:**
   - Edit `app/src/main/resources/application.properties` to configure:
     ```properties
     # Ollama Configuration
     ollama.api.url=http://localhost:11434  # Ollama API endpoint
     ollama.model.name=llama2               # Model to use
     ollama.timeout=120                     # Request timeout in seconds
     
     # CORS and other properties...
     ```

## Code Quality & Formatting (Parent-Enforced)
- **Checkstyle** (Google Java Style):
  ```sh
  mvn checkstyle:check
  ```
- **PMD** (code quality):
  ```sh
  mvn pmd:check
  ```
- **Spotless** (auto-formatting):
  ```sh
  mvn spotless:apply
  ```
- **SpotBugs** (static bug detection):
  ```sh
  mvn spotbugs:check
  ```
- All tools are enforced at the parent level and run for all submodules.

## Testing
- **Unit tests:**
  ```sh
  cd app
  mvn test
  # or from backend root: mvn -pl app test
  ```
- **Integration tests:**
  ```sh
  cd integration-test
  mvn test
  # or from backend root: mvn -pl integration-test test
  # Requires Docker running (uses Testcontainers)
  ```
- **Contract tests:**
  ```sh
  cd contract-test
  mvn test
  # or from backend root: mvn -pl contract-test test
  ```
- **Coverage report:**
  - Generated in `app/target/site/jacoco/index.html` after running unit tests.

## Related
- **Frontend:** [../frontend/README.md](../frontend/README.md)
- **Project overview:** [../README.md](../README.md)

## Contributing
- Please open issues or pull requests for improvements or bug fixes.

## Prerequisites
- Java 17 (LTS)
- Maven 3.8+
- Docker (for Testcontainers)

> **Note:** Java 21+ migration will be revisited when the Spring ecosystem and all tools fully support it. 