# Project Structure

## Root Level
```
boox/
├── backend/           # Spring Boot backend application
├── frontend/          # React (Vite) frontend application
├── scripts/           # Utility scripts (e.g., ollama-entrypoint.sh)
├── .kiro/            # Kiro IDE configuration and steering
├── docker-compose.yml # Complete development environment
├── Dockerfile        # Multi-stage build for production (non-root final image)
└── pom.xml           # Parent Maven configuration (backend module only — frontend builds via npm/Vite)
```

## Backend Structure (`backend/chatapp/`)
```
src/main/java/com/example/chatapp/
├── Application.java                    # Spring Boot main class
├── config/
│   ├── AsyncConfig.java               # Bounded SSE thread pool
│   └── SqliteDirectoryInitializer.java # Ensures the SQLite DB directory exists before startup
├── controller/                         # REST API endpoints
│   ├── ChatController.java            # Chat send/models/servers/tools
│   ├── StreamController.java          # SSE streaming endpoint
│   ├── ConversationController.java    # Conversation list/resume/rename/delete
│   ├── CorsConfig.java                # CORS configuration
│   ├── GlobalExceptionHandler.java    # Global error handling
│   ├── dto/                           # Conversation API DTOs
│   └── *Request.java                  # Chat request DTOs
├── service/
│   └── ChatService.java              # Orchestrates engines + context
├── engine/                            # Chat engine + context abstraction
│   ├── ChatEngine.java               # Core chat engine interface
│   ├── OllamaChatEngine.java         # Ollama integration + tool-call loop
│   ├── ChatContextService.java       # Context abstraction (interface)
│   ├── ContextWindowManager.java     # Token-budget window + summarization split (pure, no Spring deps)
│   └── InMemoryChatContextService.java  # Non-persistent fallback, used in unit tests
├── persistence/                       # SQLite-backed persistence (the default context implementation)
│   ├── JpaChatContextService.java    # @Primary ChatContextService implementation
│   ├── ConversationService.java      # Backs ConversationController
│   ├── Conversation.java, ChatMessageEntity.java  # JPA entities
│   └── ConversationRepository.java, ChatMessageRepository.java
├── tool/                              # Tool-calling framework
│   ├── Tool.java                     # Tool interface
│   ├── ToolRegistry.java             # Spring-managed registry of Tool beans
│   └── WebSearchTool.java            # DuckDuckGo-backed web search tool
└── resources/
    ├── application.properties         # Main configuration
    ├── application-docker.properties  # Docker-specific config
    └── META-INF/spring.factories      # Registers SqliteDirectoryInitializer
```

## Frontend Structure (`frontend/`)
```
index.html                    # Vite HTML entry point (project root, not public/)
src/
├── main.jsx                  # React entry point (createRoot)
├── App.jsx                   # Root component — owns active conversation + sidebar
├── App.css                   # Global styles
├── components/
│   ├── ChatBox.jsx          # Main chat interface
│   ├── ChatBox.css
│   ├── Message.jsx          # Message rendering (markdown + code highlighting)
│   ├── Message.css
│   ├── ToolCall.jsx         # Live tool-call card
│   ├── ToolCall.css
│   ├── ConversationSidebar.jsx  # Conversation list/new/resume/rename/delete
│   └── ConversationSidebar.css
└── services/
    └── api.js               # API client functions (chat + conversation management)
```

## Package Organization Patterns

### Backend Packages
- `controller/` - REST endpoints, request/response handling
- `service/` - Business logic, orchestration
- `engine/` - Core chat functionality, AI model integration, context-window logic
- `persistence/` - JPA entities, repositories, and the default (SQLite-backed) context implementation
- `tool/` - Extensible tool system for enhanced capabilities

### Naming Conventions
- **Java Classes**: PascalCase (e.g., `ChatController`, `OllamaChatEngine`)
- **Java Packages**: lowercase with dots (e.g., `com.example.chatapp.engine`)
- **React Components**: PascalCase files with `.jsx` extension
- **CSS Files**: kebab-case matching component names
- **Configuration**: kebab-case properties (e.g., `ollama.api.url`)

### Key Architectural Patterns
- **Layered Architecture**: Controller → Service → Engine/Persistence
- **Strategy Pattern**: `ChatContextService` has two implementations (`JpaChatContextService`, the default; `InMemoryChatContextService`, used in tests)
- **Dependency Injection**: Spring-managed beans throughout backend
- **Component Composition**: React functional components with hooks
