<div align="center">
  <h1>Unhooked</h1>
  <p><strong>Instagram without Reels, feed, Explore or ads.</strong></p>
  <p>
    An open-source patch for the official Android app that removes the feed,
    Reels, Explore, ads and telemetry — while keeping DMs, stories, search,
    notifications and profiles.
  </p>
  <p>
    <a href="https://gitlab.com/RohanDaCoder/Unhooked/-/releases"><img alt="Latest release" src="https://img.shields.io/gitlab/v/tag/RohanDaCoder/Unhooked?style=flat-square&label=release&color=10a37f"></a>
    <a href="LICENSE"><img alt="License" src="https://img.shields.io/badge/license-GPLv3-6b7280?style=flat-square"></a>
  </p>
  <p>
    <a href="https://gitlab.com/RohanDaCoder/Unhooked/-/releases"><strong>Download APK</strong></a>
    ·
    <a href="docs/INSTALL.md">Install</a>
    ·
    <a href="docs/BUILD_FROM_SOURCE.md">Build from source</a>
    ·
    <a href="docs/FAQ.md">FAQ</a>
    ·
    <a href="docs/PRIVACY.md">Privacy</a>
    ·
    <a href="https://gitlab.com/RohanDaCoder/Unhooked/-/issues">Issues</a>
  </p>
</div>

## What it does

| Keep using Instagram for | Remove or block |
| ------------------------ | --------------- |
| Direct Messages (including Reels shared in DMs) | Endless Home feed |
| Stories | Reels surfaces |
| Search, profiles and deep links | Explore and suggested-account recommendations |
| Notifications | Ads, shopping preloads, and telemetry |

All content blocks are **individual runtime toggles** — long-press the Home
tab at the bottom-left of the main tab bar to open the Unhooked settings page
(a one-time hint points you at it on your first signed-in launch). A single
APK covers every combination.

> Screenshots and the settings tutorial were removed with the old branding —
> fresh ones will be captured from an Unhooked build.

## Features

| Feature | Default | Toggleable | How |
| ------- | ------- | ---------- | --- |
| **Home Feed** | Blocked | Yes | Network-level blocking |
| **Following feed only** | Off | Yes | Feed-request header rewrite |
| **Explore** | Blocked | Yes | Network-level blocking |
| **Reels** | Blocked | Yes | Network-level blocking + Tab hidden |
| **Stories** | Visible | Yes | Network-level blocking |
| **Suggested accounts** | Blocked | Yes | Network-level + feed-parse blocking |
| **Instants (+ button in DMs)** | Blocked | Yes | View visibility hidden |
| **Notes (text bubbles above DMs)** | Blocked | Yes | View visibility hidden |
| **Ads** | Blocked | Yes | Network-level + feed-parse blocking |
| **Analytics & telemetry** | Blocked | No | Always blocked |
| **Shopping / commerce preloads** | Blocked | No | Always blocked |

**Following feed only** is a softer alternative to blocking the Home Feed
entirely: leave the feed unblocked and turn this on to get a chronological feed
of just the accounts you follow, with the recommended/ranked posts dropped.

