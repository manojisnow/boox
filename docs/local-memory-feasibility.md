# Local preferences and project memory: feasibility study

Date: 16 October 2026. Baseline: `e522ae71d098f22c453e54b59aec6cadda5f2e5f`.

Implementation update: the design is now implemented on
`codex/local-preferences-project-memory`. See the
[validation record](local-memory-validation.md) and [user guide](local-memory.md).
Descriptions of baseline gaps below explain the original design decisions.

## Outcome

Proceed with a staged implementation on the existing React, Spring Boot and SQLite
stack. No external memory service, account provider, embedding model, or additional
database is necessary for the agreed first version.

The architecture is feasible and the main product decisions are specified below.
A synthetic SQLite/JDBC probe initially passed 17 checks covering ownership and
independent-note provenance. Subsequent implementation validation covers full-schema
migration, application integration and real local-model tasks; it does not establish
model quality across all supported models.

The companion [implementation plan](local-memory-implementation-plan.md) sequences the
work. The study itself preceded production changes; those changes and their verification
are recorded separately in the validation record.

## Scope and product contract

Included:

- One internal local user, generated per installation and invisible as an account UI.
- Optional onboarding, editable explicit preferences, and conversation-level controls.
- User-owned projects, project instructions, and project membership for conversations.
- Explicit project notes/decisions saved by the user, either manually or from a message.
- Backend context assembly for ordinary Ask, streaming Ask and Agent mode.
- Visibility into which personalization inputs were included and omitted.
- Preservation of existing history and a path to future authenticated ownership.

Excluded: login, registration, cloud sync, multiple selectable local profiles, automatic
fact extraction, personality inference, global learned memory, ChatSorter, semantic
search, and guarantees of secure multi-user hosting.

Onboarding asks how Ullavox should help: response length, explanation style, familiarity,
optional background/goals, and additional working preferences. No field is mandatory.
Do not request credentials or require demographic/sensitive details. Render structured
answers into short instructions deterministically; do not invoke a model to create a
personality assessment or an opaque master prompt. Show the rendered version in Settings.

Onboarding has persisted `not_started`, `completed` and `skipped` states. Skip keeps chat
usable and does not prompt again on every reload. A failed settings request is an error,
not an empty profile to overwrite. Settings remain accessible after onboarding.

## Findings from the current implementation

| Area | Existing behavior | Gap and proposed resolution |
| --- | --- | --- |
| Persistence | SQLite/JPA; Hibernate `ddl-auto=update` in normal and Docker profiles | Introduce versioned migrations and an inspected legacy baseline; backfill ownership before making it mandatory. |
| Ownership | Conversation IDs come from clients; repository operations are globally scoped | Resolve the local owner on the server and scope all reads/writes, including indirect child and agent access. Never accept an owner ID from the request. |
| Preferences | Conversation `systemPrompt`; browser uses a shared `sessionStorage` key | Separate the profile from conversation instructions; restore each conversation's persisted instruction and allow explicit clearing. Do not import the shared browser value silently. |
| Prompt assembly | Ask uses `OllamaChatEngine`; Agent builds its own instructions/history | Share personalization resolution, precedence and budgeting; preserve mode-specific evidence/tool rules. |
| Budget | Ask budgets recent messages, then prepends system instructions and summary; Agent performs a separate approximate final check | Budget the complete outgoing request, reserve output space, and recheck after tool results and before final streaming. |
| Consistency | Ask rebuilds context on tool iterations and final streaming; Agent runs asynchronously | Resolve personalization once when execution starts, then freeze it in memory for that execution. Queued work uses the latest settings at start. |
| Tool messages | Ask persists assistant text without its structured tool calls | Keep complete tool-call/result groups in the execution context; persist only renderable history and clearly labelled historical tool evidence. |
| Agent creation | A run is saved before its new conversation is created | Create/validate the conversation, user turn and queued run in one transaction; schedule only after commit. |
| Message provenance | Database messages have IDs; `MessageView` does not expose them | Add stable message IDs for saving notes and reject invalid, foreign or wrong-project source references. |
| Reset | `JpaChatContextService.resetContext` deletes messages and clears the system prompt but leaves summary state; old agent rows retain evidence/files | Reset all conversation-derived context consistently; otherwise cleared content can return through the summary or an agent follow-up. |
| Mutation coordination | Active-agent checks exist; ordinary sends do not establish an exclusive turn reservation | Coordinate Ask, streaming and Agent acceptance and destructive mutations per conversation. |
| API/browser contract | Rename uses PATCH, but both CORS profiles omit PATCH | Include PATCH in configured allowed methods and exercise browser preflight tests. |
| Model destination | Model DTO exposes name/description/capabilities, not a verified local/cloud classification | Explain that supplied context goes to the configured model; do not infer offline guarantees from a model name. |
| Deployment | No authentication; release ports bind to all interfaces | Keep trusted single-user scope; bind local release ports to loopback by default with documented opt-in network access. |

