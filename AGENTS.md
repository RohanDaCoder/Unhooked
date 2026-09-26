# AGENTS.md — Unhooked

Morphe bytecode patches (Kotlin) + runtime extension (Java), applied to a stock
Instagram APK to produce `unhooked.apk`. Two modules, one pipeline.

## Layout

- `patches/src/main/kotlin/com/unhooked/patches/<area>/` — patch definitions
  (`network`, `settings`, `screenshot`, `display`, `signature`, `media`, `debug`, `clone`).
- `extensions/extension/src/main/java/com/unhooked/extension/` — runtime helpers
  merged into the APK (`Config`, `Settings`, feature classes).
- Root `build.gradle.kts` — tasks: `setupTools` → `mergeSplits` → `patchApk` →
  `signApk` → `buildApk`; `installApk` deploys (needs `-Pinstall`).
- `gradle/libs.versions.toml` — Morphe versions + tool-binary pins.
- `build/patch-report.json` — machine-readable patch result (see Verification).
- `.gitlab/expected-patches-classic.txt` / `-clone.txt` — the exact patch
  lists a release build must apply (10/11, comment-free so CI can diff
  literally).
- `.gitlab-ci.yml` — merge-gated release press, `main` only (see CI).
- `docs/` — user guides (`INSTALL`, `BUILD_FROM_SOURCE`, `FAQ`, `PRIVACY`),
  `DEVELOPERS.md` (build/sign/debug), `ARCHITECTURE.md` (patch internals),
  `docs/DESIGN.md` (UI rules), `docs/superpowers/` (feature plans).
- `.dexloom-ws/` + `dexloom-*.jar` — local smali workspace/CLI for fingerprint
  research. Gitignored, never commit.

## Build & verify (no test suite exists)

No `src/test` anywhere; `:patches:test` is `NO-SOURCE`. Verification is:

```sh
.\gradlew.bat :patches:build
.\gradlew.bat buildApk -Pclone   # -Papk=<apk> or INPUT_APK in .env; installApk -Pinstall to deploy
```

Input: `-Papk=<.apk|.apkm|.xapk|.apks>` wins, `INPUT_APK` (env or `.env`) is the
fallback. Bundle formats route to the APKEditor merge purely by extension —
name files truthfully. Output: `UNHOOKED_OUTPUT_PATH` env/`.env`, default
`unhooked.apk` (the Morphe CLI does **not** append `.apk`, so the default must
carry the extension). Signing reads `UNHOOKED_KEYSTORE[/_PASS/_ALIAS]` from
env/`.env`; without the pass the patcher throwaway-signs and `signApk` skips.

Then check `build/patch-report.json`: all patches under `appliedPatches`,
`failedPatches: []`, **and** the applied list matches the per-flavor
`.gitlab/expected-patches-*.txt`. A `BUILD SUCCESSFUL` alone proves nothing about
patch application. On-device smoke + `logcat -d *:E` for `AndroidRuntime` is
the final gate. Clone package id: `com.instagram.android.unhooked`.

Setup prerequisites: JDK 21, Android SDK, and a GitHub token with
`read:packages` (`~/.gradle/gradle.properties` as `gpr.user`/`gpr.key`, or
`GITHUB_TOKEN` env) for the Morphe registry. A dead registry token is masked
locally by the Gradle cache (builds keep passing) and only surfaces in CI —
if plugin resolution fails there, check token validity first (`gh api user`
with it), not the YAML. Tool jars need no manual step:
`setupTools` fetches APKEditor + Morphe CLI into `tools/` (SHA-256 pinned);
`mergeSplits`/`patchApk` depend on it.

## Morphe / extension quirks (proven, do not re-derive)

- Morphe's `android {}` block in `extensions/extension/build.gradle.kts` is a
  closed imitation: `buildConfigField` fails script compilation. Version
  stamping goes through the `generateUnhookedVersion` task (gitignored
  `UnhookedVersion.java`, wired into `compile*JavaWithJavac`); never hand-edit
  the generated file.
- Version source of truth is `gradle.properties` (`version = 1.0.0`); release
  tags are `v<semver>`. The updater compares the stamp, never Instagram's
  `versionName`.

## Patch authoring rules

- Fingerprints must not reference `LX/` obfuscated names. Anchor on stable class
  names / wire strings / method shapes; confirm against smali in `.dexloom-ws/`
  (e.g. `Select-String -Path <...>.smali -Pattern ...`).
- `invoke-static { ... }` only encodes registers v0–v15. For high registers
  (e.g. `p1` == v31 in a `.registers 34` method) use `invoke-static/range`.
  `InlineSmaliCompiler` rejects bad registers with `Invalid register` then
  `Collection is empty` at `addInstructions` — that error means the register
  form, not the fingerprint.
