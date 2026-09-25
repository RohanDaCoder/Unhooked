# AGENTS.md — Unhooked

Morphe bytecode patches (Kotlin) + runtime extension (Java), applied to a stock
Instagram APK to produce `unhooked.apk`. Two modules, one pipeline.

## Layout

- `patches/src/main/kotlin/com/unhooked/patches/<area>/` — patch definitions
  (`network`, `settings`, `screenshot`, `display`, `signature`, `media`, `debug`, `clone`).
- `extensions/extension/src/main/java/com/unhooked/extension/` — runtime helpers
  merged into the APK (`Config`, `Settings`, feature classes).
- `build.sh` — full pipeline: gradle bundle → Morphe patch → sign → `unhooked.apk`.
- `build/patch-report.json` — machine-readable patch result (see Verification).
- `docs/` — mostly website; `docs/BUILD_FROM_SOURCE.md` (build reqs),
  `docs/DESIGN.md` (UI rules), `docs/superpowers/plans/` (feature plans).
- `.dexloom-ws/` + `dexloom-*.jar` — local smali workspace/CLI for fingerprint
  research. Gitignored, never commit.

## Build & verify (no test suite exists)

No `src/test` anywhere; `:patches:test` is `NO-SOURCE`. Verification is:

```sh
.\gradlew.bat :patches:build
./build.sh <instagram.apk> --clone   # add --install for device deploy
```

Then check `build/patch-report.json`: all patches under `appliedPatches`,
`failedPatches: []`. A `BUILD SUCCESSFUL` alone proves nothing about patch
application. On-device smoke + `logcat -d *:E` for `AndroidRuntime` is the final
gate. Clone package id: `com.instagram.android.unhooked`.

Setup prerequisites: JDK 21, Android SDK, and a GitHub token with
`read:packages` (`~/.gradle/gradle.properties` as `gpr.user`/`gpr.key`, or
`GITHUB_TOKEN` env) for the Morphe registry.

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
- User-facing change ⇒ update `Readme.md` (settings bullet + project tree) and
  `CHANGELOG.md` (Unreleased entry). Mirror the previous feature commit.

## Windows shell gotchas (pwsh)

- Quote SDK package names: `android sdk install 'platforms;android-30'` —
  bare `;` splits statements.
- `build.sh` must run under Git Bash (`& 'C:\Program Files\Git\bin\bash.exe' ./build.sh ...`),
  not WSL `bash` (chokes on the file's CRLF at `set -euo pipefail`).
- The stock APK filename contains parens, which break nested shell quoting —
  copy to a simple name first (`Copy-Item ... ig-base.apk`), delete after.
- Device work via the `android` CLI (`install --device=... --apks=...`,
  `layout`, `screen capture`); `adb` only as fallback. Emulator note: QEMU on
  x86_64 hosts cannot boot `arm64-v8a` images, and this APK ships
  `lib/arm64-v8a` only — on-device testing needs physical ARM hardware.
