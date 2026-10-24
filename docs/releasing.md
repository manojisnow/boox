# Releasing Ullavox

Release work belongs on a branch and goes through a PR. Do not edit the default
branch directly. Validate the packaged app and record acceptance evidence. A release
tag triggers public image publication, so push it only after maintainer release
approval.

## Prepare a candidate

1. Update the Ullavox image tag in `docker-compose.release.yml` to the target
   version. Keep README's generic install pointed at the latest published release
   asset, and put an exact tag URL in the curated notes at
   `docs/releases/vX.Y.Z.md`. Keep the UI version marker unchanged unless a
   separate change is requested.
2. Run source quality gates and build the production Dockerfile. Validate
   `docker compose -f docker-compose.release.yml config`. For a version not yet
   published, use a separate Compose override selecting the local candidate
   image with `pull_policy: never`; do not push a candidate to the final image
   tag to make the test work.
3. Start the release Compose stack under an isolated project name and unused
   localhost ports. Verify the Ollama health check, one-time default-model
   download, app health and bundled frontend. Never select production volumes
   or stop unrelated containers for this test.
4. Test a fresh database and a SQLite backup of an existing database. Use
   SQLite's backup API for a live source, retain its original rows, and compare
   them after startup and restart. Copies contain private data and stay outside
   the repository. Validate ordinary chat, file analysis and follow-ups, web
   research, and persistence with the shipped model.
5. Run `python3 scripts/verify-release-package.py --base http://localhost:PORT`
   against each packaged app. The default model is `granite4:3b`; a different
   model requires `--model`. The script creates test conversations and stores
   raw responses under `/tmp`. Failures are release-readiness findings, not
   reasons to weaken checks. Run local-model checks sequentially when apps
   share one Ollama server. Validate streaming chat, cancellation and UI
   behavior manually as well.
6. Record the candidate commit, image ID, platform, test outcomes and remaining
   limitations. Local ARM64 checks do not prove AMD64 behavior. Include manual
   packaged-app acceptance in the validation record. Open a reviewable PR and
   verify CI on the final head and merged commit, including Secret scanning,
   before publication.

## Publish and verify

After approval, create `vX.Y.Z` at the reviewed merge commit and push that exact
tag. `.github/workflows/release.yml` runs backend quality gates, frontend
coverage/build and Dockerfile lint, then publishes AMD64 and ARM64 images to
Docker Hub and creates a GitHub release with the Compose asset and curated
notes. It requires the configured Docker Hub credentials. Existing runtime
package versions do not determine the release version; the Git tag does.

Watch the Release workflow until it succeeds. Confirm the `X.Y.Z` manifest
contains both platforms, the downloaded Compose asset selects `X.Y.Z`, and the
GitHub release includes the limits and backup instructions. Pull the published
image and perform a final isolated smoke test. A successful source build alone
is not confirmation that publication succeeded. Do not move an existing release
tag or silently overwrite a published version to repair a failed release.

Keep the prior image and database backup until the upgrade is accepted. Follow
the target release notes for rollback and preserve Docker's Compose project
name so the expected named volumes are selected.
