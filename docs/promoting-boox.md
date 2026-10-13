# Boox post-release launch and promotion runbook

Use this after a Boox release is published and tested. The original article lives
on manojkumar.eu; Medium references that article canonically. The launch also
updates the portfolio, apps listing and GitHub profile, then announces the release
on LinkedIn. Additional channels are optional.

## 0. Establish the release and working state

For this launch:

- App/version: **Boox v1.5.0**, published October 12, 2026.
- Repository: https://github.com/manojisnow/boox
- Release: https://github.com/manojisnow/boox/releases/tag/v1.5.0
- Released commit: `aff4d1986802ea1a874e16a461df26ff04a03f4e`.
- Image: `dumanoj/boox:1.5.0`; ARM64 and AMD64 images were published.
- Release notes and limitations: [v1.5.0](releases/v1.5.0.md).
- Release workflow: https://github.com/manojisnow/boox/actions/runs/38263127869
- Default model: `granite4:3b`; Ollama and model tags remain mutable.
- Intended audience: developers and people who want to run AI chat, text-file
  analysis and bounded web research on their own machine.
- Distribution: a local Docker installation, not a publicly hosted app.

Verify these details against the release before writing. Record the exact image
digest, architecture, model, provider, demo configuration and test results in the
local launch checklist. Do not silently substitute an unreleased branch or a
cloud model for the shipped default.

The source checks passed 190 backend and 116 frontend tests. Candidate package
checks covered fresh and copied databases, with original copied rows preserved.
The published ARM64 image passed chat, file totals, follow-ups and web research
on retry. Its first web request timed out: Ollama returned after 4 minutes 50
seconds, beyond Boox's three-minute model-request timeout. Keep that first
failure in the evidence record; a successful retry is not a reliability guarantee.
AMD64 publication was verified, but local inference testing was on ARM64.

### Repository and approval rules

These rules apply to **every repository**, regardless of branch protection:

1. Inspect repository instructions, Git status and deployment configuration.
   Preserve unrelated changes and never edit the default branch.
2. Create a feature branch from the appropriate current base. Use separate
   branches and PRs in Boox, portfolio-site and manojisnow as needed.
3. Build and validate the changes. Run the relevant app/site locally and ask
   Manoj to validate it **before any PR**. For documentation-only changes,
   review the rendered document and provide the running app as required; do
   not claim the document changes application behavior.
4. After manual validation, commit intended files, push the branch and raise a
   PR. Attach each created PR to the working chat. Wait for review and merge.
5. A request to prepare content does not authorize posting it. Obtain explicit
   publication authorization for social posts and Medium publication. Confirm
   deployment approval before a merge that triggers a public website update.

Keep the reusable runbook in this repository. Keep launch status, draft social
copy, raw captures and production files under ignored local directories:

- `.local/launch-v1.5.0/` — checklist, drafts, approvals and final URLs.
- `.local/demo-v1.5.0/` — captures, edits, captions, poster and video exports.

Start with the checklist in `.local/launch-v1.5.0/LAUNCH-CHECKLIST.md`. Preserve
previous versions, and update it after each meaningful step so another chat can
resume without reconstructing the launch. Only selected public media assets
belong in the portfolio repository.

## 1. Define the story and verify the claims

Suggested angle: **Boox 1.5: from a local AI chatbot to file analysis and web
research**. Explain the useful workflow and the engineering choices through
actual examples. Avoid inventing personal experiences or treating release notes
as the finished article.

Check every claim against the released app:

- Ordinary chat and agent mode are distinct. Users ask naturally; they do not
  need to name tools or add “do not search the web” to a Files-only task.
- Text-file analysis accepts up to four files and 24,000 combined characters.
  Supported inputs include text, Markdown, CSV, JSON and code. PDF and Office
  documents, file editing and code execution are unsupported. Image input is
  separate and requires a vision-capable model.
- Follow-ups retain files and web scope; clearing retained files removes them
  from subsequent tasks, but does not erase stored conversation history.
- Agent memory is bounded: 8,000 characters each for history and prior evidence.
  Web tasks have five search/page-reading calls, with 4,000-character results.
- Accuracy and tool use depend on the model. Review uses the same model;
  available source links do not prove every claim. Slow local inference can
  exceed the model-request timeout.
