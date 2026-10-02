# CI Improvement Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Restructure the merge-gated pipeline around a `fetch-base` DAG job, the baked runner image, and an extracted publish script.

**Architecture:** Four sequential tasks on `feature/ci-custom-image`: extract `publish-release` into a shell script (kills the YAML-quoting bug class), add a `fetch-base` job feeding both flavors via `needs`, switch all jobs to the immutable custom image with a trimmed cache, then set `when: always` on report artifacts. Each task ends with `glab ci lint` green and its own commit.

**Tech Stack:** GitLab CI YAML (`main`-only rules), POSIX shell with `bash` (`set -euo pipefail`), custom Docker runner image (`eclipse-temurin:21-jdk-jammy` + Android SDK 36.0.0), `glab` CLI for validation.

## Global Constraints

- Every job keeps `rules: [if: $CI_COMMIT_BRANCH == "main"]` — no MR pipelines.
- Image tags are immutable (`jdk21-sdk36.0.0-vN`), never `:latest`.
- `fetch-base` artifact uses `expire_in: 1 day`; flavor/publish artifacts use `expire_in: 2 weeks`.
- `fetch-base` artifacts default to `on_success` (a bad base fails fast with no artifact).
- Flavor patch gates stay exact: `failedPatches == 0` plus literal diff against `.gitlab/expected-patches-classic.txt` / `-clone.txt`.
- Never `set -x` in CI scripts — `GITLAB_API_TOKEN` travels in a curl header.
- `glab` commands from this checkout need `-R RohanDaCoder/Unhooked` (bare detection 404s).
- This repo has no unit-test suite; verification is `bash -n`, `glab ci lint`, and diff review.

---

### Task 1: Extract `scripts/publish-release.sh`

**Files:**
- Create: `scripts/publish-release.sh`
- Modify: `.gitlab-ci.yml:148-189` (replace `publish-release` `script:` block with one call line)

**Interfaces:**
- Consumes: `build-classic` artifacts (`unhooked-classic.apk`, `patch-report-classic.json`, `certs-classic.txt`), `build-clone` artifacts (`unhooked-clone.apk`, `patch-report-clone.json`, `certs-clone.txt`), repo files (`gradle.properties`, `CHANGELOG.md`), CI variables (`GITLAB_API_TOKEN`, `UNHOOKED_KEYSTORE_PASS`, `RELEASE_CERT_SHA256`, `CI_API_V4_URL`, `CI_PROJECT_ID`, `CI_COMMIT_SHA`, `CI_JOB_TOKEN`).
- Produces: exit `0` with `Release v$VERSION already exists` when the release exists; otherwise uploads both APKs and POSTs the release, exiting nonzero on any gate failure. Task 2–4 rely on the call line `bash scripts/publish-release.sh` staying stable.

- [ ] **Step 1: Create the script with the exact current publish body**

Create `scripts/publish-release.sh` with this complete content (identical logic to the current inline block, plus `set -euo pipefail`; YAML-only quoting removed since shell needs none):

```sh
#!/usr/bin/env bash
# Unhooked publish-release — extracted from .gitlab-ci.yml so YAML quoting
# (e.g. `#` comment truncation) can never silently rewrite it.
# Runs under `set -euo pipefail`: any gate failure aborts the job.
# Never add `set -x`: GITLAB_API_TOKEN travels in a curl header below.
set -euo pipefail

VERSION=$(grep '^version *=' gradle.properties | cut -d= -f2 | tr -d ' ')
IG_VERSION=$(aapt dump badging unhooked-classic.apk | grep -o "versionName='[^']*'" | cut -d"'" -f2)
test -n "$VERSION" || (echo "no version in gradle.properties" >&2 && exit 1)
NOTES=$(awk -v ver="$VERSION" '$0 == "## " ver {f=1; next} /^## / {f=0} f' CHANGELOG.md)
# Both sides are normalized (colons stripped) since keytool and
# apksigner print the same digest with different separators/casing.
if [ -n "${RELEASE_CERT_SHA256:-}" ]; then
  PIN=$(echo "$RELEASE_CERT_SHA256" | tr -d ':')
  tr -d ':' < certs-classic.txt | grep -qi "$PIN" || (echo "classic APK not signed with the release key" >&2 && exit 1)
  tr -d ':' < certs-clone.txt | grep -qi "$PIN" || (echo "clone APK not signed with the release key" >&2 && exit 1)
else
  echo "WARNING: RELEASE_CERT_SHA256 unset — cert pin skipped"
