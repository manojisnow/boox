# Local preferences and project memory: implementation plan

Date: 16 October 2026. Based on the [feasibility study](local-memory-feasibility.md).
Status: implemented on `codex/local-preferences-project-memory`, including the
architecture-review remediations. Backend, frontend, real local-model and container
checks passed. The user reported successful manual testing on 18 October 2026.
See the [implementation validation record](local-memory-validation.md) for
evidence and limits. The increments below retain the original delivery checklist.

## Delivery strategy

Ship a useful preferences slice before projects or cross-chat memory. Each slice must
preserve current standalone chat, streaming, model settings, and Agent evidence behavior.
Keep schema migration and data-loss checks ahead of UI work that writes new personal data.

Implement one internal owner; do not add accounts, external memory services, embeddings
or automatic inference of preferences. Use the study's decisions as the behavioral
contract. Initial limits/budget allocations are configurable proposals to validate.

## Increment 0: migration and ownership foundation

Deliverables:

- Integrate a versioned migration runner (proposed Flyway) with the existing driver and
  Spring Boot version. Prove startup order before JPA, then switch both application
  profiles from `ddl-auto=update` to validation. Keep test profiles deliberate: migration
  tests must not substitute Hibernate `create-drop` for an actual upgrade.
- Check the full legacy schema and orphan conditions before baseline adoption. Create
  separate paths for an empty database and supported legacy shapes. Unknown/custom
  schemas fail safely. Reproduce current tables before introducing ownership constraints.
- Add `local_user`, singleton installation identity, conversation owner, supporting
  indexes/FKs, and the migration/backfill. Preserve all existing IDs and fields.
- Introduce `CurrentUserResolver` and an explicit owner scope passed through synchronous
  and asynchronous services. Update `ChatContextService` and its in-memory implementation
  so tests and contract profiles also enforce the intended boundary.
- Scope conversation, message, agent-run/step, retained-evidence and provider-session
  access through ownership. Add an AgentRun-to-conversation relationship/constraint after
  validating existing rows. Backend-generated IDs apply to new resources except legacy
  client conversation IDs, which remain supported with validated creation semantics.
- Change Agent acceptance to a single transaction: create/validate the owned conversation,
  apply settings, save the user message, then insert the queued run. Dispatch only after
  commit. A rollback leaves neither a partial run nor a new partial conversation; executor
  rejection after commit marks the run failed and leaves the accepted user message visible.
- Add conversation mutation/turn coordination for Ask/SSE/Agent and reset/delete. Do not
  hold SQLite write transactions across model calls; document one app process per DB.
  Use one short owner mutex for metadata transitions, acquired before DB transactions;
  conversation reservations are state entries. Give workers an execution token and check
  it before subsequent model/tool calls and writes. A disconnect/timeout is transport
  state, not worker completion: keep the reservation until the worker and final writes
  finish. Cancellation requests retain it until acknowledged at a checkpoint and cleanup.
- Add owner-scoped conversation execution status for reconnect/wait behavior after an
  SSE timeout. Report queued/running/cancellation state without profile text. Ask waits
  for actual worker completion; retain existing Agent cancellation instead of assuming
  an unimplemented Ask cancellation capability.

Primary code: `persistence/*`, `engine/ChatContextService`,
`engine/InMemoryChatContextService`, `service/ChatService`, controllers,
`agent/AgentOrchestrator`, `agent/AgentRunService`, `tool/WebSessionService`, datasource
configuration, dependency and migration resources.

Exit gate:

- Fresh install and upgrades from recognized populated fixtures preserve messages,
  images, summaries, generation settings and agent attachments/evidence.
- Rerun leaves the same owner; injected failure rolls back safely; unknown schema fails
  before writes. Backup restoration returns a usable prior-version fixture.
- Every pooled connection enforces FKs. Second-owner tests cover list/read/write/reset,
  ID collisions, run inspection/cancellation, evidence and provider-session access.
- First Agent turn on a new conversation succeeds with FKs enabled. Inject failures
  before commit and executor rejection after commit; verify the specified atomic outcomes.
