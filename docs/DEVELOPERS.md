# Developers — building Unhooked

Requirements: JDK 21, Android SDK (`ANDROID_HOME` set), and a GitHub token
with `read:packages` in `~/.gradle/gradle.properties` (fetches the Morphe
patcher dependency):

```properties
gpr.user=<your-github-username>
gpr.key=<token-with-read:packages>
```

Download the pinned Instagram base from this project's Package Registry
(arm64-v8a, qualified per release):

```bash
glab packages download --name unhooked-base --version 446.0.0.49.77 --filename base-446.xapk
```

Then build:

```bash
./gradlew buildApk "-Papk=base-446.xapk"        # classic
./gradlew buildApk "-Papk=base-446.xapk" -Pclone # side-by-side
./gradlew installApk "-Papk=base-446.xapk" -Pclone -Pinstall # + deploy
```

The signed result is written to `unhooked.apk`. Set `INPUT_APK` in a local
`.env` file (see `.env.example`) to skip `-Papk` entirely. See
[BUILD_FROM_SOURCE.md](BUILD_FROM_SOURCE.md) for CI variables and base
qualification.

## Signing

The bundle is signed during `buildApk`. Set `UNHOOKED_KEYSTORE_PASS` (and
optionally `UNHOOKED_KEY_PASS`) to sign with `unhooked.keystore`; otherwise a
throwaway keystore is generated. Reuse the same keystore across builds to
install updates without uninstalling first. Secret values may live in a local
`.env` file instead of the environment (see `.env.example`); environment
variables take precedence.

## Debugging

Blocked requests surface as `java.io.IOException: Blocked by Unhooked` in
logcat:

```bash
adb logcat | grep "Blocked by Unhooked"
```

For endpoint research (e.g. a block that applies cleanly but never fires),
build with `-Pdebug` and use the `DebugBridge` TRACE broadcast to log every
request path — see [ARCHITECTURE.md](ARCHITECTURE.md).