fi
if [ "$(curl -s -o /dev/null -w '%{http_code}' "$CI_API_V4_URL/projects/$CI_PROJECT_ID/releases/v$VERSION")" = "200" ]; then
  echo "Release v$VERSION already exists — build verified only."
  exit 0
fi
# New release: the CHANGELOG section is the release notes (rendered in the
# update prompt), so a version bump without one fails here, not silently.
test -n "$(echo "$NOTES" | tr -d '[:space:]')" || (echo "No CHANGELOG.md section for ## $VERSION — cut the release notes before merging" >&2 && exit 1)
test -n "${UNHOOKED_KEYSTORE_PASS:-}" || (echo "refusing to publish without the release key" >&2 && exit 1)
test -n "${GITLAB_API_TOKEN:-}" || (echo "refusing to publish without GITLAB_API_TOKEN" >&2 && exit 1)
PKG="$CI_API_V4_URL/projects/$CI_PROJECT_ID/packages/generic/unhooked/v$VERSION"
curl --fail --header "JOB-TOKEN: $CI_JOB_TOKEN" --upload-file unhooked-classic.apk "$PKG/unhooked-classic.apk"
curl --fail --header "JOB-TOKEN: $CI_JOB_TOKEN" --upload-file unhooked-clone.apk "$PKG/unhooked-clone.apk"
jq -n --arg v "$VERSION" --arg ig "$IG_VERSION" --arg sha "$CI_COMMIT_SHA" --arg pkg "$PKG" --arg note "$NOTES" '{
  name: ("Unhooked " + $v),
  tag_name: ("v" + $v),
  ref: $sha,
  description: ("## Unhooked " + $v + "\nBased on Instagram " + $ig + "\n\n" + $note),
  assets: { links: [
    { name: "unhooked-classic.apk", url: ($pkg + "/unhooked-classic.apk"), link_type: "other" },
    { name: "unhooked-clone.apk", url: ($pkg + "/unhooked-clone.apk"), link_type: "other" }
  ] }
}' > release.json
curl --fail --request POST --header "PRIVATE-TOKEN: $GITLAB_API_TOKEN" --header "Content-Type: application/json" --data @release.json "$CI_API_V4_URL/projects/$CI_PROJECT_ID/releases"
```

Notes on deliberate deviations from the inline block (behavior-preserving):
- `${VAR:-}` guards replace bare `$VAR` because `set -u` would abort on unset optional variables (`RELEASE_CERT_SHA256` is legitimately unset).
- With `pipefail`, an empty `IG_VERSION` grep aborts immediately instead of failing later at the release POST — fail-fast, same outcome (job fails), earlier signal.

- [ ] **Step 2: Syntax-check the script**

Run: `bash -n scripts/publish-release.sh`
Expected: exit `0`, no output.

- [ ] **Step 3: Replace the inline block with one call line**

In `.gitlab-ci.yml`, replace the whole `publish-release` `script:` block (current lines 148–189) with:

```yaml
  script:
    - bash scripts/publish-release.sh
```

Keep the job's `stage`, `image`, `timeout`, `interruptible`, `rules`, and `needs` lines untouched — Task 3 and Task 4 own those.

- [ ] **Step 4: Lint the pipeline**

Run: `glab ci lint -R RohanDaCoder/Unhooked .gitlab-ci.yml`
Expected: `✓ CI/CD YAML is valid!`

- [ ] **Step 5: Commit**

```bash
git add scripts/publish-release.sh .gitlab-ci.yml
git commit -m "ci: extract publish-release into scripts/publish-release.sh"
```

---

### Task 2: Add `fetch-base` job and rewire flavors via `needs`

**Files:**
- Modify: `.gitlab-ci.yml:54-134` (add `fetch-base` job; slim both flavor `script:` blocks; add `needs:` to both flavors)

**Interfaces:**
- Consumes: `INPUT_APK_URL` variable (defaults to the self-hosted base in `variables:`), call-line contract from Task 1 (untouched).
- Produces: `base.xapk` artifact (`expire_in: 1 day`, `on_success`) consumed by `build-classic` and `build-clone` via `needs: [{job: fetch-base, artifacts: true}]`. Task 3 relies on flavors containing no download logic.

- [ ] **Step 1: Add the `fetch-base` job before `.flavor-build`**

Insert this block immediately above the `# Flavors build in parallel` comment (current line 54):

