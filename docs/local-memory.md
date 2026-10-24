# Preferences and project memory

Ullavox keeps preferences, projects and explicitly saved project notes in its local SQLite
database. No account or external memory provider is needed. One internal local identity
owns the data; this identity does not provide authentication for a shared server.

## Preferences

Optional onboarding asks how you want Ullavox to respond, your familiarity with a topic,
and any background, goals or working instructions you choose to share. Skip keeps chat
available and discards unsaved onboarding edits while preserving previously saved
preferences. Open Preferences later to edit, preview the generated instructions or clear
the saved answers.

Each chat can enable or disable preferences. Preferences are defaults: a request such as
"explain this in detail" can override your usual preference for short answers. Chatting
does not automatically change your saved preferences.

Ullavox reads the enabled settings when execution starts. A queued Agent task uses the
settings current when it starts; a task already running keeps its resolved version.
Changes to settings affect subsequent executions. Clearing settings currently used by
running work can return a conflict; wait for completion or cancel an Agent task and retry.

## Projects and notes

Create a project to group chats around a goal and supply project instructions. A chat
outside a project remains standalone. Project context can be disabled for an individual
chat. Moving a chat carries its transcript to the selected project; it does not copy
notes from the old project.

Project notes are explicit: write one in the notes panel or save a completed, persisted
message after reviewing its text. Choose a note, decision or constraint, edit it, and
optionally pin it. Partial streaming output cannot be saved. Ullavox does not automatically
extract personal traits or promote conversation summaries into project notes.

For an enabled project, selection considers notes explicitly selected for that turn,
then pinned notes, then lexical matches against the latest request. At most five notes
fit within the available memory budget. Unrelated unpinned notes are not automatically
included. Browse and explicitly select notes for vague requests such as "what did we
decide?" Selection is bounded text matching, not semantic search over all past chats.

Saved notes belong to their project independently of their source messages. Deleting or
resetting a source chat preserves note content and removes its source link. Moving a
source chat leaves its saved notes in the old project with the source unavailable.
Delete a note explicitly to remove it from future retrieval. Deleting a project removes
its notes and detaches its chats; it does not delete the chat transcripts.

## Context and execution

Context used reports included and omitted preferences/project notes and their versions.
The backend selects and assembles this information; it is not copied from browser storage
into every outgoing request. Profiles, project context and conversation instructions are
separate. Application tool/evidence restrictions remain enforced by Ullavox.

Ask and Agent share context accounting but retain different capacities. Ask's configured
input ceiling remains separate from Agent's larger default context. Required input that
cannot fit is rejected rather than silently truncated. Tool calls/results are kept as
complete groups through each execution.

A browser disconnect or stream timeout does not establish that the model stopped.
Execution status keeps conflicting sends and destructive chat actions blocked until the
worker finishes. Completed replies can be persisted even after the browser disconnects.
Agent cancellation takes effect at a checkpoint after an in-flight call returns.

Resetting a chat removes its messages, running summary, saved conversation instruction,
Agent history/files/evidence and context-use history, while retaining its project,
generation settings and inclusion flags. Saved project notes and global preferences
remain. Disable project context or explicitly delete notes if they should no longer be
supplied. Old replies can contain previously supplied information; changing or deleting
saved context does not erase transcript references or retract data already sent to a model.

## Data handling

Local persistence does not make a cloud model local. Included preferences, project
instructions and notes go to the selected model, which may use remote inference. Web
tools can also receive model-generated queries based on that context. Files-only disables
web tools, not cloud model processing. Use downloaded local models and disable web tools
for local operation. No new inference call is required to render onboarding answers or
select notes.

The SQLite database is not newly encrypted by this feature. Protect the host, disk and
backups. Compose binds application and Ollama ports to `127.0.0.1` by default. Setting
`ULLAVOX_BIND_ADDRESS=0.0.0.0` deliberately exposes them to other machines; the APIs have no
login protection. Operate one Ullavox application process per database.

## Upgrade and rollback

Before upgrading an installation, stop its app and back up the complete data directory,
keeping any database WAL/SHM files together as a set. Keep the previous application image
and Compose configuration. See the [existing backup instructions](releases/v1.5.0.md).

Versioned schema migration adopts supported legacy databases, creates a stable random
local owner and preserves existing conversation IDs and history. Existing chats start
without a project and with empty global preferences. Unknown schemas or invalid ownership
relations must fail migration rather than silently discard data. Schema validation replaces
uncontrolled production schema updates.

Rollback uses the complete pre-upgrade backup with the previous application image.
Do not assume an older binary can use the migrated database. Backups and older replies
can retain information removed from the current settings or project notes.
