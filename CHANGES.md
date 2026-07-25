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

There was no Phase 7 in either plan. The smart chatbot plan explicitly covered 6 phases. You might be counting the two separate planning sessions together (4 + 6 = 10 sub-phases, loosely remembered as 7). At the time, natural candidates for a 7th feature phase were:
- ~~**Conversation persistence** — save/restore chat history across page reloads (localStorage or a backend store)~~ — built in Session 5 below (SQLite-backed, not localStorage)
- ~~**Multiple chat sessions** — sidebar to switch between named conversations~~ — built in Session 5 below
- **Model configuration UI** — temperature slider, context length, stop tokens exposed in the UI — still open
- **More tools** — calculator, URL fetcher, file reader — still open

---

## Session 4: Stack Modernization

Goal: move off stacks that had gone EOL or unmaintained (Spring Boot 2.6.6, Create React App 4) before adding more features on top of them.

- **Backend:** Spring Boot 2.6.6 → 3.5.3, Java 17 → 21. `javax.*` → `jakarta.*` in the validation-annotated classes; REST Assured/Mockito/Testcontainers versions handed off to the Boot BOM instead of being pinned by hand; removed the unused `spring-cloud-starter-openfeign` dependency (HTTP was always `RestTemplate`); added a `NoResourceFoundException` handler so Spring 6.1's stricter 404 behavior doesn't fall through to the catch-all 500 handler.
- **Frontend:** Create React App 4 → Vite 8, React 17 → 19, axios 0.21 → 1.18, react-markdown 8 → 10 (its `code` renderer API changed — `Message.jsx`'s `CodeBlock` was updated accordingly). `src/index.js` → `src/main.jsx`; `public/index.html` → root `index.html`.
- **Fix:** the Boot 3 upgrade silently broke `GET /api/chat/models` — Spring 6 removed the debug-symbol fallback for method parameter names, so `@RequestParam` without an explicit name needs the compiler's `-parameters` flag, which this project's POM never set (it imports the Boot BOM directly rather than using `spring-boot-starter-parent`, which sets it by default). Caught via a live smoke test against Ollama, not by the unit tests — added a MockMvc-based regression test that exercises real param binding.

---

## Session 5: Conversation Persistence & Context Window Management

Goal: the two biggest product gaps — chat history vanished on every restart, and long conversations had no ceiling on what got sent to the model.

### Conversation Persistence
- `JpaChatContextService` — a SQLite-backed (`Spring Data JPA` + `sqlite-jdbc` + Hibernate's SQLite dialect) implementation of the existing `ChatContextService` interface, made the default (`@Primary`) over `InMemoryChatContextService`. Because the engine already wrote every message through that interface, this required no changes to `OllamaChatEngine` itself.
- `Conversation` / `ChatMessageEntity` JPA entities; auto-titled from the first user message; `Conversation` implements `Persistable` so its assigned-String id uses JPA `persist` rather than `merge` (the default merge path silently diverges the managed instance for entities with non-generated ids).
- New `ConversationController` (`/api/conversations`) — list, resume (fetch messages, filtering out intermediate tool-call rows for a clean replay), rename, delete.
- Frontend: `ConversationSidebar.jsx` — list, new chat, resume, inline rename, delete. `ChatBox.jsx` uses the conversation id as the session id, loads history on mount, and no longer wipes the conversation when the model is switched mid-chat (previously it did).
- **Fix:** navigating away while a response was still streaming both hid the new conversation from the sidebar (it only refreshed after streaming completed) and dropped the assistant's reply (the backend only persisted it after the stream finished, so a disconnect mid-stream lost it). Fixed by refreshing the sidebar as soon as a message is sent, and by having the backend keep consuming Ollama and persist the full reply even after the SSE client disconnects.
- Docker: a named `boox_data` volume at `/app/data` so the SQLite file survives container recreation.

### Context Window Management
- `ContextWindowManager` — pure, unit-tested: estimates tokens (~chars/4) and splits a conversation's messages into a recent window that fits a budget plus the older messages that don't, nudging the window boundary to start on a `user` message.
- Incremental summarization: a running `summary` + `summarizedCount` live on the `Conversation`; each turn folds only the *newly* dropped messages into the existing summary via one Ollama call (best-effort — a failure is logged and the chat continues). The summary is injected as a system message; the full message history is untouched in storage.
- **Fix (found in the same pass):** `temperature` was being sent at the top level of the Ollama request payload, where Ollama silently ignores it — moved under `options`, alongside an optional `num_ctx`.

---

## Session 6: Security Hardening

Goal: run [laria](https://github.com/manojisnow/laria) (an in-house multi-tool security scanner) against the repo and act on real findings.

- **Dockerfile ran as root** — the final image had no `USER` directive. Added a dedicated system user/group, `chown`'d `/app` (including `/app/data`, where the SQLite file lives) before switching, and verified end-to-end: the process runs as a non-root uid, a real chat message still persists to SQLite through the Docker volume, and the container's own `HEALTHCHECK` reports healthy.
- **GitHub Actions workflows had no top-level `permissions` block**, so `GITHUB_TOKEN` defaulted to write-all. Added `permissions: contents: read` as the workflow-level default in `ci.yml`, `release.yml`, and `qodana_code_quality.yml`; the jobs that genuinely need more (the release workflow's publish job, Qodana's PR-comment job) keep their existing job-level elevation.
- Everything else the scan surfaced was noise from stale `.claude/worktrees/` checkout copies and a gitignored JetBrains workspace file — not real findings.
