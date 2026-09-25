# Rebrand — Design (2026-09-25)

## Context

Feurstagram is a Morphe bytecode patch set (Kotlin) + runtime extension (Java)
applied to stock Instagram APK, producing `feurstagram.apk`. The brand string
is load-bearing across code, build, runtime, docs, site, and assets.

Current touchpoints (verified by grep):

- Code namespaces: `com.feurstagram.patches.*`, `com.feurstagram.extension`
  (`ClonePatch.kt:1`, `Constants.kt:9`, `Config.java:1`, `UpdateChecker.java:1`).
- Build: `settings.gradle.kts:1` (`feurstagram-patches`), `build.sh:34,68-69`
  (`feurstagram.apk`, `feurstagram.keystore`, `FEURSTAGRAM_*` env).
- Clone defaults: `ClonePatch.kt:32-48` (`com.instagram.android.feurstagram`,
  `Feurstagram`).
- Runtime: `Config.java:17` (`feurstagram_prefs`), `UpdateChecker.java:68,72`
  (`rohandacoder/Feurstagram`, `Feurstagram-UpdateCheck`), `Blocked by Feurstagram`
  IOException, Settings/Onboarding/FollowPrompt titles, log tags.
- Docs/site: `Readme.md`, `CHANGELOG.md`, `docs/*.md`, `docs/**/*.html`,
  `docs/sitemap.xml`, `robots.txt`, `.gitlab/ISSUE_TEMPLATE/*`,
  `REPOSITORY_SETTINGS.md`, release template.
- Assets: `docs/app_icon.png`, `app_icon_not_rounded.png`, `preview.png`,
  `screens.png`, `tuto.gif`, `docs/press/`, `.sketch` sources.

## Decisions (validated)

1. Scope: full + docs/website (code, build, runtime, docs, site, assets, externals).
2. Approach: A — tokenized full rename, executed in one commit once the name
   is chosen.
3. Compatibility: clean break. No `feurstagram_prefs` migration, no dual
   `REPO` fallback. Old installs reinstall. New keystore + alias.
4. Brand name: **Unhooked** (`Unhooked` / `unhooked` / `UNHOOKED`).
   Derived tokens: clone package `com.instagram.android.unhooked`, prefs
   `unhooked_prefs`, APK `unhooked.apk`, keystore `unhooked.keystore`
   (alias `unhooked`), env `UNHOOKED_*`, `USER_AGENT Unhooked-UpdateCheck`,
   repo `rohandacoder/Unhooked`.

## Token map

| Token   | Meaning             | Example   | Used for                                                                                                                                              |
| ------- | ------------------- | --------- | ----------------------------------------------------------------------------------------------------------------------------------------------------- |
| `Brand` | Display/capitalized | `NewName` | App label (`ClonePatch appName`), Settings titles, docs headings, `USER_AGENT` prefix, `Blocked by <Brand>`                                           |
| `brand` | Lowercase           | `newname` | Package suffix (`com.instagram.android.<brand>`), dirs `com/<brand>/...`, prefs (`<brand>_prefs`), APK (`<brand>.apk`), keystore (`<brand>.keystore`) |
| `BRAND` | Uppercase           | `NEWNAME` | Env vars (`<BRAND>_KEYSTORE_PASS`, `<BRAND>_KEYSTORE`, `<BRAND>_KEY_ALIAS`)                                                                           |
| `Repo`  | GitHub repo name    | `NewName` | `UpdateChecker REPO = <owner>/<Repo>`, site URLs (`<owner>.gitlab.io/<Repo>/`), badges, release template                                              |

Case-sensitive pass covers all four legacy casings: `Feurstagram`,
`feurstagram`, `FEURSTAGRAM`, `FeurStagram` (repo-casing in URLs).

## Execution groups (in order)