Evidence locations:

- [Persistence configuration](../backend/chatapp/src/main/resources/application.properties)
  and [Docker configuration](../backend/chatapp/src/main/resources/application-docker.properties).
- [Conversation entity](../backend/chatapp/src/main/java/com/example/chatapp/persistence/Conversation.java),
  [conversation services](../backend/chatapp/src/main/java/com/example/chatapp/persistence/ConversationService.java),
  and [JPA context service](../backend/chatapp/src/main/java/com/example/chatapp/persistence/JpaChatContextService.java).
- [Ask context and streaming](../backend/chatapp/src/main/java/com/example/chatapp/engine/OllamaChatEngine.java),
  [Agent context](../backend/chatapp/src/main/java/com/example/chatapp/agent/AgentExecutionService.java),
  and [agent lifecycle](../backend/chatapp/src/main/java/com/example/chatapp/agent/AgentRunService.java).
- [Browser chat state](../frontend/src/components/ChatBox.jsx),
  [HTTP client](../frontend/src/services/api.js), and
  [message DTO](../backend/chatapp/src/main/java/com/example/chatapp/controller/dto/MessageView.java).

## Data model decision

Use UUIDs generated by the backend for new users, projects and memories. Existing
conversation IDs remain unchanged. This initial design uses immutable ownership;
linking a future login identity to an existing local user is a separate future feature.

| Table/change | Required purpose and fields |
| --- | --- |
| `local_user` | Stable `id`, created timestamp. No email, password or authentication fields. |
| `installation` | Singleton key plus `local_user_id`; resolves the configured local identity rather than selecting an arbitrary first row. |
| `user_preferences` | `user_id` PK/FK, onboarding state, structured response/explanation/familiarity preferences, optional background/goals/additional instructions, version, updated timestamp. Empty values mean unset. |
| `project` | `id`, `owner_id`, name, description, instructions, version, timestamps. Unique `(id, owner_id)` for scoped references. |
| `project_memory` | `id`, `project_id`, `owner_id`, user-written title/content, kind (`note`, `decision`, `constraint`), origin (`manual`, `message`), pinned flag, version, timestamps. Independent project-owned content. |
| `project_memory_source` | Optional provenance row referencing a note and its source conversation/message within the same owner/project. Source deletion cascades to this row only, never to note content. |
| `conversation` additions | Non-null `owner_id`, nullable `project_id`, persisted `use_preferences` and `use_project_context` flags (default true), version for settings changes. |
| Context-use metadata | Turn/run ID, conversation ID, profile/project/memory IDs and versions, inclusion/omission reasons, budget estimates, timestamp. Do not duplicate raw profile/memory text in this trail. |
| Execution snapshot | In-memory personalization and structured tool context for a running turn. No durable raw personalization snapshot; restart fails interrupted runs under the existing recovery policy. |

Messages inherit ownership from their conversation. Agent runs inherit it through a
validated conversation relationship; currently `AgentRun.conversationId` is only a
string, so add database referential integrity after checking for legacy orphans. Scope
run-by-ID lookup/cancellation, retained attachments/evidence, model context, web-session
lookup, source-message lookup and metadata inspection as well as sidebar queries.

Use database constraints plus service checks. A composite `(project_id, owner_id)`
reference prevents attaching one user's chat to another user's project. At save time, a
source must belong to that conversation and project/owner. A provenance row requires
both source IDs and matching note/project/owner references. Manual notes have no provenance
row. A message-origin note without that row displays "Source unavailable". Do not retain
deleted source excerpts or titles in provenance. Content is always explicitly reviewed
before saving; assistant text is not automatically authoritative. Editing the note does
not make its content dependent on the source's lifetime.

Use a service boundary for project-note selection rather than coupling domain entities
to a vector store or external provider. Do not add a broad provider framework now.

## Ownership and asynchronous execution