```yaml
# Base is downloaded once and fanned out via `needs` artifacts — flavors
# must not download it themselves (parallel runners each paid a full
# download before). Always a fresh download: the base is immutable at its
# registry path, and the size/zip gates below catch error pages.
fetch-base:
  stage: build
  image: eclipse-temurin:21-jdk-jammy
  timeout: 15m
  interruptible: true
  rules:
    - if: $CI_COMMIT_BRANCH == "main"
  script:
    - test -n "$INPUT_APK_URL" || (echo "INPUT_APK_URL is not set" >&2 && exit 1)
    - curl -sSL --retry 3 --fail -o base.xapk "$INPUT_APK_URL"
    - test "$(stat -c%s base.xapk)" -gt 100000000 || (echo "base.xapk suspiciously small — probably an error page" >&2 && exit 1)
    - unzip -l base.xapk > /dev/null || (echo "base.xapk is not a valid archive" >&2 && exit 1)
    - ls -la base.xapk
  artifacts:
    expire_in: 1 day
    paths:
      - base.xapk
```

(The `image:` line is intentionally still `eclipse-temurin:21-jdk-jammy` — Task 3 swaps all three at once.)

- [ ] **Step 2: Slim `build-classic` — add `needs`, delete the download block**

Add after `extends: .flavor-build`:

```yaml
  needs:
    - job: fetch-base
      artifacts: true
```

Delete from its `script:` (current lines 66–79): the `INPUT_APK_URL` preflight line, the `# base.xapk is cached ...` comment trio, the `if [ ! -f base.xapk ] ... fi` download block, and the size + `unzip -l` gate lines. Keep: the `GITHUB_TOKEN` preflight, the keystore-decode block, and everything from `# Classic build` down. Resulting head of `build-classic`:

```yaml
build-classic:
  extends: .flavor-build
  needs:
    - job: fetch-base
      artifacts: true
  script:
    - test -n "$GITHUB_TOKEN" || (echo "GITHUB_TOKEN is not set" >&2 && exit 1)
    - |
      if [ -n "$UNHOOKED_KEYSTORE_B64" ]; then
        echo "$UNHOOKED_KEYSTORE_B64" | base64 -d > unhooked.keystore
      fi
    # Classic build (replaces stock Instagram).
    - UNHOOKED_OUTPUT_PATH=unhooked-classic.apk ./gradlew buildApk "-Papk=base.xapk" --console=plain
    ...
```

- [ ] **Step 3: Slim `build-clone` identically**

Same `needs:` block (job `fetch-base`, `artifacts: true`). Delete its `INPUT_APK_URL` preflight, download block, and size/zip gates (current lines 106–114, including the `# --- preflight: same as classic ...` comment — replace that comment line with `# --- preflight: base arrives via needs: fetch-base ---`). Keep `GITHUB_TOKEN`, keystore decode, and everything from `# Clone build` down.

- [ ] **Step 4: Verify no flavor still touches the network for the base**

Run: `Select-String -Path .gitlab-ci.yml -Pattern 'base\.url|if \[ ! -f base\.xapk'`
Expected: no matches (the cache sidecar logic is gone; cache-path cleanup lands in Task 3).

- [ ] **Step 5: Lint**

Run: `glab ci lint -R RohanDaCoder/Unhooked .gitlab-ci.yml`
Expected: `✓ CI/CD YAML is valid!`

- [ ] **Step 6: Commit**

```bash
git add .gitlab-ci.yml
git commit -m "ci: fetch base once via fetch-base job with needs fan-out"
```

---

### Task 3: Switch to the custom image, slim `before_script`, trim cache

**Files:**
- Modify: `.gitlab-ci.yml:27-52` (`variables` `ANDROID_HOME`, `cache:`, `before_script:`, all three `image:` lines)

**Interfaces:**
- Consumes: registry image `registry.gitlab.com/rohandacoder/unhooked/ci:jdk21-sdk36.0.0-v1` (built/pushed in Step 1), `fetch-base` artifact contract from Task 2 (untouched).
- Produces: all jobs (`fetch-base`, `.flavor-build`, `publish-release`) on the baked image with a two-line `before_script`; cache holding only `.gradle/caches/`, `.gradle/wrapper/`, `tools/`. Task 4 relies on these regions being final.

- [ ] **Step 1: Build and push the image (local Docker, once)**

Run from the repo root:

```bash
docker build -t registry.gitlab.com/rohandacoder/unhooked/ci:jdk21-sdk36.0.0-v1 .gitlab/ci-image
docker push registry.gitlab.com/rohandacoder/unhooked/ci:jdk21-sdk36.0.0-v1
```

