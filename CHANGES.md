# Boox — Development History

Two separate development sessions, ten total sub-phases across two plans.

---

## Session 1: Frontend Overhaul (4 phases)

Goal: fix real bugs, remove accumulated dead code, and establish a clean design foundation before adding features.

### Phase 1 — Clean the House
- Deleted dead `Sidebar.jsx` + `Sidebar.css` (never imported anywhere)
- Purged ~450 lines of dead CSS from `ChatBox.css` (`.gemini-*`, `.chat-bubble-*`, duplicated selectors)
- Removed a debug `rgba(255,0,0,0.1)` background that shipped in `App.css`
- Established 25 CSS design tokens in `index.css` `:root` (colors, spacing, radii, shadows, transitions)
- Fixed font stack to system fonts first

### Phase 2 — Core UX
- Single Enter to send, Shift+Enter for newline
- Typing indicator (animated dots) while waiting for response
- Auto-resizing textarea (capped at 120px)
- Send button disabled only when input is empty or locked, not permanently after first message
- Model dropdown unlocked during conversation
- Model change mid-chat resets session automatically

### Phase 3 — Visual Polish & Dark Mode
- Dark mode via `prefers-color-scheme` media query
- Gradient user bubbles, flat bot bubbles
- Smooth hover/focus transitions throughout
- Compact inline controls bar

### Phase 4 — Errors, Icons & Accessibility
- Auto-dismissing toast notifications (5s) for errors
- SVG icons replacing emoji (send button, system prompt toggle)
- `aria-label` on all interactive elements
- Error clears automatically when user selects server/model

---

## Session 2: Smart Chatbot Enhancements (6 phases)

Goal: turn the cleaned-up chat UI into a proper smart chatbot with streaming, system prompts, and tool calling.

### Phase 1 — Markdown Rendering
- Installed `react-markdown`, `remark-gfm`, `react-syntax-highlighter`
- Bot messages now render as rich markdown with syntax-highlighted code blocks
- User messages stay as plain text
- Fixed a long-standing API bug: `resetContext` was calling `/api/chat/reset` but backend expected `/api/chat/reset-context`

### Phase 2 — Real-time SSE Streaming
- New `StreamController.java` — `POST /api/chat/stream` returns `SseEmitter`
- `OllamaChatEngine.streamFinalResponse()` reads Ollama's NDJSON stream and forwards tokens as SSE events
- Frontend `ChatBox.jsx` reads the stream with `fetch` + `ReadableStream`, appending tokens to the last message in real time
- Stream toggle in the input bar (on by default)

### Phase 3 — System Prompts
- `SendMessageRequest` gains an optional `systemPrompt` field
- `ChatContextService` / `InMemoryChatContextService` extended with `setSystemPrompt` / `getSystemPrompt`
- `OllamaChatEngine.buildMessagesWithSystemPrompt()` prepends a system message when set
- Frontend: collapsible system prompt textarea in the input bubble (persists to `sessionStorage`)

### Phase 4 — Tool Calling Framework
- `Tool.java` interface: `getName()`, `getDescription()`, `getParameters()`, `execute(Map args)`
- `ToolRegistry.java`: spring-managed registry, auto-wires all `Tool` beans, builds Ollama-compatible `tools` definition array
- `OllamaChatEngine.sendMessage()` extended with a tool loop: up to 5 iterations of call → extract tool_calls → execute tools → add results to context → re-call
- `ChatContextService` gains `addMessage()` for arbitrary role (needed for `"tool"` role messages)

### Phase 5 — Web Search Tool
- `WebSearchTool.java`: implements `Tool`, calls DuckDuckGo Instant Answer API (`duckduckgo.com/json?q=...&format=json`)
- Parses `AbstractText`, top 3 `RelatedTopics`, and `Answer` fields into a plain-text summary
- Enabled via `tools.web-search.enabled=true` in `application.properties`
- Removed the old stub `searchWeb()` from `ChatService` and the unused `GET /api/chat/search` endpoint
- Full unit tests for `WebSearchTool` and `ToolRegistry`; JaCoCo at 90%+

### Phase 6 — Frontend Tool Awareness
- `ToolCall.jsx` + `ToolCall.css`: collapsible card component showing tool name, query, and result
  - Pulsing magnifier icon while search is in progress (`status: "searching"`)
  - Checkmark + expandable result panel when done (`status: "done"`)
- `OllamaChatEngine.streamMessage()` extended to emit named SSE events:
  - `event: tool_call` + JSON payload before executing a tool
  - `event: tool_result` + JSON payload after receiving the result