Introduce a single `CurrentUserResolver` with a local implementation reading the
installation record. Public requests do not provide user IDs. Owner-scoped operations
return 404 for unavailable foreign IDs and never create a new local conversation over
an existing foreign ID. Tests can inject another owner to verify isolation without a
multi-user UI.

Resolve the user and validate the conversation before scheduling SSE/Agent work. Pass
immutable owner/conversation/context identifiers explicitly to workers; request-thread
state alone will not survive asynchronous execution. Background completion must validate
the existing conversation rather than invoke a helper that could recreate a deleted one.

A per-conversation turn coordinator covers ordinary sends, streaming and Agent creation.
It assigns an execution token and rejects conflicting work with 409. A transport timeout,
browser disconnect or cancellation request does not mean the worker has stopped: retain
the reservation and active context references until the worker exits and its final writes
finish. Existing disconnect behavior may continue generation and save the final reply.
Cancellation stops at checkpoints after an in-flight call returns. Check the execution
token before subsequent model/tool calls and writes; cancelled/stale work cannot commit
a reply. A disconnected client can inspect execution status and wait; offer cancellation
only where the backend supports it. Release in worker-finally cleanup, not SSE callbacks.

Use one short-lived mutex per owner for lifecycle metadata transitions in this single-
process release: validate/reserve a conversation; start an execution and register context
references; change settings; or validate and commit a destructive mutation. Under that
mutex, use a short database transaction where needed. Acquire the owner mutex before a
database transaction and never hold it or a database transaction over model/network calls.
Conversation reservations are state entries, not additional held locks. Project operations
inspect all affected reservations under the same owner mutex. This avoids cross-project
lock ordering and closes races across conversations sharing one profile.

For Agent acceptance, within one transaction create/validate the owned conversation first,
apply settings, save its user message, then save the queued run referencing that conversation.
Register the reservation under the owner mutex; undo it on transaction rollback. Dispatch
only after commit. Executor rejection marks the committed run failed and releases its
reservation; the accepted user message remains visible. Test this path on a brand-new
conversation with foreign keys enabled. On restart, recover queued/running runs as failed
before accepting new work. This release supports one Ullavox process per database.

## How saved information enters live requests

The model never reads SQLite directly. The backend builds a `PersonalizationContext`
when its worker begins execution, using the current owner, persisted flags and project membership.
Ask and Agent share this component and its budgeting rules. Each mode retains its own
tool/evidence behavior. The same personalization version is reused across tool calls,
draft/review calls and final streaming for that turn.

For both Ask and Agent, under the owner mutex read a consistent database snapshot, build
the bounded personalization context, and register its active references before releasing
the mutex. Destructive mutations acquire the same mutex, check those references and commit
before releasing it. Thus deletion either completes before resolution or is rejected with
409 while a running execution uses the data; it cannot slip between read and registration.
A failed resolution registers nothing and fails the execution without a model call.

Queued Agent work stores request data and explicit note IDs, but no profile/note text and
no personalization references. Settings edits or deletion before execution therefore affect
that queued work. Revalidate explicitly selected note IDs at execution: fail visibly if a
selected note was removed, rather than silently replacing it. Resolve the project's flags
and preferences at this point and display their actual versions in Context used. Previews
before execution are provisional. Keep the resolved text only in memory until worker exit;
persist IDs/versions and inclusion metadata, never a second raw personalization history.

Deleting/clearing preferences or notes returns 409 while a running execution references
them. The UI offers wait/retry, or cancellation where supported. Non-clearing edits may
proceed and affect executions that have not started yet. Deleting source history does not
delete a saved note, so it does not need to cancel unrelated executions using that note.
An already sent model request cannot be retracted.

| Layer | Inclusion rule |
| --- | --- |
| Application instructions | Always included; enforce tool access, evidence rules and data interpretation. |
| User preferences | Enabled by default; apply saved response preferences across new and existing chats unless that chat opts out. |
| Project instructions | Included when a project is assigned and `use_project_context` is true. |
| Conversation instructions | Existing user-supplied custom instruction, restored from that conversation and editable/clearable. |
| Project notes | Bounded pinned/relevant notes for the current project only, labelled as user-saved context rather than system authority. |
| History and latest request | Existing summary/recent turns and the actual current user request; Agent evidence remains separately labelled. |