1. **Code namespaces.** Move `patches/src/.../com/feurstagram/` to
   `com/<brand>/`, `extensions/.../com/feurstagram/` to `com/<brand>/`;
   update `package` lines, imports, `Constants.EXTENSION_PACKAGE`
   (`Constants.kt:9`). Then `settings.gradle.kts` project name and `build.sh`
   outputs/env.
2. **Runtime strings.** `Config.PREFS` (`Config.java:17`), `ClonePatch`
   `packageName`/`appName` defaults (`ClonePatch.kt:32-48`), `UpdateChecker`
   `REPO` + `USER_AGENT` (`UpdateChecker.java:68,72`), `Block` IOException
   text, `Settings`/`Onboarding`/`FollowPrompt`/`Toasts` titles, log tags.
3. **Verify build.** `:patches:build`, then `build.sh ig-base.apk --clone`;
   gate on `build/patch-report.json` (`failedPatches: []`).
4. **Docs/site.** `Readme.md` (settings bullet + project tree per AGENTS.md),
   `CHANGELOG.md` Unreleased entry, `docs/*.md`, `docs/**/*.html`,
   `sitemap.xml`, `robots.txt`, issue templates, `REPOSITORY_SETTINGS.md`.
5. **Assets + external cutover.** Regenerate `app_icon.png`,
   `app_icon_not_rounded.png`, `preview.png`, `screens.png`, `docs/press/`
   (`.sketch` sources updated too; `tuto.gif` re-recorded if label visible).
   Then repo rename, release template, contact links (email, IG handle,
   Discord) — only if new handles exist, else keep and note.

Order is load-bearing: code+build first so verification gates docs/assets.

## Risks and mitigations

- Missed case variant → triple-pattern grep gate; zero hits except
  `CHANGELOG.md` history and `.git/`.
- `EXTENSION_PACKAGE` out of sync with moved classes → fails fast at
  `:patches:build`.
- New package suffix must pass `ClonePatch` regex `^[a-z]\w*(\.[a-z]\w*)+$`.
- `REPO` + site canonical/OG URLs move in the same commit or update-check
  and links break.
- New keystore/alias: reinstall-required; stated in `CHANGELOG.md` + release
  notes.
- Stale visuals until assets regenerated; no release before Section 5 done.

## Verification (no test suite exists)

1. `.\gradlew.bat :patches:build` → BUILD SUCCESSFUL (`:patches:test` is
   NO-SOURCE, expected).
2. `build.sh ig-base.apk --clone` → `build/patch-report.json`: everything
   under `appliedPatches`, `failedPatches: []`.
3. Device smoke on physical ARM hardware (x86_64 emulators unsupported) +
   `logcat -d *:E` clean of `AndroidRuntime`; `Blocked by <Brand>` present.
4. `grep -ri feurstagram` → only `CHANGELOG.md` history + `.git/`;
   old typo-casing `FeurStagram` in URLs → zero (redirects handled by GitHub).
5. AGENTS.md gate: `Readme.md` + `CHANGELOG.md` updated; mirror previous
   feature commit.

## Resolved during implementation (2026-09-25)

1. ~~`Brand` / `brand` / `BRAND` / `Repo` values~~ → **Unhooked** (see Decisions).
2. Clone package suffix → default `com.instagram.android.unhooked` confirmed.
3. Repo move → same owner, renamed repo assumed (`rohandacoder/Unhooked`);
   **GitHub repo must actually be renamed**, or the update check 404s.
4. Contact handles → converted to `unhooked@gmail.com` /
   `@unhooked_official`; **must be claimed** (or those two spots reverted).
5. Icon direction → still open; all binary assets (`app_icon*.png`,
   `preview.png`, `screens.png`, `tuto.gif`, `press/`, `social-preview.png`,
   `.sketch` sources) flagged for regeneration before release.

## Out of scope

- Prefs migration (`feurstagram_prefs` → `<brand>_prefs`).
- Dual update-check against old repo.
- Code refactoring unrelated to the rename.
- Website theme/structural changes beyond brand substitution.