The settings page also covers the **navigation bar** (show/hide each bottom-bar
icon), **Force dark** (disables Instagram's forced HDR window, on by default),
**Allow screenshots** (off by default), and the cold-start **landing page**.
Changing anything restarts the app via Done or Back so blocks always apply
cleanly; settings persist in SharedPreferences (`unhooked_prefs`).

## Installation

Grab the latest patched APK from the
[Releases](https://gitlab.com/RohanDaCoder/Unhooked/-/releases) page, or
follow the [installation guide](docs/INSTALL.md). Add `-Pclone` at build time
to install Unhooked **alongside** stock Instagram instead of replacing it.

## Updates

Unhooked checks GitLab for a newer release on every launch and shows a
download prompt when your build is out of date. The **automatic update check**
is on by default and can be turned off in the settings page (Updates section).
After an update, the first launch shows a **What's new** card with that
version's release notes.

## Safety

Unhooked does not collect, proxy, store, or transmit your credentials. It does
not bypass login, add tracking, or scrape accounts — the patches are open
source and documented, so you can inspect what is changed. For maximum trust,
[build the APK yourself](docs/BUILD_FROM_SOURCE.md).

That said, Unhooked is unofficial and unaffiliated with Instagram or Meta. It
installs outside the Play Store, so Android may show installation warnings,
and using a modified client may violate Instagram's terms of service. Use it at
your own risk.

## Contact

- Discord: `rohan_ohio`
- Email: [alzamirohan@proton.me](mailto:alzamirohan@proton.me)

## For developers

<details>
<summary><strong>Build from source</strong></summary>

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
[docs/BUILD_FROM_SOURCE.md](docs/BUILD_FROM_SOURCE.md) for CI variables and
base qualification.

</details>

<details>
<summary><strong>Project structure</strong></summary>

```
Unhooked/
├── build.gradle.kts              # Full pipeline (merge → patch → sign → unhooked.apk)
├── patches/                      # Patches (Kotlin): where to inject, by fingerprint
│   └── src/main/kotlin/com/unhooked/patches/
│       ├── network/              # Network blocking + feed-item filter + following-feed
│       ├── settings/             # Long-press settings entry point (tab bar)
│       ├── restart/              # Restart relay (relaunch after a settings change)
│       ├── shared/               # Fingerprint helpers shared by patches
│       ├── signature/            # Signing-cert trust bypass (deep links)
│       ├── display/              # Force SDR (disable Instagram's forced HDR window)
│       ├── screenshot/           # Screenshot bypass (strip FLAG_SECURE, gated on toggle)
│       ├── ui/                   # Popup hiding ("Couldn't refresh feed" noise)
│       ├── update/               # Install-packages permission (in-app update)
│       ├── debug/                # ADB settings bridge (dev only, -Pdebug)
│       └── clone/                # Side-by-side package/label rename
└── extensions/                   # Runtime code (Java), compiled and merged in
    └── .../com/unhooked/extension/
        ├── Block.java            # URI blocking rules + feed-item type filter
        ├── LimitFeed.java        # Following-feed header rewrite
        ├── Config.java           # SharedPreferences toggles
        ├── Settings.java         # Settings page
        ├── HomeTabWatcher.java   # Tab-bar watcher (long-press entry + hiders + redirect)
        ├── Onboarding.java       # One-time long-press hint on first signed-in launch
        ├── Hiders.java           # Nav-tab / Notes / Instants hiders + landing redirect
        ├── HiddenTabSwipeSkipper.java  # Keeps swipes off hidden tabs' pages
        ├── Display.java          # Window colour-mode control (Force dark / SDR)
        ├── Screenshots.java      # Window flag control (Allow screenshots bypass)
        ├── CacheCleaner.java     # Cache wipe + relaunch after a settings change
        ├── RestartActivity.java  # One-shot relay that performs the relaunch
        ├── Toasts.java           # Popup suppression for blocked-surface noise
        ├── DebugBridge.java      # Dev-only ADB bridge over the settings (-Pdebug builds)
        └── UpdateChecker.java    # On-launch GitLab release check
```

Unhooked's own screens follow Material 3 Expressive in a monochrome dark
scheme — see [docs/DESIGN.md](docs/DESIGN.md) before changing any UI.

</details>

<details>
<summary><strong>How it works</strong></summary>

Patches locate their injection points by **fingerprint** (matching code by
stable traits like class names, strings and method shapes) rather than by
obfuscated names, so they keep applying across Instagram updates. The runtime
logic lives in compiled extension classes that are merged into the app.

**Settings hook.** A fingerprint matches the main tab-bar binder and injects a
watcher on it. The watcher resolves the `feed_tab` id via
`Resources.getIdentifier(...)`, grabs the Home tab once laid out, and installs
a long-press listener that opens the settings page. The same hook installs the
navigation-tab/Notes/Instants hiders, the hidden-tab swipe skipper and the
cold-start landing-page redirect.

**Network blocking.** A fingerprint matches `TigonServiceLayer.startRequest`
and injects a call on the request URI. Blocked calls throw an `IOException`, so
the surface fails to load and stays empty. Blocked requests surface as
`java.io.IOException: Blocked by Unhooked` in logcat (`adb logcat | grep
"Blocked by Unhooked"`).

**Feed item filtering.** Ads and "suggested" units arrive inline inside the
normal `/feed/timeline/` response, so URL blocking can't touch them. A second
fingerprint matches the feed-item JSON deserialiser (by wire-format type tokens
like `clips_netego` and the `parseFromJson` name) and rewrites a blocked unit's
type token to an invalid one, which Instagram's own parser drops — no crash.

**Following feed only.** A fingerprint matches the main-feed request class and
rewrites its `pagination_source` header from the recommended feed to
`following`. The header field is obfuscated, so it is resolved dynamically each
build.

**Deep-link signature bypass.** Instagram checks the APK is Meta-signed before
following deep links into its own content; a patch forces those trust checks to
pass (matched on a stable error string, no per-version maintenance).

**Force SDR display.** Instagram switches its window into HDR mode at runtime,
lifting the black floor on HDR OLED panels. A patch reroutes window
colour-mode changes through a helper that pins SDR while **Force dark** is on
(hooks framework `Window.setColorMode`, no per-version maintenance).

Blocked network paths (matched with `String.contains()` on the URI path —
adjust `extensions/.../Block.java` if Instagram changes URL shapes):

| Path / pattern | Purpose | Toggleable |
| -------------- | ------- | ---------- |
| `/feed/timeline/` | Home feed posts | Yes |
| `/feed/reels_tray` | Stories tray | Yes |
| `/discover/topical_explore` | Explore tab content | Yes |
| `/clips/home/`, `/clips/discover`, `/clips/get_blend_medias/` | Reels feed + discovery + Blend reels | Yes |
| `/discover/ayml/`, `/discover/sectioned_ayml/`, `/discover/chaining/`, `/discover/recommended_accounts_for_category/`, `/discover/suggested_businesses/`, `/discover/recs_from_friends_suggestions/`, `/discover/recs_from_friends_user_info/`, `/discover/surface_with_su/`, `/discover/fetch_suggestion_details/`, `/discover/account_discovery/`, `/discover/reshare_suggestions/`, `/fbsearch/accounts_recs/`, `/friendships/feed_favorites_suggestions/`, `/friendships/share_to_friends_story_suggested_users/`, `/direct_v2/search_friending_suggestions/`, `/business/discovery/suggest_business/` | Suggested-account recommendations | Yes |
| `/api/v1/ads/` (all `ads/*`), `/feed/async_ads_ranking/`, `/feed/shop_everything_feed_of_ads*`, `/feed/user_interests_contextual_feed_of_ads/`, `/discover/chaining_experience_contextual_ads/`, `/discover/chaining_experience_notification_ads/`, `/direct_v2/ads_for_ctd_ads_thread_view/`, `/direct_v2/should_show_ad_responses_tab/`, `/profile_ads/get_profile_ads/`, `/stories/stories_high_intent_discovery_ads/`, `/stories/stories_intent_aware_ads/`, `/commerce/product_collections/ads_collection_page/` | Sponsored ads | Yes |
| `/logging/` | Client event logging | No |
| `/async_ads_privacy/` | Ad-related tracking | No |
| `/async_critical_notices/` | Engagement nudge analytics | No |
| `/api/v1/media/.../seen/` (path contains `/api/v1/media/` and `/seen`) | Post “seen” tracking | No |
| `/api/v1/fbupload/` | Telemetry upload | No |
| `/api/v1/stats/` | Performance / usage stats | No |
| `/api/v1/commerce/`, `/api/v1/shopping/`, `/api/v1/sellable_items/` | Shopping / commerce preloads | No |

Note: despite the name, `/feed/reels_tray` is the stories tray endpoint in
Instagram internals.

**New Instagram versions** usually just need a rebuild
(`./gradlew buildApk "-Papk=<new-instagram.apk>"`) — only restructured targets
need fingerprint adjustments.

</details>

<details>
<summary><strong>Signing</strong></summary>

The bundle is signed during `buildApk`. Set `UNHOOKED_KEYSTORE_PASS` (and
optionally `UNHOOKED_KEY_PASS`) to sign with `unhooked.keystore`; otherwise a
throwaway keystore is generated. Reuse the same keystore across builds to
install updates without uninstalling first. Secret values may live in a local
`.env` file instead of the environment (see `.env.example`); environment
variables take precedence.

</details>

## Contributing

Fork it, open a pull request, or copy the code — contributions welcome. See
[CONTRIBUTING.md](CONTRIBUTING.md) and [SECURITY.md](SECURITY.md).

## License

Released under the GNU General Public License v3.0 — see [LICENSE](LICENSE).
Built with the [Morphe](https://morphe.software) patcher and adapting patch
ideas from [Piko](https://github.com/crimera/piko); see [NOTICE](NOTICE).

*Unhooked is a fork of [Feurstagram](https://github.com/jean-voila/Feurstagram),
independently maintained and unaffiliated with Instagram or Meta.*