Precedence is deliberate: application rules are enforced first; the current explicit
user request overrides general user-level response preferences; conversation instructions
override project defaults, which override general user preferences. Saved notes and old
summaries are contextual data, not instructions granting capabilities or verified evidence.
Placing these sections in a prompt does not guarantee a model will obey the hierarchy;
tool/owner/evidence restrictions must remain enforced by code.

Typing a one-off request must not modify preferences. Saving settings uses a separate
API. Global settings updates take effect on the next execution start, including queued
and resumed chats; already running executions retain their frozen version.
Existing local history and replies may already contain older preferences; disabling or
deleting a preference prevents new injection but cannot retract prior model input or
erase generated references. Offer clear-context separately and state its effect plainly.

### Bounded project-note selection

V1 does not promise automatic understanding of all past project chats. Other chats are
not read wholesale. Only explicitly saved notes can cross the conversation boundary.

Select current-project notes in this deterministic order:

1. Explicitly selected notes for this turn (when supplied).
2. Pinned notes in stable order.
3. Positive lexical matches against the latest request, using title/content terms;
   break ties by latest update and stable ID. No embedding/model call.

Deduplicate by ID, cap the count at five and enforce a total note budget. Do not fill
the remaining slots with unrelated notes. When no terms match, use only selected/pinned
notes. Support browsing/searching and selecting a note manually for vague requests such
as "what did we decide?" This is a limited, explainable baseline, not semantic retrieval.

Show included and omitted notes in Context used. Reject an explicit selection that
cannot fit, instead of silently pretending it was supplied. Apply a configurable save
limit (initial proposal: 200 active notes/project, 2,000 characters/note); tune using
realistic projects. Those are product limits, not measured scalability bounds.

### Prompt budget

Refactor the current windowing behavior before adding extra blocks. Define an effective
positive model context size and output reserve for each request; the existing Ask default
`num_ctx=0` leaves actual capacity implicit. Send the chosen context size explicitly and
honor valid conversation overrides. Include fixed instructions, tool schemas, profile,
project text, summary, messages, images, attached evidence and expected output in estimates.

Share the estimator and accounting algorithm, not a single numerical cap. Ask retains
its configured input ceiling (currently 3,000 estimated tokens) and receives an explicit
positive context-size/output-reserve policy. Agent retains its default `num_ctx=16,384`
and output reserve up to 2,048, with its own optional input ceiling; it must not inherit
Ask's 3,000-token cap. For each mode, available input is context capacity minus output
reserve and safety overhead, bounded by that mode's input ceiling if configured. Preserve
valid conversation overrides. Regression-test previously accepted 24,000-character Agent
file input with representative fixed instructions, rather than assuming upload limits
guarantee every combination fits. Reserve space for the latest request and required inputs
first. Bound personalization to at most 25% of the
available input budget, with an initial absolute cap of 900 estimated tokens. This is a
starting allocation to validate, not a claim about exact token counts. Prefer short
structured preferences and short project instructions; report omitted optional fields.
Never silently truncate an explicit current request, attached file, or selected note.

Prune optional notes, optional background and older history/evidence as appropriate;
summary generation needs its own budget and must operate on raw conversation content,
not a concatenated profile/project prompt. If mandatory input exceeds capacity, fail
before the model call with a clear action. Rebudget after every tool result. Preserve
valid tool-call/message groupings. Use one conservative estimator across Ask/Agent with
image/tool overhead and safety margin; estimates are not exact model tokenization.

### Tool-call representation

Ask currently discards `tool_calls` when saving the assistant row. The context builder
cannot reconstruct a valid tool exchange from that text alone. Introduce an execution-
local structured message list carrying the assistant's full tool calls and every matching
tool result, including the names/identifiers required by the transport. Use that list for
subsequent tool rounds and final streaming rather than reloading a lossy database transcript.
Budget an assistant call plus all its results as one group; keep the active group intact,
or fail with an overflow message if it cannot fit. Multiple calls in one assistant message
are one group, including failure results. Do not execute a tool again merely to rebuild context.

V1 does not resume an interrupted tool loop, so durable tool-call replay is unnecessary.
Persist renderable user/final assistant messages and any historical tool evidence separately
or with a clear record kind. On future turns, legacy/unpaired tool rows are excluded from
protocol replay and may only be used as labelled historical text; do not send orphaned
`tool` role messages. Agent keeps its existing structured execution messages, while using
the shared accounting rules. Capture outgoing payloads to verify both paths.

