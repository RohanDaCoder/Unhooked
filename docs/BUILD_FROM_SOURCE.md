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
- A supported Instagram APK (arm64-v8a from a trusted source such as APKMirror)

## Steps

1. Clone the repository.

   ```sh
   git clone https://gitlab.com/RohanDaCoder/Unhooked.git
   cd Unhooked
   ```

2. Build and apply the patches to your Instagram APK.

   ```sh
   ./gradlew buildApk "-Papk=instagram.apk"
   ```

   Add `-Pclone` to install alongside a stock Instagram, and run `installApk`
   with `-Pinstall` to push to a connected device:

   ```sh
   ./gradlew installApk "-Papk=instagram.apk" -Pclone -Pinstall
   ```

3. Install the signed result (if you did not run `installApk -Pinstall`).
   ```sh
   adb install -r unhooked.apk
   ```
   Set `INPUT_APK` in a local `.env` file (see `.env.example`) to skip
   `-Papk` entirely.

## Signing

The bundle is signed during the build. Set `UNHOOKED_KEYSTORE_PASS` (and
optionally `UNHOOKED_KEY_PASS`) to sign with `unhooked.keystore`;
otherwise a throwaway keystore is generated. Do not commit your keystore, and
keep the same one to reinstall without losing app data.

## CI (GitLab, main branch only)

Pushing to `main` builds both flavors — `unhooked-classic.apk` and
`unhooked-clone.apk` — as job artifacts (`.gitlab-ci.yml`). Configure these
CI/CD variables first (Settings → CI/CD → Variables):

- `INPUT_APK_URL` — URL of the stock arm64-v8a Instagram APK/APKM.
- `GITHUB_ACTOR` / `GITHUB_TOKEN` — GitHub user + token with
  `read:packages` for the Morphe registry.
- Optional release signing: `UNHOOKED_KEYSTORE_B64` (File variable holding
  the base64 keystore), `UNHOOKED_KEYSTORE_PASS`, `UNHOOKED_KEY_ALIAS`.
  Without them the artifacts are throwaway-signed.

## Notes

- Only use APKs from trusted sources.
- Patches are fingerprint-based, so a new Instagram version usually only needs a
  rebuild.
- If the build fails, check open issues or create a bug report.