- Text attachments and history are stored in local SQLite. Cloud model choices
  send task input, including attached content, to that provider. Files-only
  disables web tools; it does not make a cloud model local.
- Web research sends queries and requested URLs to external providers. Parallel
  MCP is optional; legacy search is the default. Parallel receives a random
  per-conversation identifier and the active model name. Model-generated queries
  can contain task information. Provider access and costs are separate.
- APIs are unauthenticated and release Compose ports bind to all interfaces.
  Describe a trusted, single-user deployment with restricted network access.

Use “local models supported; cloud models optional.” Avoid blanket claims such
as “fully private,” “zero cost,” “reads any attachment,” “unlimited memory,”
“always accurate” or “safe to expose publicly.” Do not imply that MCP itself
makes research accurate or private.

## 2. Prepare and review the demonstration

Agree on the script and presentation before recording. Aim for a short,
caption-led walkthrough; choose length and aspect ratio for Boox instead of
copying ResumeForge's video settings automatically.

Run the published image in an isolated Compose project, with unused localhost
ports and a fresh demo database. Do not use personal conversations, production
volumes or unrelated services. Keep model downloads within this MacBook's
capacity; do not download or run 7B models for this demo.

Show these steps with fictional data:

1. Ordinary chat with the selected model visible.
2. Agent mode, Files-only, with `totals.csv`: amounts 5, 7 and 9.
3. Ask for the total, then ask why it is correct; verify 21 and the explanation.
4. Clear retained files, enable Web, and ask a natural-language research question
   with sources. Verify actual tool activity and working evidence links.
5. Show the collapsible activity panel and the final answer.

If demonstrating Parallel, label the optional provider configuration explicitly;
do not present it as the default. Keep keys out of capture. Do not present edits
or removed waiting time as real-time performance. If an answer or tool call
fails, keep the failure in the private test record and investigate before recording.

Capture at sufficient native resolution; do not enlarge small screenshots or
add slow zoom effects that blur UI text. Hide personal data, credentials and
unrelated desktop content. Use only music with publication rights. Export an
MP4, poster and caption file. Review the encoded video at full size, including
text, transitions, audio, duration and playback. Obtain Manoj's approval before
using it publicly.

## 3. Write the original article

Publish first on **https://manojkumar.eu**. Choose a stable slug and record it
in the checklist; do not treat a proposed URL as live.

Include the useful task, what changed in 1.5.0, the approved demo, installation
and the practical limits above. Explain what remains local and what leaves the
machine. A concise limitation paragraph plus release-note links is preferable
to repeating every configuration detail.

Embed the approved video with controls, poster and captions; avoid autoplay.
Link to the repository, exact release and setup instructions. Explain first-start
model downloading and the local nature of installation. Link backup/rollback
instructions for existing users. Keep the wording factual and personal only
where Manoj has supplied the experience. Read it aloud and remove repetition,
stock openings, slogans and unsupported comparisons.

## 4. Update portfolio-site and the apps listing

Repository: `/Users/manoj/Documents/GitHub/hobby-apps/portfolio-site`.
Inspect its existing conventions and current deployment settings before editing.

- Add the article under `portfolio/blog/posts/` and selected public media under
  its assets directory, ensuring the build copies them.
- Add it to the homepage writing cards and blog index, newest first.
- Update the Boox project description to mention file analysis, follow-ups and
  optional web research. Remove stale blanket “no cloud” or cost claims.
- Provide clear links between the project entry, article, repository and release.
  Do not misleadingly replace a repository-labelled link with an article URL.
- Set the article's self-canonical URL; update RSS, sitemap, description and
  social-preview metadata. Include a useful preview image and accessible text.
- Update `apps/index.html` with **Run locally**, linked to released download/setup
  instructions. Do not add **Open app** unless a public deployment exists and
  has been tested. Localhost is never a public launch destination.

Run `npm run build` and inspect built output. Serve it locally and obtain manual
validation before the PR. Check desktop and narrow mobile layouts, links, fixed
navigation anchors, canonical metadata, video, captions, RSS and sitemap.