## Visibility and API semantics

- Bootstrap endpoint returns onboarding state and preferences; account details are absent.
- Preferences GET/PUT/DELETE support explicit clearing and optimistic versions. PUT
  replaces the full editable document; omitted fields clear rather than preserve stale
  data. Return 409 on concurrent updates. DELETE clears data and marks onboarding skipped
  after checking active context references. Clearing populated fields through PUT must
  use the same active-reference protection as DELETE; ordinary non-clearing edits can
  take effect at the next execution start.
- Project CRUD and memory CRUD are owner-scoped; all text fields have server-side limits.
- Conversation-detail/settings API returns its actual system prompt, project and flags.
  PATCH distinguishes an omitted field from an explicit null/empty value. Membership is
  set at conversation creation or changed via an explicit action, not overwritten with
  every send. A first-send creation path preserves compatibility with existing clients.
- Current request shapes for send/stream remain supported. New clients do not resend
  profile answers or memory text every time. A per-turn explicit memory selection carries
  IDs only; the backend validates owner/project and loads the actual content.
- Surface Context used through a named SSE metadata event, an additive non-stream
  response field and Agent run metadata. Store only IDs/versions/reasons after completion;
  show unavailable/deleted revisions as such, rather than pretending current content is
  the exact previous prompt. A preflight preview is explicitly provisional until execution starts.
- Message IDs returned after persistence enable Save for project. Optimistic or partial
  streaming messages cannot be saved until their persisted IDs are available.
- An owner-scoped conversation execution-status endpoint reports queued/running work
  and cancellation state so reconnection does not assume an SSE timeout ended the worker.
  Ask offers wait/retry until its worker ends; existing Agent cancellation remains available.
- A settings read/composition failure must not silently send a different personalized
  request. Allow retry or an explicit send without saved context when technically possible.

## Reset, deletion and project changes

| Action | Defined V1 result |
| --- | --- |
| Reset conversation | Reject while its work is queued/running; remove messages, summary/count, saved conversation system prompt, agent runs/steps/retained files/evidence and context-use metadata. Remove source links to those messages, preserving all saved project notes. Retain owner, project, generation settings and context flags. |
| Delete conversation | Same dependent cleanup, then remove the conversation. All saved project notes and global preferences remain. |
| Delete a saved project note | Reject while referenced by active work, then remove it from future injections. Existing replies may contain it; note deletion is not historical transcript erasure. |
| Clear preferences | Reject while referenced by active work, then remove explicit answers and prevent their future injection; do not wipe projects or conversations. |
| Delete project | Explicit confirmation reports affected chats/notes. Reject active work; delete project notes, detach conversations, then delete the project in one transaction. Conversation transcripts remain. |
| Reassign a conversation | Explicit action with no queued/running work. Existing transcript travels with the chat. Detach old-project provenance links before changing membership; notes remain in their original project with source unavailable. Do not copy notes to the target project. |

Deleting a source cascades only to `project_memory_source`; project-note content has its
own lifetime. Project deletion still deletes its owned notes, after active-reference checks.
The chat reset/delete UI explains that saved project notes remain and links to managing
them. Users explicitly delete a note to forget its saved content. Source unavailable must
not expose another project's history after a move. Future automatic extraction, if added,
will need a different provenance/revocation design.

## Data handling and deployment boundary

All new persistence stays in the same local SQLite file and volume. This does not provide
application-level encryption at rest. Existing host access controls, disk encryption and
private backups remain relevant; do not advertise newly encrypted profile storage.

Onboarding explains that included preferences and project context go to the selected
model, which may be remotely served, and that web-tool queries can disclose information
used by a model. Context toggles control inclusion; files-only controls web tools, not
model destination. Do not log full preferences, composed prompts, or context previews.
Review existing INFO tool-argument logs because model-generated queries can contain
profile/project details. Keep arbitrary raw HTML out of new editing/preview surfaces.

A local owner ID is organization for future authentication, not an authentication
mechanism today. CORS is also not authentication. Bind release app and Ollama ports to
loopback by default and document intentional remote access. No signup/token service or
new internet dependency is introduced. Strict offline operation still requires local
models, disabled web tools and network controls; it is separate from local persistence.

## Migration decision and evidence

