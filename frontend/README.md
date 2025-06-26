# React Frontend for Chat Application

This directory contains the **React** single-page application (SPA) for the chat app. It communicates with the Spring Boot backend for AI-driven chat and (optionally) web search.

[← Back to Project Overview](../README.md)

---

## Features
- Modern chat UI with message history
- Real-time communication with backend AI (Llama model via Ollama)
- (Planned) Web search integration via DuckDuckGo

## Project Structure
- `src/components/` – UI components (ChatBox, Message, Sidebar)
- `src/services/api.js` – API calls to backend
- `src/App.jsx`, `src/index.js` – App entry points
- `public/index.html` – HTML template
- `package.json` – npm dependencies and scripts

## Setup & Development
1. **Install dependencies:**
   ```sh
   npm install
   ```
2. **Start the development server:**
   ```sh
   npm start
   ```
   - App runs at [http://localhost:3000](http://localhost:3000) by default.
3. **API configuration:**
   - The frontend expects the backend to be running at the URL specified in the backend's CORS config (see [backend setup](../backend/README.md)).

## Usage
- Open the app in your browser and start chatting!
- Messages are sent to the backend and responses are displayed in real time.

## Customization
- Edit or extend components in `src/components/` to change the UI.
- Update `src/services/api.js` to modify how the frontend communicates with the backend.

## Contributing
- Please open issues or pull requests for improvements or bug fixes.
- For backend/API changes, see [backend/README.md](../backend/README.md).