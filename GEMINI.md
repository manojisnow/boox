# Project Overview

This project is a full-stack chat application consisting of a Spring Boot Java backend and a React (Vite) frontend, using Ollama for local AI models. Conversation history is persisted to SQLite and survives restarts; long conversations are kept within a token budget via incremental summarization of older turns.

## Project Structure

-   `backend/`: Contains the Spring Boot application.
    -   `backend/chatapp/`: The main Spring Boot application module.
        -   `src/main/java/`: Java source code (`controller/`, `service/`, `engine/`, `persistence/`, `tool/`).
        -   `src/main/resources/`: Application properties and templates.
        -   `src/test/java/`: Java test code.
        -   `google_checks.xml`: Checkstyle configuration.
        -   `pom.xml`: Maven build file for the backend application.
    -   `backend/pom.xml`: Parent POM for the backend module (BOM import + shared quality-tool config).
-   `frontend/`: Contains the React (Vite) application.
    -   `index.html`: Vite's HTML entry point (project root, not `public/`).
    -   `src/`: React source code (`components/`, `services/api.js`, `main.jsx`, `App.jsx`).
    -   `package.json`: Node.js package configuration, including scripts and dependencies.
    -   Built via `npm run build` (or the Dockerfile's `node` stage) — not Maven; there is no `frontend/pom.xml`.
-   `docker-compose.yml`: Defines the multi-container Docker application (app + Ollama, with named volumes for conversation history and models).
-   `Dockerfile`: Multi-stage build (Node → Maven → JRE) producing a single image that serves both frontend and backend from `:8080`, running as a non-root user.
-   `pom.xml`: Root Maven aggregator — wraps only the `backend` module.

## Code Quality Tools and Checks

### Backend (Java)

The backend uses Maven plugins to enforce code quality:

-   **Checkstyle (`maven-checkstyle-plugin`):** Enforces coding standards and style guidelines using `google_checks.xml`.
    -   **Check Command:** `mvn checkstyle:check` (part of `mvn verify` phase)
-   **PMD (`maven-pmd-plugin`):** Performs static code analysis to find common programming flaws (best practices, codestyle, design, error prone, multithreading, performance, security).
    -   **Check Command:** `mvn pmd:check` (part of `mvn verify` phase)
-   **Spotless (`spotless-maven-plugin`):** Formats Java code using Google Java Format.
    -   **Check Command:** `mvn spotless:check` (part of `mvn verify` phase)
    -   **Apply Formatting:** `mvn spotless:apply`
-   **SpotBugs (`spotbugs-maven-plugin`):** Detects potential bugs.
    -   **Check Command:** `mvn spotbugs:check`
-   **JaCoCo (`jacoco-maven-plugin`):** Measures code coverage for unit tests (≥90% enforced).
    -   **Report Generation:** `mvn jacoco:report` (part of `mvn verify` phase)

To run all backend quality checks and tests, navigate to `backend` and run:
```bash
mvn clean verify
```

### Frontend (React / Vite)

-   **Vitest:** Test runner.
    -   **Check Command:** `npm test`
-   No linter is currently configured for the frontend.

## Build and Deployment Workflow

When making changes to the project, follow these steps to ensure everything is built correctly and the application runs as expected:

1.  **Build Backend (Java) Changes:**
    If you modify any Java code in the `backend/` directory, you need to build the backend JAR:
    ```bash
    cd backend
    mvn clean install
    cd ..
    ```

2.  **Build Frontend (React/Vite) Changes:**
    If you modify any React code in the `frontend/src/` directory, you need to build the frontend static assets:
    ```bash
    cd frontend
    npm install # Only if dependencies changed
    npm run build
    cd ..
    ```

3.  **Build Docker Image:**
    After building both backend and frontend, you need to rebuild the Docker image to include the latest changes (the multi-stage `Dockerfile` builds both from source, so this step alone is usually sufficient):
    ```bash
    docker build -t boox .
    ```

4.  **Run with Docker Compose:**
    Finally, bring up the application using Docker Compose to ensure all services are running correctly:
    ```bash
    docker compose up --build
    ```
    To run in detached mode:
    ```bash
    docker compose up --build -d
    ```
    To stop the services:
    ```bash
    docker compose down
    ```