Use versioned migrations (proposed: Flyway with the existing Xerial driver), followed
by Hibernate schema validation rather than Hibernate-owned upgrades. Official Flyway
documentation lists SQLite support and its single-migrator limitation. SQLite documents
the parent-table rebuild procedure and required foreign-key handling. Neither source
alone validates integration with Ullavox's exact dependency set.

Migration rules:

1. Back up the stopped data directory, including WAL/SHM as a set; offer the existing
   documented backup procedure. Support one migrator/app process per database.
2. Inspect schema metadata. An empty database gets the full baseline. A recognized legacy
   shape gets an explicit baseline/adoption path. Never automatically bless an arbitrary
   non-empty database; reject unknown shapes/orphans without modifying them.
3. Create a random local owner and singleton installation row exactly once. Backfill all
   legacy conversations; leave project null and preferences empty. Preserve messages,
   images, summaries/counts, custom prompts, generation settings and agent evidence.
4. Add non-null ownership and scoped foreign keys through tested SQL. Where rebuilding
   a parent, preserve every legacy column/index/trigger and all child IDs. Never rename
   the old parent first. Manage foreign-key mode outside the migration transaction on
   that dedicated connection, run foreign-key checks before commit, and restore it after
   both success and failure. Enable FK enforcement on every application connection.
5. Migration history/checksums prevent replay. Fail startup on migration/validation
   failure. Existing active agent tasks follow the current interrupted-task recovery.
6. Test fresh, populated, older recognized variants, failed/interrupted and repeated
   startup. Rollback of a released app uses the complete pre-upgrade backup; do not run
   an old binary against the new schema and assume it is compatible.

Executed probe: [SqliteOwnershipProbe.java](studies/local-memory/SqliteOwnershipProbe.java).
It uses a reduced synthetic legacy schema with representative summary/settings, image
and agent rows, and Ullavox's configured `sqlite-jdbc:3.53.4.0` (SQLite 3.53.4). It does not
open a real application database. It uses `user_version` purely as a study marker;
production migration history will belong to the chosen migration framework.

Results after architecture-review remediation: 17/17 passed: FK enablement, transactional
DDL/data rollback on injected failure,
legacy values/child preservation, owner backfill, stable repeat adoption, rejection of a
missing owner, cross-owner project link rejection, source-message/conversation matching,
complete provenance validation, preservation of edited/manual notes after source deletion,
provenance-only cleanup, note preservation in the original project after source movement,
note survival after source-conversation deletion, deletion of notes with their owning
project without deleting another project's chat, foreign-key check, integrity check, and
restored FK enablement. This supersedes the original 13-check probe's source-to-note cascade
policy; that policy is no longer part of the proposed design.

Reproduce with Java 17+ and the cached/configured driver (adjust the Maven cache path):

```sh
java --class-path "$HOME/.m2/repository/org/xerial/sqlite-jdbc/3.53.4.0/sqlite-jdbc-3.53.4.0.jar" \
  docs/studies/local-memory/SqliteOwnershipProbe.java
```

This probe does not test Flyway, JPA mappings, the full production schema, filesystem
crash recovery, multiple processes, restart durability, UI behavior or real model
responses. Those limitations are reflected in the implementation gates.

References:

- [Flyway SQLite driver and limitations](https://documentation.red-gate.com/flyway/reference/database-driver-reference/sqlite).
- [SQLite schema changes](https://www.sqlite.org/lang_altertable.html).
- [SQLite foreign-key behavior](https://www.sqlite.org/foreignkeys.html).
- [Existing backup/rollback and deployment scope](releases/v1.5.0.md).

## Implementation validation and remaining work

The implemented architecture retains the existing stack. Full backend and frontend
verification, a private-copy legacy database upgrade, a local-model acceptance script
and packaged-container restart checks passed. The
[validation record](local-memory-validation.md) maps these checks to the implementation.

Execution coordination tests distinguish worker completion from stream completion,
exercise atomic context/deletion registration, and check first-Agent-turn transaction
ordering. Prompt tests cover separate mode budgets and complete tool groups. Ownership,
version conflicts and independent-note provenance have service/database tests.

Saved project notes intentionally survive reset; the "no resurrection" requirement
concerns cleared conversation history/summary/Agent evidence. Independently retained
project notes remain available when enabled.

The user reported successful manual browser testing on 18 October 2026. Broader
preference usefulness and model obedience remain product-evaluation work; the local-model
smoke checks do not establish these for every model. Perform the documented pre-upgrade
backup and restore procedure before deploying against a real installation.
