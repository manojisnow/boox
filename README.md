# React-SpringBoot Chat Application

This project is a full-stack chat application featuring a React frontend and a Spring Boot backend. It leverages the Ollama-hosted local models for AI-driven conversations and integrates DuckDuckGo for web search capabilities (planned).

---

## Prerequisites

### Required
- **Ollama**: This application requires Ollama to be installed and running locally
  - [Install Ollama](https://ollama.ai/download)
  - After installation, pull the required model:
    ```sh
    ollama pull llama2  # or your preferred model
    ```
  - Start the Ollama server:
    ```sh
    ollama serve
    ```
  - The server will be available at `http://localhost:11434` by default

### Development
- Java 17 (LTS)
- Maven 3.8+
- Docker (for integration tests)
- Node.js & npm (for frontend)

> **Note:** Java 21+ migration will be revisited when the Spring ecosystem and all tools fully support it.

---

## Repository Structure

- [`backend/`](backend/README.md): Multi-module Spring Boot Java backend (API, AI, config, code quality, test separation)
  - `app/`: Main application code
  - Unit tests: Now located in `app/` alongside main code (with JaCoCo coverage)
  - `integration-test/`: Integration tests (Testcontainers, etc.)
  - `contract-test/`: Contract tests (Spring Cloud Contract)
- [`frontend/`](frontend/README.md): React frontend (UI, API integration)

---

## Quick Start

### 1. Clone the repository
```sh
git clone <repository-url>
cd react-springboot-chat-app
```

### 2. Build and Run (All-in-one)
```sh
mvn clean verify
```
- Builds both backend and frontend, runs all code quality checks and tests.

### 3. Run Backend
```sh
cd backend/app
mvn spring-boot:run
```

### 4. Run Frontend
```sh
cd frontend
npm install
npm start
```

---

## Documentation
- **Backend:** [backend/README.md](backend/README.md) (multi-module, code quality, test separation)
- **Frontend:** [frontend/README.md](frontend/README.md)

---

## Contributing
Contributions are welcome! Please open issues or submit pull requests for improvements or bug fixes.

## License
MIT License.

## Backend Prerequisites

- Java 17 (LTS)
- Maven 3.8+
- Docker (for integration tests)

> **Note:** Java 21+ migration will be revisited when the Spring ecosystem and all tools fully support it.

