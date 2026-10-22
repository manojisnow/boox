# Credential scanning

The Secret scanning workflow uses checksum-verified Gitleaks 8.30.1 to inspect full Git history, including merge patches. It runs for pull requests and pushes to main. Secret values are fully redacted; inline `gitleaks:allow` comments cannot disable detection.

Local `.env` and `.env.*` files are ignored by Git; safe `.env.example`, `.env.sample` and `.env.template` files can be committed only with placeholder values. Docker build exclusions also cover environment files, databases and private `.local` launch assets. Neither ignore file removes a file already tracked by Git: review staged changes before committing.

An audit with the updated scanner found one historical Docker credential in generated test logs. The owner confirmed on 20 October 2026 that it was retired/recycled. `.gitleaksignore` lists only the eight exact historical finding fingerprints. It contains no secret values and does not exempt a path, token pattern or future commit. The credential remains in old Git history; retirement, rather than this exception, prevents its use. History has not been rewritten.

To scan locally with the pinned version:

```sh
gitleaks git . --log-opts="--all --full-history -m" --ignore-gitleaks-allow --redact=100 --no-banner --exit-code=1
```

New findings must be investigated and exposed credentials retired before any exception is added. Never put token values in issues, PRs, reports or screenshots.
