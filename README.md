# React-SpringBoot Chat Application

This project is a full-stack chat application featuring a React frontend and a Spring Boot backend. It leverages the Ollama-hosted local models for AI-driven conversations and integrates DuckDuckGo for web search capabilities (Yet to be done).

---

**Breaking Change:**
CORS and Ollama API configuration are now set in `backend/src/main/resources/application.properties`.
If you upgrade or pull these changes, review and update your configuration as needed.

---

## Project Structure

The repository is organized into two main directories: `backend` and `frontend`. ### Backend (`backend/`)
- **Spring Boot** Java application
- **Key files & directories:**
  - `Application.java`: Main entry point
  - `controller/ChatController.java`: REST API for chat
  - `service/ChatService.java`: Business logic for chat, AI, and search
  - `engine/ChatEngine.java`, `OllamaChatEngine.java`: Abstractions and implementation for AI model interaction
  - `resources/application.properties`: Configuration
  - `test/java/com/example/chatapp/ChatServiceTest.java`: Unit tests
  - `pom.xml`: Maven dependencies

### Frontend (`frontend/`)
- **React** single-page application
- **Key files & directories:**
  - `src/components/ChatBox.jsx`: Main chat UI
  - `src/components/Message.jsx`: Message display
  - `src/components/Sidebar.jsx`: (if present) Sidebar UI
  - `src/services/api.js`: API calls to backend
  - `src/App.jsx`, `src/index.js`: App entry points
  - `public/index.html`: HTML template
  - `package.json`: npm dependencies

## Backend Configuration

The backend is highly configurable via `backend/src/main/resources/application.properties`. Key properties include:

```
# Ollama API configuration
ollama.api.url=http://localhost:11434
ollama.api.tags-path=/api/tags
ollama.api.chat-path=/api/chat
ollama.api.temperature=0.7
ollama.model.description=Ollama model

# CORS configuration
chat.cors.allowed-origins=http://localhost:3000
chat.cors.allowed-methods=GET,POST,PUT,DELETE,OPTIONS
chat.cors.allowed-headers=*
chat.cors.allow-credentials=true
```

You can adjust these values to match your environment or deployment needs.

## Backend Logging

The backend now uses SLF4J for logging. Key actions, warnings, and errors are logged in the controller, service, and engine layers for better observability and debugging.

## Setup Instructions

### 1. Clone the repository
```sh
git clone <repository-url>
cd react-springboot-chat-app
```

### 2. Build and Run with Maven (Recommended)
You can use the root `pom.xml` to build both the backend and frontend together:
```sh
mvn clean install
```
- This will build both the backend and frontend projects.

To run the backend after building:
```sh
cd backend
mvn spring-boot:run
```

To run the frontend in development mode:
```sh
cd frontend
npm start
```

### Alternative: Build and Run Individually
You can also build and run the backend and frontend separately as described below.

#### Backend Setup
```sh
cd backend
mvn clean install
mvn spring-boot:run
```

#### Frontend Setup
```sh
cd frontend
npm install
npm start
```

## Usage

- Open [http://localhost:3000](http://localhost:3000) in your browser.
- Enter messages in the chat interface. The backend will respond using the Llama model and, when needed, provide web search results from DuckDuckGo.

## Testing

- Backend unit tests: Run `mvn test` in the `backend` directory.

## Contributing

Contributions are welcome! Please open issues or submit pull requests for improvements or bug fixes.

## License

MIT License.

