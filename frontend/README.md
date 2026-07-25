# React Frontend for Chat Application

This directory contains the **React** single-page application (SPA) for the chat app, built with **Vite**. It communicates with the Spring Boot backend for AI-driven chat, tool calling, and conversation history.

[← Back to Project Overview](../README.md)

---

## Features
- Modern chat UI with real-time streaming responses (SSE) and markdown rendering
- **Conversation sidebar** — list, resume, rename, and delete saved conversations (backed by the persistent backend)
- Live tool-call cards while the model is using a tool (e.g. web search)
- Dark mode, system prompt editor, and the other UX detailed in the [root README](../README.md#features)

## Project Structure
- `index.html` — Vite's HTML entry point (project root, not `public/`)
- `src/main.jsx` — React entry point (mounts `<App />`)
- `src/App.jsx` — root component; owns the active conversation and the sidebar
- `src/components/` — `ChatBox.jsx` (main chat interface), `Message.jsx`, `ToolCall.jsx`, `ConversationSidebar.jsx`
- `src/services/api.js` — Axios/fetch client for the backend API
- `vite.config.js` — dev server (port 3000, proxies `/api` to the backend on `:8080`) and build config

## Setup & Development
1. **Install dependencies:**
   ```sh
   npm install
   ```
2. **Start the dev server:**
   ```sh
   npm run dev
   ```
   - App runs at [http://localhost:3000](http://localhost:3000); API calls are proxied to `http://localhost:8080` (see `vite.config.js`), so no CORS setup is needed in dev.
3. **Build for production:**
   ```sh
   npm run build     # outputs to build/ (the Dockerfile copies this into the backend's static resources)
   npm run preview   # serve the production build locally
   ```
4. **Run tests:**
   ```sh
   npm test          # Vitest
   ```

## Usage
- Open the app in your browser and start chatting!
- Use the sidebar (☰ top-left) to start a new chat or resume a previous one — conversations persist across restarts.

## Customization
- Edit or extend components in `src/components/` to change the UI.
- Update `src/services/api.js` to modify how the frontend communicates with the backend.

## Contributing
- Please open issues or pull requests for improvements or bug fixes.
- For backend/API changes, see [backend/README.md](../backend/README.md).
