# CI Improvement — Design (2026-09-27)

## Context

Pipeline is merge-gated on `main`: `build-classic` + `build-clone` in parallel,
then `publish-release` (`.gitlab-ci.yml:23-189`). Pipeline `#2885217992`
failed in `publish-release` with `bash: unexpected EOF while looking for
matching '"'` because `.gitlab-ci.yml:172` contained `for ## $VERSION` —
YAML cut the line at ` #`, leaving an unbalanced quote. Fixed on this branch
by single-quoting the line (`.gitlab-ci.yml:172`). `glab ci lint` passes
before and after, so lint cannot catch this class.

Current costs per failed `main` run: two full flavor builds (~3m each) plus a
base download per flavor, and a `before_script` that installs apt packages,
the Android CLI, and SDK components on every job (~40s+ in `publish-release`,
which only needs `apksigner`/`aapt`).

## Constraints (validated)

1. CI stays `main`-only. No MR pipelines; extra runner minutes for MR
   validation were declined.
2. Adopt the custom runner image. `.gitlab/ci-image/Dockerfile` (untracked)
   bakes JDK 21 + `platforms;android-35/36`, `build-tools;36.0.0`,
   `platform-tools`. Tags are immutable (`jdk21-sdk36.0.0-vN`), never
   `:latest`.
3. Approach B: harden in place plus a `fetch-base` DAG job. No full
   validate-stage job; local checks cover pre-merge safety instead.

## Section 1 — Structure (approved)

Stages stay `build` → `publish`. New `fetch-base` job in `build` stage
downloads `INPUT_APK_URL` once, enforces size (`>100MB`) and `unzip -l`
validity, and exposes `base.xapk` as an artifact (`expire_in: 1 day`).
`build-classic` and `build-clone` declare `needs: [fetch-base]`, drop their
download/validate blocks, and keep their patch gates
(`failedPatches == 0` + diff against
`.gitlab/expected-patches-classic.txt` / `-clone.txt`) and
signature/package-id checks. `publish-release` order is unchanged
(version → notes → cert pin → release-exists early exit → changelog gate →
key checks → upload → release POST). All jobs keep
`rules: [$CI_COMMIT_BRANCH == "main"]`.

## Section 2 — Image and cache (approved)

Track `.gitlab/ci-image/Dockerfile`; build and push the initial immutable tag
`jdk21-sdk36.0.0-v1` (bump `-vN` only when SDK pins or the JDK change) and
point every job's `image:` at it. `before_script` slims to `PATH` exports
plus `chmod +x gradlew`. Cache drops `android-sdk/`, `base.xapk`, and
`base.url` (covered by the image and the `fetch-base` artifact); keep
`.gradle/caches/`, `.gradle/wrapper/`, `tools/`. `publish-release` no
longer pays the SDK install.

## Section 3 — Reliability and debug (approved)

Extract the `publish-release` body into `scripts/publish-release.sh` with
`set -euo pipefail` so inline-YAML quoting can never truncate it again
(supersedes the line-172 quoting fix as the long-term guard).
`patch-report-*.json` and `certs-*.txt` use `artifacts: when: always` so
failed flavor and publish runs leave evidence (`fetch-base` keeps
default `on_success`: a bad base must fail fast with no artifact). Pre-push locally:
`bash -n scripts/publish-release.sh` and
`glab ci lint -R RohanDaCoder/Unhooked`. Note: bare `glab ci ...` 404s
from this checkout; always pass `-R RohanDaCoder/Unhooked`.

## Non-goals

MR pipelines, scheduled pipelines, SAST/secret scanning, environment
tracking, and Gradle configuration-cache changes are out of scope.

## Acceptance

- A `main` run with a new `gradle.properties` version and matching
  `CHANGELOG.md` section publishes `v<semver>` with both APKs; a bump
  without notes fails at the changelog gate with the full message intact.
- `fetch-base` runs once per pipeline; flavors contain no download logic.
- Failed builds still attach patch reports and cert dumps.
- `glab ci lint -R RohanDaCoder/Unhooked` passes; `bash -n` passes on the
  extracted script.
