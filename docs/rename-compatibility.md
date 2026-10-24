# Upgrading an existing installation to Ullavox

The source tree uses the Ullavox name throughout. Existing installations require
an explicit configuration migration: earlier environment-variable names, storage
keys and default filenames are no longer supported automatically. The rename
itself does not rewrite database tables, messages, conversation IDs or user content.

## Before starting

Record the existing image reference, Compose project name, mounted data/model
volume names and database filename. Stop the app before copying the complete data
directory, including matching SQLite WAL and SHM files. For a live source, use
SQLite's backup API instead. Keep backups outside Git and retain the old image and
Compose configuration for rollback.

## Database and volume migration

The new defaults are `./data/ullavox.db` locally and `/app/data/ullavox.db` in Docker.
Set `ULLAVOX_DB_PATH` to the actual existing filename to keep using it. Do not
simply start with a new default path: that selects a separate empty database.

Pass the setting into the container using a private Compose override. Map the new
volume keys to the actual existing Docker volume names:

```yaml
services:
  app:
    environment:
      ULLAVOX_DB_PATH: /app/data/EXISTING_DATABASE_FILENAME
volumes:
  ullavox_data:
    external: true
    name: EXISTING_APP_VOLUME_NAME
  ollama_data:
    external: true
    name: EXISTING_MODEL_VOLUME_NAME
```

Replace all three uppercase placeholders with values recorded from the existing
installation, then start with both files:

```bash
docker compose -f docker-compose.release.yml -f compose.local.yml up -d
```

For a source build, use `docker-compose.yml` instead and add `--build`. Keep the
same project name with `-p EXISTING_PROJECT_NAME` where appropriate. Volume mapping
is explicit so a renamed checkout cannot silently select different data. Never
use `down -v` for a normal upgrade. Confirm the effective configuration with
`docker compose ... config` before starting.

For a non-container installation, export `ULLAVOX_DB_PATH` with the existing
absolute file path before starting the backend. No database-file rename is needed.

The container account is now `ullavox`, with explicit UID/GID `999:999` matching
the previous image. Verify permissions when using custom bind mounts; do not
change ownership of unrelated directories.

## Other configuration

- Rename any earlier bind-address variable to `ULLAVOX_BIND_ADDRESS`. The default
  remains `127.0.0.1`. `0.0.0.0` exposes unauthenticated services to other machines.
- Verification scripts use `ULLAVOX_TEST_URL` and `ULLAVOX_TEST_MODEL`.
- A variable in Compose's `.env` is not automatically passed to a container;
  database overrides must appear in the service's `environment` configuration.
- Browser selection is stored under `ullavox-active-conversation`. Reopen an
  existing chat from history after upgrading; earlier browser selection keys are
  not imported. Stored backend history remains available when the same database
  is mounted.

Verify chats, preferences, projects and saved notes after startup and restart.
User-written content is preserved, including any earlier product names.

## Rollback

Stop the new app and preserve its data separately. Restore the consistent
pre-upgrade backup, matching files, original Compose configuration and image.
Reuse the original project/volume names and writable numeric ownership. Follow
schema compatibility guidance for the actual versions involved; do not assume an
older binary can read data upgraded by a later feature release.

## Distribution status

Repository URLs and registry destinations in this checkout describe the intended
Ullavox distribution. They become usable after the repository/registry cutover and
first renamed release. Until then, build this checkout locally. Archived release
artifacts remain historical snapshots; changing source documentation does not
republish old images or modify existing Git tags.
