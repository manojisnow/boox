# Ullavox UX direction

Design review · 18 October 2026 · Proposal, not an implemented interface

## Product premise

Ullavox is a calm place to think, with context you control. It combines local model access, ongoing conversations, explicit project knowledge, and bounded research. The interface should make that combination feel like one product.

The primary loop is: open a conversation, understand its context, write, read, continue. Organizing knowledge and tuning the model support that loop.

## Review of the current app

Reviewed the running local frontend at localhost:3000, including the initial screen, navigation drawer, and an existing conversation. Also reviewed App.jsx, ChatBox.jsx, ConversationSidebar.jsx, Personalization.jsx, AgentRunPanel.jsx, their styling, and the documented feature limits. The browser was a narrow desktop panel; observations about wider layouts also draw on the CSS. This was a heuristic review, not a usability study, accessibility audit, or measured performance assessment. No research task or model generation was executed.

| Finding | Consequence | Proposed change |
| --- | --- | --- |
| Server, model, project, two context checkboxes, and Save chat settings occupy the top of the conversation. | Configuration receives attention before the user's task. | Compact header with model and Context entry points. Project appears as a breadcrumb. |
| Project selection filters navigation and separately assigns the current chat. | Two similar controls have different effects. | Projects become visible navigation destinations. The chat's project is clear in its header; moving an existing chat is an explicit menu action. |
| New chat appears both as a floating button and in the sidebar. | Competing entry points and detached visual anchors. | One New chat action in the desktop sidebar; one in mobile navigation. |
| Ask/Agent, system prompt, generation settings, attachments, send, and streaming occupy the same input row. | Small targets and icons compete with writing space. | Text on its own row. A short secondary row contains attachment, mode, scope where relevant, and send. |
| The initial screen asks the user to select a server and model. | Infrastructure setup becomes the product's greeting. | Restore a valid previous model or choose a configured default. When none is usable, show a clear Connect a model setup state. |
| Preferences open automatically when onboarding has not started. | A profile form interrupts the first conversation. | Allow immediate conversation; offer optional personalization without taking over the screen. |
| Context setup, saved project notes, and Context used are separate surfaces. | Users must assemble their own picture of what influences an answer. | One context inspector, with a clear distinction between next-message settings and the record of a past response. |
| Agent help adds several paragraphs immediately above the message field. | Switching mode expands a technical form. | Show a short Research description and an explicit scope selector; put detailed limits in attachment and scope help. |
| Purple accents, bubbles, shadows, rounded floating controls, and browser-like form fields coexist. | Visual emphasis lacks a consistent hierarchy. | Neutral surfaces, one restrained accent, consistent controls, typography and spacing. |
| Below 900px the drawer overlays the conversation. | Navigation obscures the task without the model of a distinct mobile navigation layer. | Deliberate drawer with scrim, close control, focus containment, Escape handling, and focus restoration. |

## Information architecture

- **Chats:** recent conversations and a single New chat action.
- **Projects:** named groups containing their chats, instructions, and saved notes. New chats inherit the project from which they are created.
- **Settings:** appearance, response preferences, model connections, and defaults. No routine Refresh projects action; refresh data after changes and provide a retry on failure.
- **Conversation header:** title/project breadcrumb, current model, Context, and a menu for chat actions.
- **Context inspector:** project assignment, preferences, project instructions, selected/pinned notes, and a separately labeled Advanced section for chat instructions and generation overrides.

Do not add a dashboard, activity feed, or separate agent destination. Research is work within a conversation. Do not imply a file library: the current app has retained text attachments for agent follow-ups and explicitly saved project notes.

## Two layout directions

**Quiet conversation — recommended default.** A restrained navigation rail, compact header, readable centered transcript, and generous composer. Context opens on demand. This keeps casual chat and everyday project work approachable.

**Project workspace — optional expanded state.** The same conversation with a context inspector alongside it on sufficiently wide screens. Useful while curating notes, inspecting research evidence, or checking project instructions. At narrower widths the inspector becomes a drawer or an inline panel; do not squeeze all three columns together.

These are layout alternatives for review. The preferred product would support the quiet layout and let the user open its inspector, rather than require choosing a permanent product mode.

## Interaction rules

### Start and resume

Open the last conversation when appropriate; New chat presents a short invitation and the composer. Restore the last valid model without forcing a server choice on every visit. The model picker groups installed models by connection and clearly marks local versus remote processing. If the model is unavailable, show a nearby recovery action and retain the draft.

A new project chat inherits its project. Standalone conversations remain possible. Moving a chat changes future context only; past response records retain what was actually supplied.

### Composer

Keep the message field dominant, with a generous minimum height. Put attachments and mode below it. Use **Chat** and **Research** as user-facing labels; retain the existing Ask/Agent semantics underneath. Research includes file analysis as well as web work, with a short explanation on first use.

Research scope stays visible before sending: **Files only**, **Web**, or **Files + web**. Attaching a text file defaults to Files only, matching current behavior. Enabling web is explicit. Do not silently escalate from Chat to Research, or enable web based on inferred intent.