- Concurrent Ask/Agent/stream requests return 409 for conflicting work. Simulate timeout
  or disconnect while a slow model is still running: reset/delete/another turn remain
  blocked until actual worker termination. Cancelled/stale tokens cannot write replies;
  reservations release on worker cleanup, including errors and rejected dispatch.
- Existing targeted backend/contract behavior remains passing.

If the migrator fails to integrate safely, stop this increment and resolve the migration
design before adding data-writing UI. The SQL study probe alone is not this gate.

## Increment 1: shared context assembly and existing-state corrections

Deliverables:

- Add immutable `PersonalizationContext`/context-use metadata and a shared resolver and
  conservative budget component. Start with empty personalization; integrate into both
  `OllamaChatEngine` and `AgentExecutionService` without changing their tool permissions.
- Resolve/freeze once when the worker starts, reusing the in-memory personalization
  across tool loops, draft/review and final streaming. Do not persist raw profile/note
  snapshots. Queued Agent work uses settings current at execution start; missing explicit
  note selections produce an error. Existing restart recovery fails interrupted runs.
- Under the owner mutex, read a consistent DB snapshot and register active references
  before releasing it. Deletion/clearing uses the same mutex for check-and-commit, returning
  409 when running work references the data. Release references only on worker exit.
  Queued work holds its conversation reservation but no personalization references.
  Edits affect subsequent execution starts; source deletion preserves notes and does not
  revoke unrelated executions. Show wait/retry or cancellation where supported.
- Define mode-specific `num_ctx`, output reserve and input cap. Keep Ask's configured
  3,000-token input ceiling scoped to Ask; retain Agent's default 16,384 context and up-to-
  2,048 output reserve. Share estimation/accounting, with separate configuration policies.
  Budget complete requests, tool schemas/results and images; preserve required inputs.
- Replace Ask's lossy tool-loop reload with structured execution-local messages. Retain
  full assistant calls and matching results as atomic groups through subsequent calls and
  final streaming; budget each whole group. Persist final renderable history and labelled
  historical evidence, without requiring durable tool replay. Exclude legacy orphaned
  tool rows from protocol messages on future turns. Keep Agent's existing structured path.
- Fix reset to clear summary/count and retained agent context as specified by the study;
  reset/delete cannot recreate data through late worker completion.
- Add conversation detail/settings endpoint including persisted custom prompt; remove
  the shared browser prompt as the source of truth. Support explicit prompt clearing.
- Add PATCH to normal/Docker CORS configuration and cover preflight.

Exit gate:

- Captured payloads verify consistent personalization within each execution. Queued work
  sees edits/deletions made before start; running work retains its frozen version. No raw
  snapshot remains in the database on queue, completion, failure or restart.
- Deterministic race tests pause between context read and reference registration while
  another conversation attempts deletion. Exactly one outcome is possible: deletion wins
  and new context excludes the data, or execution wins and deletion returns 409. Test
  rollback/failed resolution, clearing through PUT, and project/note deletion too.
- Complete request accounting covers long summaries/prompts, small `num_ctx`, images,
  tool growth and Agent evidence. Mandatory overflow returns a useful error before call.
- A previously valid representative 24,000-character Agent attachment request remains
  valid at Agent defaults; Ask settings do not cap Agent input at 3,000 tokens. Exercise
  smaller per-conversation context overrides and required-input overflow independently.
- Multi-tool rounds preserve call/result pairing, including failure results and final
  streaming. Budget pressure cannot leave orphan tool results or re-execute a tool.
  Legacy saved tool rows are handled as historical evidence, not active protocol calls.
- Summary generation does not ingest the global/profile prompt as conversation content.
- Reset after summarization and Agent runs leaves no reusable summary/files/evidence;
  switching chats restores only that conversation's actual custom prompt.
- Streaming completion and errors preserve existing SSE response handling. No extra
  model call is introduced for onboarding or note selection.

## Increment 2: onboarding and editable preferences (first user-facing release)

