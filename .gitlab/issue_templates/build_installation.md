<!-- GitLab issue template: Build or installation help.
     Applied automatically when the reporter picks this template. -->

/label ~"help wanted" ~"needs triage"

Use this template for `buildApk` failures, APK installation problems, clone/classic install conflicts, Android warnings, APKM/bundle questions, or device architecture compatibility.

## Problem type

<!-- buildApk failed / APK does not install / unknown-sources warning / clone conflict / classic conflict / APKM question / architecture compatibility / other -->

## Environment

- **Unhooked version or commit:** <!-- e.g. v446-0-0-49-77 or commit hash -->
- **Instagram APK details:** <!-- version, source, minAPI, DPI, architecture — e.g. 446.0.0.49.77, Package Registry base, minAPI 28, arm64-v8a, nodpi -->
- **Computer OS:** <!-- e.g. Windows 11, Ubuntu 24.04 -->
- **Android device and version:** <!-- e.g. Pixel 7, Android 14 -->
- **APK type:** <!-- classic / clone / built from source / not sure -->
- **Architecture:** <!-- arm64-v8a / 32-bit / x86_64 / universal / not sure -->

## Command or install step

<!-- Paste the exact command you ran or describe the install step that failed. -->

```sh
./gradlew buildApk -Pclone
```

## Full error output

<!-- Paste the complete terminal output, Android install error, or screenshot text. -->

```text

```

## What have you tried?

<!-- Another APK version, clone vs classic, reinstall, fresh `.env`, etc. -->