One Attach menu can expose supported text files and images, with capability information and clear unavailable states. Text files require Research; explain this before switching. Images require a vision-capable model. Do not suggest support for PDFs, Office documents, editing, or code execution. Show limits in the attachment surface: four images or four text files, with text capped at 24,000 combined characters.

Stream normal chat by default; move its technical toggle to Advanced. During generation replace Send with Stop where cancellation is supported. Research uses Cancel and states that cancellation takes effect after the current operation returns. Preserve drafts and selected scope across connection problems.

### Context and knowledge

The Context trigger provides a compact summary and opens the inspector. Edits save automatically with visible Saving, Saved, and failure states. Do not remove explicit Save until reliable persistence, conflict handling, and send gating exist. A send must use the displayed saved configuration or clearly block with a recovery path.

The inspector lists preferences, project instructions, and saved notes. Note selection says **Use in next message**. Distinguish explicitly selected notes from pinned and automatically matched notes; do not promise every note is always included. Existing budgets and selection limits remain enforced.

Label the past-response disclosure **Context used**, attached to the corresponding response. It shows included and omitted items and reasons. The next-message inspector must not masquerade as that execution record.

Save a useful message through **Save to project notes**, then allow editing before saving. Keep notes explicit and user-controlled. They survive deletion of a source chat, with their source link removed as today. Never describe this as unlimited memory or automatically learned knowledge.

### Research and sources

While active, show one concise status row, a visible Cancel action, and expandable actual steps/evidence. On completion collapse the activity by default, retaining a sources entry point. Keep a concise disclosure in the activity detail that the draft review uses the same model; completion is not independent fact verification.

Attach sources and Context used to their actual response/run. Label retained attachments separately from project notes and provide Remove from future research. Avoid exposing raw evidence as the default transcript, while keeping it inspectable. Error states distinguish disconnection, provider failure, cancellation, and a failed run; reconnecting must not duplicate a task.

## Visual system

- Neutral light and dark surfaces with a restrained violet accent for selected navigation and primary actions.
- System sans-serif for controls and body, optionally a restrained serif welcome heading. Use one coherent type scale rather than oversized branding.
- An approximately 680–760px reading column; body text around 16px with comfortable line spacing. User messages can have a quiet fill; assistant answers sit directly on the reading surface.
- A consistent spacing scale, one icon family, subtle borders, and minimal shadows. Reserve elevation for overlays.
- Desktop sidebar around 220–240px. Avoid a permanently visible inspector unless enough reading width remains. Preserve usable target sizes, including approximately 44px touch targets.
- Respect color preference and reduced motion. Maintain visible keyboard focus and readable contrast; use labels alongside color and accessible names for icon actions.

## Essential states to design and verify

1. First use with a working model, without a model, and with an unreachable connection.
2. Empty standalone chat, new project chat, and resumed conversation.
3. Short and long answers, code blocks, wide tables, and image attachments.
4. Project notes: none, selected, omitted by budget, disabled, and deleted source.
5. Research: files only, web, mixed, retained attachments, running, cancelled, disconnected, and failed.
6. Saving settings, save failure, conflicting edits, and switching conversations while requests are pending.
7. Mobile keyboard, long chat titles, touch controls, keyboard-only use, dark appearance, and reduced motion.

## Implementation sequence

1. **Shell and composer:** navigation, header, type/spacing tokens, transcript, and reorganized composer. Preserve existing workflows and backend behavior.
2. **Context:** consolidate settings and notes; add reliable autosave before retiring Save chat settings. Preserve execution provenance.
3. **Research:** short scope controls, compact progress, evidence and source disclosure, and actionable recovery states.
4. **Onboarding and refinement:** model connection recovery, optional preferences, responsive drawers, and accessibility verification.

The review prototype uses illustrative project/note content and model labels from the current app. Its controls demonstrate local navigation and disclosure only; it does not save settings, connect models, upload files, or generate responses.

## First implementation

The quiet conversation layout is now implemented in the React frontend: neutral light/dark tokens, a desktop sidebar and compact navigation drawer, visible project destinations, a compact conversation header, a readable transcript, and a composer with separate writing and control rows. Context opens as a native modal drawer with focus containment, Escape handling, and focus restoration. Existing preferences, project notes, conversation instructions, generation overrides, and streaming controls remain accessible. Optional personalization no longer interrupts the first conversation.

Chat and Research are the visible names for the existing Ask/Agent behaviors. Research scope remains explicit, detailed help is collapsed, and attachments are available through a disclosure. Backend limits, model requests, and persistence behavior remain intact. Settings save explicitly or before sending; reliable automatic saving and an adjacent nonmodal workspace inspector remain future work.

The model picker always identifies its source, including when only one connection is configured. Context explains that the list comes from the configured service and that Ollama can offer both local and cloud models. Per-model execution-location badges require metadata not currently exposed by the backend.

Verification: 159 frontend tests pass with coverage above the repository thresholds. See the [UI validation report](ux-validation.md) for live chat/research checks, responsive and theme inspection, defects corrected, and remaining test limits.