Deliverables:

- Add preferences migration/entity, constrained DTOs, versioned GET/PUT/DELETE service,
  and bootstrap response. Choose explicit length limits for each field and total text;
  structured choices should remain compact. Full replacement and clearing semantics
  must match the study and not depend on truthy frontend checks.
- Add optional onboarding with Skip, editable settings, rendered instruction preview,
  and plain-language notice about sending included context to the chosen model.
- Persist completion/skip; leave existing conversation content unchanged. Profile save
  affects the next execution start in existing/new/queued chats unless their preference
  flag is off; explain that running work keeps the version it started with.
- Add `use_preferences` setting (default true; an empty/skipped profile adds no content).
  Surface its state in chat and apply it equally in Ask and Agent.
- Deliver Context used: named SSE event/additive non-stream field/Agent metadata plus
  a compact UI showing included fields, versions and omission reasons. Do not advertise
  a preview or current profile version as an exact historical prompt snapshot.
- Restrict raw-profile/assembled-prompt logging, review tool-argument logging, and bind
  release application/Ollama ports to loopback by default with documented overrides.

Primary frontend work: `App`, new onboarding/settings/context-used components,
`ChatBox`, `services/api`, and appropriate styles. No profile copy in browser storage.

Exit gate:

- Complete, skip, reload, edit, clear, conflict and API failure flows work; failed loads
  never overwrite saved settings. No onboarding wall prevents ordinary chat.
- Preferences are applied to normal/streaming/Agent requests; disabled means absent
  from newly assembled requests. A one-off user request changes no persisted preference.
- Real-model scenarios check brevity, expanded explanation and step-by-step behavior,
  plus a request overriding the stored response style. Report model-dependent results.
- Context used matches the assembled payload; no new external service request occurs
  for saving/rendering preferences.
- Local deployment/disclosure checks pass. Document that old replies, model providers
  and backups may retain previously supplied information.

Release this slice only after its gates pass; project-memory work need not delay it.

## Increment 3: projects and scoped instructions

Deliverables:

- Add owner-scoped project CRUD, versioned project text, conversation project relation
  and `use_project_context` (instructions plus saved notes). Validate owner scope in DB
  constraints as well as services.
- Add project navigation/filtering in `App`/`ConversationSidebar`, create/edit project
  UI, project chat creation and explicit assignment/reassignment for existing chats.
- New chat inherits the currently selected project; standalone creation is explicit.
  Resolve stale browser active IDs without creating a phantom conversation/project.
- Include bounded project instructions via the existing shared assembler; honor chat
  opt-out and current request/conversation-specific overrides.
- Delete project atomically clears its notes, detaches chats and removes the project;
  preview impact and reject active work. Never delete transcripts through this action.

Exit gate:

- Two projects plus standalone chats remain isolated; refreshing/resuming retains
  membership and flags; invalid/foreign project IDs cannot change a conversation.
- Instructions appear only for the assigned enabled project in all modes.
- Delete/reassign/active-run/race cases satisfy the study's cleanup semantics. Reassignment
  preserves old-project notes and detaches their source links instead of deleting/copying
  those notes; this behavior becomes testable when increment 4 adds notes.
- Existing standalone clients still send, stream and resume successfully.

## Increment 4: explicit cross-chat project notes

Deliverables:

- Add independent project-note records and an optional `project_memory_source` relation
  with complete source provenance. Source deletion removes provenance only; project
  deletion removes its owned notes. Implement
  owner/project validation, versioned CRUD, pinning and bounded lexical selection.
- Return stable persisted message IDs to the browser. Add Save for project for completed
  messages, with an editable confirmation form and kind/title/content. Refresh persisted
  messages after completion or return stable IDs in completion metadata; do not guess IDs
  from UI array indexes or identical message text. Partial output is not saveable.
- Add project-memory panel: manual create, browse/search, edit, delete, pin and select
  for the next turn. Selection sends IDs only and clears after successful acceptance.
- Include at most five notes within the shared budget. Selected notes precede pins,
  then positive lexical matches; no unrelated-note fallback. Surface omissions.