- Reuse a real method register (`SettingsEntryPointPatch.kt`) or the `/range`
  single-register idiom (`FeedItemFilterPatch.kt`) instead of inventing registers.
- The extension compiles against `android.jar` only: never import Instagram
  classes; reach them via reflection on public fields (`getField`, no
  `setAccessible`). Never overwrite view tags (Instagram casts them back).
- Runtime code must degrade to a toast, never crash; do IO off the main thread.
- Constraints: minSdk 28, no new manifest permissions, feature-gated on its
  settings toggle.
- Endpoint renames are the #1 silent killer: `Block.java` matches URL
  substrings, so a moved endpoint (e.g. stories tray leaving
  `/feed/reels_tray` on the 449 base) applies cleanly yet never fires. A
  `-Pdebug` build + `DebugBridge` TRACE broadcast logs every request path —
  that is the designed way to find the new address.
- User-facing change ⇒ update `Readme.md` (settings bullet + project tree) and
  `CHANGELOG.md` (Unreleased entry). Mirror the previous feature commit.

## CI / release (GitLab, `main` only)

- Merging `dev` → `main` is the release act: `build-classic` +
  `build-clone` in parallel (preflight + gates + signature/package checks),
  then `publish-release` (cert pin + publish) creates `v<VERSION>` with both
  APKs **iff** that release doesn't exist yet. No tags, no schedules, no MR jobs.
- Stock base is self-hosted: `unhooked-base/<ig-version>` in this project's
  Package Registry (`glab packages upload/download`). No mirrors anywhere —
  do not reintroduce APKPure/APKMirror URLs.
- CI variables (all Protected; secrets Masked): `GITHUB_ACTOR`,
  `GITHUB_TOKEN`, `GITLAB_API_TOKEN` (project token, `api` scope — creates the
  release), `UNHOOKED_KEYSTORE_B64` (plain variable, base64 text — **not** File
  type), `UNHOOKED_KEYSTORE_PASS`, optional `RELEASE_CERT_SHA256` pin. Set via
  `glab variable set KEY -m -p` with values piped over stdin, never argv.
- Project access tokens **cannot** publish generic packages (401 even with
  `api` scope) — uploads need a PAT or `glab packages` (own auth). Validate
  YAML with `glab ci lint`, read jobs with `glab ci trace <job-id>`.

## Device testing

- `android` CLI first (`install --device=... --apks=...`, `layout`, `screen
  capture`); `adb` only where the CLI has no equivalent (logcat, input,
  `am start`). Read `android-cli` skill references `interact.md` (before any
  layout/screen use) and `journeys.md` (before journey runs).
- Parse `android layout` output piped through `Select-String` — never `--output`
  files into the repo. Screenshots and dumps go to the temp dir; keep the repo
  root free of `*.png`, `*.json` dumps, and APKs (all gitignored, still clean
  them up).
- `Done` in settings only **opens a confirmation dialog** — tap its Restart
  button by coordinates and confirm the PID changed, or no restart happened.
  Network blocks apply live per-request, but already-rendered surfaces (stories
  tray) and Room-prefetch DBs only clear on the confirmed restart.
- Base is arm64-v8a-only (`native-code: 'arm64-v8a'`): x86_64 AVDs reject it
  with `INSTALL_FAILED_NO_MATCHING_ABIS` — test on physical ARM hardware (or an
  arm64 AVD). Never log into test accounts on behalf of the user; hand over at
  the login screen.
- Debug builds (`-Pdebug`) carry the ADB settings bridge and a spoofed ancient
  version — never distribute; installing one requires uninstalling the same-ID
  release flavor first (cert mismatch), which wipes its session.

## Git flow & shell gotchas (pwsh)

- Work on `dev`, branch off it, merge to `main` for release. `main` is
  protected (release-only secrets); `origin` is GitLab, `upstream`
  (`jean-voila/FeurStagram`) is kept for pulling upstream. Never commit
  `.env`, `*.keystore`, or `tools/` jars.
- Quote `-Papk="..."` — stock APK filenames contain parens, which break nested
  shell quoting. Quote SDK package names: `android sdk install
  'platforms;android-30'` — bare `;` splits statements.
- YAML `: ` inside plain scalars is a mapping error — quote script lines
  containing it (`'curl ... "Content-Type: application/json" ...'`). Never
  backslash-escape quotes inside them: `\"` survives literally and breaks the
  shell quoting (proven — plain double quotes work, escaped ones don't).
- `yes | sdkmanager --licenses` dies with SIGPIPE (141) under pipefail — the
  repo no longer uses sdkmanager (android CLI instead); don't reintroduce it.
