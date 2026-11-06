# AGENTS.md — Unhooked

Morphe bytecode patches (Kotlin) + runtime extension (Java), applied to a stock
Instagram APK to produce `unhooked.apk`. Two modules, one pipeline.

## Layout

- `patches/src/main/kotlin/com/unhooked/patches/<area>/` — patch definitions
  (`network`, `settings`, `restart`, `shared`, `signature`, `display`,
  `screenshot`, `ui`, `quoicouview`, `update`, `debug`, `clone`).
- `extensions/extension/src/main/java/com/unhooked/extension/` — runtime helpers
  merged into the APK (`Config`, `Settings`, feature classes).
- Root `build.gradle.kts` — tasks: `setupTools` → `mergeSplits` → `patchApk` →
  `signApk` → `buildApk`; `installApk` deploys (needs `-Pinstall`);
  `fetchBase` downloads the pinned stock base; `checkPatches` re-gates an
  existing report.
- `gradle/libs.versions.toml` — Morphe versions + tool-binary pins.
- `build/patch-report.json` — machine-readable patch result (see Verification).
- `.gitlab-ci.yml` — merge-gated release press, `main` only (see CI).
- `scripts/` — `publish-release.sh` (CI publish job),
  `rewrite-upstream.sh` + `upstream-replacements.txt` (upstream porting, see
  Upstream ports).
- `docs/` — `ARCHITECTURE.md` (patch internals, UI rules, debug bridge),
  `BUILD_FROM_SOURCE.md` (build, verification, CI variables).
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

`buildApk` gates itself: `patchApk` finalizes with `checkPatches`, which
derives the expected patch set live from the bundle the build just produced
(`morphe list-patches` on the .mpp — the declarations in `patches/src` are
the single source of truth) plus the opt-ins the flags enabled (`Clone` for
`-Pclone`, `Debug bridge` for `-Pdebug`), then fails the build on any failed,
missing or unexpected patch — `BUILD SUCCESSFUL` means the patch set matched.
There is no committed expected-list to maintain. Re-check a report without
rebuilding with the `checkPatches` task (pass the flags it was built with).
On-device smoke + `logcat -d *:E` for `AndroidRuntime` is the final gate.
Clone package id: `com.instagram.android.unhooked`.

Setup prerequisites: JDK 21, Android SDK, and a GitHub token with
`read:packages` (`~/.gradle/gradle.properties` as `gpr.user`/`gpr.key`, or
`GITHUB_TOKEN` env) for the Morphe registry. A dead registry token is masked
locally by the Gradle cache (builds keep passing) and only surfaces in CI —
if plugin resolution fails there, check token validity first (`gh api user`
with it), not the YAML. Tool jars need no manual step:
`setupTools` fetches APKEditor + Morphe CLI into `tools/` (SHA-256 pinned);
`mergeSplits`/`patchApk` depend on it.

## Morphe / extension quirks

- Morphe's `android {}` block in `extensions/extension/build.gradle.kts` is a
  closed imitation: `buildConfigField` fails script compilation. Version
  stamping goes through the `generateUnhookedVersion` task (gitignored
  `UnhookedVersion.java`, wired into `compile*JavaWithJavac`); never hand-edit
  the generated file.
- Version source of truth is `gradle.properties` (`version = 1.1.0`); release
  tags are `v<semver>`. The updater compares the stamp, never Instagram's
  `versionName`.

## Single sources of truth

Every fact gets exactly one home; everything else reads it. A second copy of
a fact is a bug — it drifts, and the drift is silent.

- The version lives only in `gradle.properties`; the release tag, the APK
  stamp and the updater all derive from it.
- The opt-in patch set (`Clone`, `Debug bridge`) lives only in `optInPatches`
  in the root `build.gradle.kts`: the `-e` args the Morphe CLI receives and
  the gate's expectations all read that one list, driven by `-Pclone` and
  `-Pdebug`.
- The default patch set lives only in the patch declarations
  (`default = true/false` in `patches/src`). The gate asks the CLI what the
  bundle declares — there is deliberately **no committed expected-patches
  list** to keep in sync.
- Rebrand tokens live only in `scripts/upstream-replacements.txt`.
- Known exception: the stock-base pin exists in both `fetchBase`'s
  `baseVersion` default and CI's `INPUT_APK_URL` — they must move together
  (see Migrating).

When you add a fact, give it one home and make every other place consume it;
prefer generating over copying. If you catch yourself editing the same value
in two files, stop and wire them together instead.

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
  that is the designed way to find the new address (see
  `docs/ARCHITECTURE.md` → Debug bridge).
- User-facing change ⇒ update `README.md` (settings bullet + project tree) and
  `CHANGELOG.md` (Unreleased entry). Mirror the previous feature commit.
  Changelog entries must stay short and plain — they ship verbatim in the
  in-app update prompt to non-technical users: one bullet per change, say
  what the user sees, no internals, no build jargon.

## Upstream ports (`rewritten-upstream`)