- Integrate provenance cleanup on source reset/delete and conversation move. Display
  source unavailable while preserving edited/saved note content in its original project.
  Reset/delete UI explains that saved notes remain and links to their management panel.
  Project deletion still removes project-owned notes after impact preview/reference checks.
  No background extraction, summary-to-note conversion or auto-profile
  updates. Agent citations/evidence validation must not trust a saved note as a web source.

Exit gate:

- Save a decision in chat A; chat B in the same project receives it through selected,
  pinned or lexical retrieval. Another project/standalone chat does not.
- Vague-query manual selection works. Source IDs, wrong projects, duplicates, oversized
  selections and version conflicts behave predictably.
- Editing affects the next execution start. Reset/delete of a source chat removes its
  provenance links but preserves manual and message-origin notes, including edited notes.
  Moving a source chat leaves its notes in the original project and exposes no source
  history across projects. Explicit note deletion removes it from future context; project
  deletion removes its notes. Old replies remain historical content, not silently scrubbed.
- Malicious instructions inside saved notes do not grant tools, bypass ownership or turn
  unsupported URLs into verified Agent citations. Code-enforced boundaries remain tested.

## Verification and release

Use focused tests per increment, then the repository's required checks for the release:

- Backend `mvn clean verify` under Java 25, including the existing coverage/static checks.
- Frontend Vitest with coverage and production build using the repository's Node version.
- Browser-level onboarding, settings, project, note, SSE and Agent acceptance scenarios,
  including keyboard access and failed/stale requests.
- Container fresh-install and backed-up legacy-upgrade smoke tests; reboot/restart
  persistence, migration failure and restore, local model operation, and network-exposure
  defaults. Keep real user data out of fixtures and logs.
- Real-model evaluation on the default local chat model and a representative larger
  installed model when available; record prompts/expected behavior with synthetic content.
  Captured payload tests establish inclusion, not semantic correctness of answers.
- Confirm no extra inference calls for onboarding/rendering/lexical retrieval; measure
  actual latency/prompt-size overhead without imposing an invented performance claim.

Update README, API contracts, environment configuration, release notes and backup/rollback
instructions around delivered behavior. Test rollback by restoring a full backup; do not
promise an in-place schema downgrade. Existing release-package and Agent follow-up checks
remain applicable after the changes.

## Implementation readiness

The revised study resolves ownership, execution-start snapshots, worker-safe reservations,
atomic context/deletion coordination, transactional Agent creation, separate mode budgets,
structured tool exchanges and independent saved-note lifetimes. Start at increment 0.
Its migration framework/full-schema proof is the first go/no-go gate; later slices have
separate application/model gates. The SQL probe cannot establish concurrency or model behavior.
Do not describe these planned capabilities as implemented until those gates pass.

## Architecture-review remediation map

| Finding | Remediation | Validation gate |
| --- | --- | --- |
| Transport timeout releases a live writer | Keep token/reservation through worker-finally; separate transport and execution status | Increment 0 slow-model timeout/disconnect and stale-write tests |
| Context read races with deletion | One short owner mutex covers snapshot read/reference registration and mutation check/commit | Increment 1 deterministic cross-conversation race tests |
| New Agent foreign key breaks first-turn ordering | Create conversation, user turn and run transactionally; dispatch after commit | Increment 0 fresh Agent turn, rollback and executor-rejection tests |
| Shared cap reduces Agent input capacity | Shared accounting with separate Ask/Agent limits | Increment 1 representative large-file and small-context regression cases |
| Tool structure is lost before budgeting | Execution-local full call/result groups, with explicit legacy-history handling | Increment 1 multi-call, failure, overflow and final-stream payload tests |
| Source deletion destroys saved project decisions | Independent notes plus optional provenance relation | Updated SQL probe and increment 4 reset/delete/move/edit tests |
| Durable personalization snapshots add lifecycle complexity | Resolve at execution start and retain text only in memory | Increment 1 queued-edit/deletion and restart tests |
