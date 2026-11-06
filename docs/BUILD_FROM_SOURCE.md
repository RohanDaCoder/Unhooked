# Build from source

This guide explains how to build Unhooked yourself. Building from source lets
you inspect the patches and avoid trusting a prebuilt APK.

## Requirements

- JDK 21 and the Android SDK (`ANDROID_HOME` set, build-tools installed)
- A GitHub token with the `read:packages` scope in `~/.gradle/gradle.properties`
  (used to fetch the patcher dependency):
  ```properties
  gpr.user=<your-github-username>
  gpr.key=<token-with-read:packages>
  ```
- The stock Instagram base the release pins (an `.apkm` split bundle,
  arm64-v8a). Two ways to get it:

  ```sh
  # easiest: downloads the pinned version from this project's Package Registry
  ./gradlew fetchBase                       # -PbaseVersion=<version> to override

  # or directly with glab
  glab packages download --name unhooked-base --version 454.0.0.13.77 \
      --filename base-454.apkm
  ```

  Any other Instagram APK or split bundle (`.apk`, `.apkm`, `.xapk`, `.apks`)
  works too — bundle formats are routed by file extension.

## Steps

1. Clone the repository.

   ```sh
   git clone https://gitlab.com/RohanDaCoder/Unhooked.git
   cd Unhooked
   ```

2. Build and apply the patches to the base.

   ```sh
   ./gradlew buildApk "-Papk=base-454.apkm"
   ```

   Add `-Pclone` to install alongside a stock Instagram, and run `installApk`
   with `-Pinstall` to push to a connected device:

   ```sh
   ./gradlew installApk "-Papk=base-454.apkm" -Pclone -Pinstall
   ```

3. Install the signed result (if you did not run `installApk -Pinstall`).
   ```sh
   adb install -r unhooked.apk
   ```
   Set `INPUT_APK` in a local `.env` file (see `.env.example`) to skip
   `-Papk` entirely.

## Verifying the result

`./gradlew buildApk` prints the patch report and writes it to
`build/patch-report.json`. Check that everything is under `appliedPatches`,
that `failedPatches` is empty, and that the applied names match
`.gitlab/expected-patches.txt` (the clone flavor applies the same list plus
the `Clone` patch). A `BUILD SUCCESSFUL` line alone says nothing about whether
patches actually applied.

On device, blocked requests surface as `java.io.IOException: Blocked by
Unhooked` in logcat:

```sh
adb logcat | grep "Blocked by Unhooked"
```

For endpoint research — e.g. a block that applies cleanly but never fires —
build with `-Pdebug` and use the Debug bridge's TRACE command; see
[ARCHITECTURE.md](ARCHITECTURE.md#debug-bridge). Debug builds are for
development only and must not be distributed.

## Signing

The bundle is signed during the build. Set `UNHOOKED_KEYSTORE_PASS` (and
optionally `UNHOOKED_KEY_PASS`) to sign with `unhooked.keystore`;
otherwise a throwaway keystore is generated. Do not commit your keystore, and
keep the same one to reinstall without losing app data.

## CI (GitLab, main branch only)

Merging `dev` into `main` is the release act. The pipeline (`.gitlab-ci.yml`)
has three jobs: `build-classic` + `build-clone` in parallel, then
`publish-release`.

Each build job starts with preflight (validates the stock bundle and the
required variables, failing fast), builds its flavor APK, then gates on
`failedPatches == []` **and** a diff of the applied list against
`.gitlab/expected-patches.txt` (the clone job expects the same list plus
`Clone`) — a silently missing patch fails the job. It finishes by verifying
the signature and asserting the package identity
(`com.instagram.android` vs `com.instagram.android.unhooked`).

`publish-release` runs only when no `v<VERSION>` release exists yet
(re-merges without a bump just build). It first pins both APKs to the release
cert when `RELEASE_CERT_SHA256` is set, then uploads both APKs to Generic
Packages and creates the GitLab Release with them as assets, which is what
the in-app updater reads. It hard-fails without the release key.

Configure these CI/CD variables (Settings → CI/CD → Variables):

- `GITHUB_ACTOR` / `GITHUB_TOKEN` — GitHub user + token with
  `read:packages` for the Morphe registry.
- `GITLAB_API_TOKEN` — project access token with `api` scope (creates the
  release; `JOB-TOKEN` cannot mint tags).
- `INPUT_APK_URL` — stock Instagram bundle (defaults in-YAML to the
  self-hosted base in this project's Package Registry,
  `unhooked-base/<ig-version>/base-<major>.apkm`; to qualify a new base,
  upload it there with `glab packages upload base-<major>.apkm --name
  unhooked-base --version <ig-version>` and update the default).
- Release signing: `UNHOOKED_KEYSTORE_B64` (plain variable with the base64
  keystore text), `UNHOOKED_KEYSTORE_PASS`, `UNHOOKED_KEY_ALIAS`.
- `RELEASE_CERT_SHA256` — expected cert fingerprint from
  `apksigner verify --print-certs` on a release-signed APK (optional pin).

## Notes

- Only use APKs from trusted sources.
- Patches are fingerprint-based, so a new Instagram version usually only needs a
  rebuild; the release flow for migrating to a new base is documented in
  [AGENTS.md](../AGENTS.md).
- If the build fails, check open issues or create a bug report.