- `ChatBox.jsx` SSE parser updated to track `event:` lines and route accordingly:
  - `tool_call` → append a new `ToolCall` entry with `status: "searching"` to the message
  - `tool_result` → find matching call by name and update it to `status: "done"` with result
  - plain data → append text token as before
- `Message.jsx` renders tool call cards above the markdown text inside the same bubble

---

---

## Session 3: Code Review & Hardening

Goal: senior-engineer review of all prior work, fix every identified issue before shipping to main.

### What was found
A structured review of both sessions identified 10 issues across backend and frontend:

| # | Area | Issue |
|---|------|-------|
| 1 | `StreamController` | Used `Executors.newCachedThreadPool()` — unbounded, never shut down, anonymous threads |
| 2 | `OllamaChatEngine` | `new ObjectMapper()` created on every tool call inside a loop |
| 3 | `OllamaChatEngine` | `callOllamaWithTools()` returned nullable `Map` (null = empty body) |
| 4 | `OllamaChatEngine` | `sendMessage` and `streamMessage` each had an identical ~40-line tool loop |
| 5 | `WebSearchTool` | Description said "search the web for current information" — overpromises DDG Instant Answers |
| 6 | `InMemoryChatContextService` | Both session maps grow unbounded, no TTL or eviction noted |
| 7 | `ChatBox.jsx` | Tool result matched by tool name only — fragile if same tool is called twice in one turn |
| 8 | `ChatBox.jsx` | `line.slice(5).trim()` stripped leading spaces from Ollama tokens before appending |
| 9 | `api.js` | `credentials: 'include'` on the `fetch` stream call but not on axios calls |
| 10 | `ChatBox.jsx` | Malformed SSE JSON silently swallowed (`catch (_) {}`) |

### What was fixed

**Backend**
- `AsyncConfig.java` *(new)*: Spring `@Bean("streamTaskExecutor")` — bounded `ThreadPoolTaskExecutor` (core=4, max=20, queue=50, thread prefix `sse-stream-`)
- `StreamController.java`: injects `@Qualifier("streamTaskExecutor") TaskExecutor` via constructor — no more unmanaged pool. Thread names now appear in logs as `[sse-stream-N]`
- `OllamaChatEngine.java`:
  - `private static final ObjectMapper MAPPER` at class level — shared, thread-safe, allocated once
  - `callOllamaWithTools()` returns `Optional<Map<String,Object>>` — callers use `.isEmpty()` instead of null checks
  - New `executeToolLoop(model, sessionId, emitter)` private method: single implementation of the tool-call loop; when `emitter` is non-null it emits named SSE events, when null it runs silently (for non-streaming path). ~80 lines reduced to ~40
  - SSE tool events now include a sequential `"index"` field so the frontend can match calls and results by position rather than name
- `WebSearchTool.java`: accurate description; `private static final ObjectMapper MAPPER` replaces per-call instantiation
- `InMemoryChatContextService.java`: TODO comment added pointing to Caffeine/scheduled cleanup for production use
- `StreamControllerTest.java`: updated to inject `SyncTaskExecutor` so the runnable executes synchronously — no more flaky `timeout(1000)` waits

**Frontend**
- `ChatBox.jsx`: tool result matching uses `tc.index === parsed.index` — correct for repeated tool calls
- `ChatBox.jsx`: `rawData = line.slice(5)` (no trim) preserves whitespace tokens; `.trim()` only for `[DONE]` sentinel comparison
- `ChatBox.jsx`: malformed SSE payloads now `console.warn(...)` instead of silent discard
- `api.js`: removed `credentials: 'include'` from `fetch` — consistent with axios behaviour for a local app

**Quality gates after fixes**: 58 tests pass · JaCoCo ≥ 90% · SpotBugs 0 bugs · Spotless clean · Frontend build clean

---

## What was originally planned as a Phase 7?

There was no Phase 7 in either plan. The smart chatbot plan explicitly covered 6 phases. You might be counting the two separate planning sessions together (4 + 6 = 10 sub-phases, loosely remembered as 7). If a 7th feature phase were added, natural candidates would be:
- **Conversation persistence** — save/restore chat history across page reloads (localStorage or a backend store)
- **Multiple chat sessions** — sidebar to switch between named conversations
- **Model configuration UI** — temperature slider, context length, stop tokens exposed in the UI
- **More tools** — calculator, URL fetcher, file reader