Expected: push completes; the tag appears under the project's Container Registry. If local Docker is unavailable, stop here and leave this task uncommitted — do not point YAML at an image that does not exist yet.

- [ ] **Step 2: Point all jobs at the baked image**

Replace all three `image: eclipse-temurin:21-jdk-jammy` lines (`.flavor-build`, `fetch-base`, `publish-release`) with:

```yaml
  image: registry.gitlab.com/rohandacoder/unhooked/ci:jdk21-sdk36.0.0-v1
```

- [ ] **Step 3: Slim `before_script` and repoint `ANDROID_HOME`**

Replace the whole `before_script:` block (current lines 42–52) with:

```yaml
before_script:
  - export PATH="/opt/android-sdk/build-tools/36.0.0:/opt/android-sdk/platform-tools:$HOME/.local/bin:$PATH"
  - chmod +x gradlew
```

And change `variables:` `ANDROID_HOME: "$CI_PROJECT_DIR/android-sdk"` to:

```yaml
  ANDROID_HOME: "/opt/android-sdk"
```

(The Dockerfile bakes the SDK at `/opt/android-sdk` with `platforms;android-35/36`, `build-tools;36.0.0`, `platform-tools`; the export is belt-and-braces since the image `ENV` already sets `PATH`.)

- [ ] **Step 4: Trim the cache**

Replace the `cache:` block (current lines 32–40) with:

```yaml
cache:
  key: unhooked-main
  paths:
    - .gradle/caches/
    - .gradle/wrapper/
    - tools/
```

Rationale: `android-sdk/` lives in the image now; `base.xapk`/`base.url` are obsolete since Task 2 (fresh download + artifact fan-out).

- [ ] **Step 5: Lint**

Run: `glab ci lint -R RohanDaCoder/Unhooked .gitlab-ci.yml`
Expected: `✓ CI/CD YAML is valid!`

- [ ] **Step 6: Commit**

```bash
git add .gitlab-ci.yml
git commit -m "ci: run all jobs on baked image jdk21-sdk36.0.0-v1, trim setup and cache"
```

---

### Task 4: `when: always` on report artifacts + final verification

**Files:**
- Modify: `.gitlab-ci.yml` (three `artifacts:` blocks: `build-classic`, `build-clone`, `publish-release` — `publish-release` currently has no `artifacts:` block, so add one)

**Interfaces:**
- Consumes: everything Tasks 1–3 produced.
- Produces: failed runs attach `patch-report-*.json` and `certs-*.txt`; `publish-release` attaches `release.json` on success path context. Nothing downstream — this is the last task.

- [ ] **Step 1: Add `when: always` to both flavor artifact blocks**

In `build-classic` and `build-clone`, change:

```yaml
  artifacts:
    expire_in: 2 weeks
    paths:
```

to:

```yaml
  artifacts:
    expire_in: 2 weeks
    when: always
    paths:
```

(`fetch-base` keeps the default `on_success` per Global Constraints.)

- [ ] **Step 2: Give `publish-release` an always-artifact block**

Append to the `publish-release` job (after its `script:` block):

```yaml
  artifacts:
    expire_in: 2 weeks
    when: always
    paths:
      - release.json
```

- [ ] **Step 3: Final verification pass**

Run, in order:

```bash
bash -n scripts/publish-release.sh
glab ci lint -R RohanDaCoder/Unhooked .gitlab-ci.yml
```

Expected: first command silent with exit `0`; second prints `✓ CI/CD YAML is valid!`. Then review the full diff:

```bash
git diff dev...HEAD --stat
git diff dev...HEAD -- .gitlab-ci.yml
```

Expected shape: one new `fetch-base` job, two slimmed flavor scripts with `needs:`, three swapped `image:` lines, slimmed `before_script`, trimmed `cache:`, one-line publish `script:`, three `when: always` blocks, plus new `scripts/publish-release.sh`. No `rules:` changes, no stage changes, no `:latest` anywhere — verify with:

```bash
Select-String -Path .gitlab-ci.yml -Pattern ':latest'
```

Expected: no matches.

- [ ] **Step 4: Commit**

```bash
git add .gitlab-ci.yml
git commit -m "ci: keep patch reports and certs on failed runs"
```

---

## Out of scope (do not add)

MR pipelines, scheduled pipelines, SAST/secret scanning, `environment:` tracking, Gradle configuration-cache changes, and any new manifest permission or `minSdk` change (stays 28).