Stage intended source and public assets only. Keep `.local` production files,
private test results and secrets out of commits. Confirm the Netlify deployment
branches for both portfolio and apps; merge only after the required approval.
After deployment verify the actual public article, cards, apps entry and every
media URL. A green build or Git push is not proof of a completed deployment.

## 5. Import the public article into Medium

The original must be publicly accessible before importing; localhost cannot be
used. Check Medium's official instructions at execution time:

- https://help.medium.com/hc/en-us/articles/214550207-Importing-a-post-to-Medium
- https://help.medium.com/hc/en-us/articles/360033930293-Set-a-canonical-link

On desktop, use **Stories → Import a story** with the exact public article URL.
Review text, spacing, code and links. If the video does not import, add a clear
link to watch it in the original. In **More settings → Advanced Settings**, verify
the original-source/canonical URL points to the portfolio article. Set it manually
if needed. Obtain draft approval and explicit authorization before publishing.

After publication inspect the page source for the canonical URL, check links,
and record the Medium URL. The portfolio remains the original; do not point its
canonical tag back to Medium.

## 6. Update the GitHub profile

Repository: `/Users/manoj/Documents/GitHub/hobby-apps/manojisnow`.

Update the existing Boox entries rather than duplicating them. Describe the
released local chat and agent capabilities accurately. Add the original portfolio
article to recent writing, keeping date order and the current display length.
Preserve automated sections and unrelated profile content. Use a separate branch,
review the rendered README and running relevant site/app with Manoj before the
PR, then push, raise and attach the PR. Verify the public profile after merge.

## 7. Prepare and publish LinkedIn

Use a short feed post with the approved MP4 uploaded directly. Explain what
someone can do with Boox, name v1.5.0, include the original article link and invite
specific feedback. Use a few relevant tags, such as `#OpenSource #LocalAI
#Java #Ollama`; select them intentionally rather than promising reach.

A separate LinkedIn article is optional. Review the combined video preview,
captions, text and links before requesting explicit publication authorization.
Publish only after authorization, then record the post URL and verify video
playback and the article link.

## 8. Consider additional channels

Decide individually; “wherever possible” does not require posting everywhere.
Check each channel's current rules and obtain publication authorization.

- **DEV Community:** a technical adaptation about Spring Boot, Ollama, tool
  orchestration and testing. Check canonical-link support and point it at the
  original article if republishing substantially the same content.
- **Reddit:** choose communities where the demonstrated local AI or self-hosting
  use case fits. Read their promotion rules, disclose authorship and invite
  useful feedback. Avoid mass posting or repeating the same sales copy.
- **Hacker News:** consider Show HN only if Boox is a substantial runnable project
  that meets the current guidelines, not merely a version announcement or blog
  link. Manoj must write the submission and comments himself; do not generate,
  edit or automatically post HN text. Check https://news.ycombinator.com/showhn.html
  and https://news.ycombinator.com/newsguidelines.html before proceeding.

Record skipped channels and the reason. Respond to genuine questions and track
bugs as follow-up work; do not claim likes, stars or hashtags guarantee adoption.

## 9. Close out and resume safely

Update the local checklist with final URLs, approvals, merged PRs, verification
results and outstanding work. Keep failures and retries distinct. Remove temporary
viewport overrides; stop only services created for the launch. Preserve private
working files and older exports. Do not remove production data or personal models.

The launch is complete when the agreed channels are live and verified. Pending
PRs, approved-but-unpublished drafts and missing URLs remain explicit unfinished
items. Do not publish another release tag as part of promotion.

### Handoff prompt

> Follow `docs/promoting-boox.md` for Boox v1.5.0. The release is
> https://github.com/manojisnow/boox/releases/tag/v1.5.0 at commit
> aff4d1986802ea1a874e16a461df26ff04a03f4e. First read
> `.local/launch-v1.5.0/LAUNCH-CHECKLIST.md` and inspect Git status and the released
> app plus sibling portfolio/profile repository instructions. Resume the first
> unfinished item without repeating completed publication. Use branches in every
> repository, preserve unrelated changes, run the relevant app/site and obtain
> Manoj's manual validation before any PR, then push and attach the PR. Keep
> working files local and verify deployment after approved merges. Do not publish
> Medium or social posts without explicit authorization. Report pending work and
> update the checklist before stopping.
