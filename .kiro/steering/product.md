# Product Overview

Boox is a modern AI chat application that provides a web-based interface for interacting with local AI models through Ollama. The application enables users to have conversations with AI models in a clean, responsive chat interface.

## Key Features

- Real-time chat interface with AI models (SSE streaming)
- Integration with Ollama for local AI model hosting
- Web search capabilities through DuckDuckGo integration
- Configurable model parameters (temperature, model selection)
- **Persistent conversation history** — conversations survive restarts (SQLite), with a sidebar to list, resume, rename, and delete them
- **Context window management** — long conversations stay fast and coherent via a token-budgeted window and incremental summarization of older turns
- Modern React frontend with Spring Boot backend
- Docker-based deployment with zero-configuration setup, running as a non-root container

## Target Use Cases

- Local AI model interaction without cloud dependencies
- Development and testing of AI chat applications
- Educational purposes for understanding AI integration
- Privacy-focused AI conversations using local models

## Architecture

The application follows a full-stack architecture with:
- React frontend for user interface
- Spring Boot backend providing REST APIs
- Ollama service for AI model hosting
- Optional web search integration for enhanced responses