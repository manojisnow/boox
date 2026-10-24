# Local preferences and project memory: validation record

Date: 16 October 2026. Branch: `codex/local-preferences-project-memory`.

Implemented optional onboarding/preferences, projects, explicit project notes,
conversation inclusion controls and context-use reporting. The backend resolves context
for Ask, streamed Ask and Agent executions. One internal local identity owns persisted
data. No account UI, ChatSorter integration or automatic trait extraction was added.

## Automated verification

| Check | Result |
| --- | --- |
| Java 25 backend `mvn -o -B -f backend/pom.xml spotless:apply verify` | 226 tests passed; formatting, Checkstyle, SpotBugs, PMD's configured policy and JaCoCo gate passed. |
| Node 24 frontend coverage suite | 146 tests passed; all configured coverage thresholds passed. Statements 90.21%, branches 83.82%, functions 87.77%, lines 97.08%. |
| Frontend production build | Passed. |
| Packaged application startup | Flyway migration completed before Hibernate schema validation; health endpoint became ready. |
| Docker image build | Built using the repository Dockerfile. |
| Disposable Docker volume and restart | Packaged assets served; preferences and project notes survived restart. |

Backend tests cover fresh and populated legacy migration, rollback after an injected
rebuild failure, repeat startup, unknown-schema rejection, foreign keys on application
connections, stable message IDs, owner isolation, provenance, optimistic versions,
context freezing, deletion/execution coordination, Agent acceptance and prompt budgets.
Frontend tests cover onboarding, projects/notes, per-chat settings, stale asynchronous
responses, preserved generation settings, context reporting and stream recovery.

PMD reports existing-style warnings under the repository's configured non-failing policy;
passing verification does not mean that the codebase has no PMD warnings. Existing
coverage thresholds were preserved.

## Existing database upgrade

The existing local database was opened read-only and copied with SQLite's backup API
into a private temporary directory. The packaged application migrated that copy and
started successfully. The original database was not upgraded or modified.

| Table | Rows preserved |
| --- | ---: |
| Conversations | 23 |
| Messages | 105 |
| Agent runs | 38 |
| Agent steps | 244 |

Foreign-key validation passed. This exposed a supported legacy variant with nullable
Agent version values and redundant CHECK parentheses; regression tests cover that
variant. Unexpected schemas still fail before adoption.

## Real local-model acceptance

`scripts/verify-local-memory.py` ran against an isolated database and local
`qwen2.5:14b`, with web tools disabled. All five checks passed:

1. Save preferences and reject an update using a stale version.
2. Recall an explicitly saved project phrase and report the included context versions.
3. Reset source history while retaining its saved note and marking its source unavailable.
4. Keep project notes out of a standalone chat.
5. Create a fresh Agent conversation and correctly total an attached CSV with preferences enabled.

The script requires explicit consent to update preferences when pointed at an existing
server; use a disposable database for repeatable validation. These checks demonstrate
the tested model's behavior on these tasks. They do not establish universal preference
obedience or a measured improvement in answer quality.

`scripts/verify-memory-container.py` verified assets, fresh startup and durable preferences
and notes after restarting a disposable named-volume container. It only accepts container
names beginning with `ullavox-memory-validation-` to reduce accidental use against a real
installation.

## Remaining acceptance and operating limits

The initial browser check was blocked by a locked Mac. On 18 October 2026, an isolated
test instance was opened for a user-led walkthrough of onboarding/preferences, projects,
saved notes, cross-chat recall and inclusion controls. The user reported that everything
worked. This records user-reported acceptance of the flows exercised, rather than an
independently observed exhaustive browser regression pass.

During follow-up testing on 18 October, Skip onboarding exposed a stale-version conflict:
an older onboarding panel could remain open after preferences were saved elsewhere.
Skip now reloads the saved state, preserves existing preferences and closes without a
write if onboarding is already complete. Its regression tests cover stale versions,
discarding only draft edits, and load failures. All 148 frontend tests and the production
build passed. A browser retry closed the stale panel, and the saved preference version
remained unchanged.

The application remains a single-process local installation with no login protection.
Internal ownership prepares the schema for future accounts; it does not authenticate
requests. SQLite encryption, multi-process coordination, account migration and automatic
memory extraction are outside this implementation. Local storage also does not prevent
enabled context from being sent to a selected cloud model or used in web-tool queries.
See [the user guide](local-memory.md) for controls, deletion and backup/rollback behavior.