Unhooked is a rebrand of [FeurStagram](https://github.com/jean-voila/FeurStagram);
raw upstream commits do not apply to this tree (different package dirs,
different brand strings). Port through the rewritten mirror instead:

```sh
scripts/rewrite-upstream.sh   # needs git-filter-repo (pip install git-filter-repo);
                              # first run adds the `upstream` remote itself
git log --oneline rewritten-upstream ^dev   # commits not yet ported
git cherry-pick -x <sha>                   # port one; resolve like any cherry-pick
```

- The rewrite applies the fork's rebrand tokens
  (`scripts/upstream-replacements.txt`) plus the three package-dir renames to
  upstream history, so cherry-picks land on the rebranded tree.
- The rewrite is **deterministic**: re-running the script reproduces identical
  hashes, so shas in older port work stay valid as upstream grows.
  Commits that only fixed Feurstagram casing vanish (empty after replacement).
- `scripts/upstream-replacements.txt` must stay pure rules — **no comments**,
  not even stray `#` lines: git-filter-repo's `--replace-text` does not strip
  comments, and a bare `#` would rewrite every `#` in the tree. Log tags
  (`FeurFeed`, `FeurNet`, …) intentionally keep their prefix.
- `rewritten-upstream` is a disposable local mirror — never push it, merge it,
  or build from it; only cherry-pick.

## Migrating to a new Instagram version

1. **Base first, before touching CI.** The stock bundle comes from APKMirror;
   the release-page URL is a template on the version:

   ```
   https://www.apkmirror.com/apk/instagram/instagram-instagram/instagram-<dashed>-release/
   ```

   `<dashed>` is the version with every `.` turned into `-`
   (454.0.0.13.77 → `instagram-454-0-0-13-77-release`, 410.0.0.0.67 →
   `instagram-410-0-0-0-67-release`); older pages carry an extra segment
   (360.0.0.0.37 → `instagram-instagram-360-0-0-0-37-release`). Download the
   `.apkm` bundle there, then ask the user to upload it to this project's
   Package Registry and to paste back the file URL:

   ```sh
   glab packages upload base-<major>.apkm --name unhooked-base --version <ig-version>
   # e.g. glab packages upload base-454.apkm --name unhooked-base --version 454.0.0.13.77
   ```

   Do not guess or construct the URL, and do not write anything into
   `.gitlab-ci.yml` until the user has provided it. (Project access tokens
   cannot publish generic packages — the upload has to run as the user.)
   Optionally verify it is live (`curl -sIL <url>` → 200) before committing.
2. Update the pins: `INPUT_APK_URL` in `.gitlab-ci.yml`, the `baseVersion`
   default in `build.gradle.kts` (`fetchBase`), the `.env.example` hint.
3. Port/rebuild: usually fingerprints still match and only the base changes;
   adjust fingerprints (see Patch authoring rules) where they don't. No
   expected-list to touch — the gate reads the bundle itself.
4. `:patches:build`, then `buildApk` against the new base; gate on
   `build/patch-report.json` (see Build & verify). Update `README.md` /
   `CHANGELOG.md` for anything user-facing.

## CI / release (GitLab, `main` only)

- Merging `dev` → `main` is the release act: `build-classic` +
  `build-clone` in parallel (preflight + gates + signature/package checks),
  then `publish-release` (cert pin + publish) creates `v<VERSION>` with both
  APKs **iff** that release doesn't exist yet. No tags, no schedules, no MR jobs.
- Stock base is self-hosted: `unhooked-base/<ig-version>` in this project's
  Package Registry, file `base-<major>.apkm` (`glab packages upload/download`).
  CI fetches only from that registry — never reintroduce APKPure/APKMirror
  URLs as build inputs. The APKMirror template under Migrating is only for
  the manual download before upload, never for the pipeline.
- CI variables (all Protected; secrets Masked): `GITHUB_ACTOR`,
  `GITHUB_TOKEN`, `GITLAB_API_TOKEN` (project token, `api` scope — creates the
  release), `UNHOOKED_KEYSTORE_B64` (plain variable, base64 text — **not** File
  type), `UNHOOKED_KEYSTORE_PASS`, optional `RELEASE_CERT_SHA256` pin. Set via
  `glab variable set KEY -m -p` with values piped over stdin, never argv.
- Validate YAML with `glab ci lint`, read jobs with `glab ci trace <job-id>`.

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

- Branch off `dev` for every piece of work; commit early in small,
  bite-sized steps, and push the branch — **the branches are the granular
  history, so keep them after merging** (don't delete merged branches).
  Merge with squash (feature → `dev`, and `dev` → `main` at release): the
  target branch stays one clean commit per feature, and the small steps
  remain one `git log <branch>` away. Name the branch in the squash-commit
  body when the steps matter. `main` is protected (release-only secrets);
  `origin` is GitLab, `upstream` (`jean-voila/Feurstagram`) is kept for
  pulling upstream (the `rewrite-upstream.sh` script manages it). Never
  commit `.env`, `*.keystore`, or `tools/` jars.
- Quote `-Papk="..."` — stock APK filenames contain parens, which break nested
  shell quoting. Quote SDK package names: `android sdk install
  'platforms;android-30'` — bare `;` splits statements.
- YAML `: ` inside plain scalars is a mapping error — quote script lines
  containing it (`'curl ... "Content-Type: application/json" ...'`). Never
  backslash-escape quotes inside them: `\"` survives literally and breaks the
  shell quoting (plain double quotes work, escaped ones don't).
- `yes | sdkmanager --licenses` dies with SIGPIPE (141) under pipefail — the
  repo no longer uses sdkmanager (android CLI instead); don't reintroduce it.
