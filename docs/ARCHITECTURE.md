# Architecture — how Unhooked works

Patches locate their injection points by **fingerprint** (matching code by
stable traits like class names, strings and method shapes) rather than by
obfuscated names, so they keep applying across Instagram updates. The runtime
logic lives in compiled extension classes that are merged into the app.

## Project structure

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
scheme — see [DESIGN.md](DESIGN.md) before changing any UI.

## Patch internals

**Settings hook.** A fingerprint matches the main tab-bar binder and injects a
watcher on it. The watcher resolves the `feed_tab` id via
`Resources.getIdentifier(...)`, grabs the Home tab once laid out, and installs
a long-press listener that opens the settings page. The same hook installs the
navigation-tab/Notes/Instants hiders, the hidden-tab swipe skipper and the
cold-start landing-page redirect.

**Network blocking.** A fingerprint matches `TigonServiceLayer.startRequest`
and injects a call on the request URI. Blocked calls throw an `IOException`, so
the surface fails to load and stays empty.

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

## Blocked network paths

Matched with `String.contains()` on the URI path — adjust
`extensions/.../Block.java` if Instagram changes URL shapes:

| Path / pattern                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                             | Purpose                              | Toggleable |
| ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------ | ---------- |
| `/feed/timeline/`                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                          | Home feed posts                      | Yes        |
| `/feed/reels_tray`                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                         | Stories tray                         | Yes        |
| `/discover/topical_explore`                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                | Explore tab content                  | Yes        |
| `/clips/home/`, `/clips/discover`, `/clips/get_blend_medias/`                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                              | Reels feed + discovery + Blend reels | Yes        |
| `/discover/ayml/`, `/discover/sectioned_ayml/`, `/discover/chaining/`, `/discover/recommended_accounts_for_category/`, `/discover/suggested_businesses/`, `/discover/recs_from_friends_suggestions/`, `/discover/recs_from_friends_user_info/`, `/discover/surface_with_su/`, `/discover/fetch_suggestion_details/`, `/discover/account_discovery/`, `/discover/reshare_suggestions/`, `/fbsearch/accounts_recs/`, `/friendships/feed_favorites_suggestions/`, `/friendships/share_to_friends_story_suggested_users/`, `/direct_v2/search_friending_suggestions/`, `/business/discovery/suggest_business/` | Suggested-account recommendations    | Yes        |
| `/api/v1/ads/` (all `ads/*`), `/feed/async_ads_ranking/`, `/feed/shop_everything_feed_of_ads*`, `/feed/user_interests_contextual_feed_of_ads/`, `/discover/chaining_experience_contextual_ads/`, `/discover/chaining_experience_notification_ads/`, `/direct_v2/ads_for_ctd_ads_thread_view/`, `/direct_v2/should_show_ad_responses_tab/`, `/profile_ads/get_profile_ads/`, `/stories/stories_high_intent_discovery_ads/`, `/stories/stories_intent_aware_ads/`, `/commerce/product_collections/ads_collection_page/`                                                                                      | Sponsored ads                        | Yes        |
| `/logging/`                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                | Client event logging                 | No         |
| `/async_ads_privacy/`                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                      | Ad-related tracking                  | No         |
| `/async_critical_notices/`                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                 | Engagement nudge analytics           | No         |
| `/api/v1/media/.../seen/` (path contains `/api/v1/media/` and `/seen`)                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                     | Post “seen” tracking                 | No         |
| `/api/v1/fbupload/`                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                        | Telemetry upload                     | No         |
| `/api/v1/stats/`                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                           | Performance / usage stats            | No         |
| `/api/v1/commerce/`, `/api/v1/shopping/`, `/api/v1/sellable_items/`                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                        | Shopping / commerce preloads         | No         |

Note: despite the name, `/feed/reels_tray` is the stories tray endpoint in
Instagram internals.

## New Instagram versions

Usually just need a rebuild (`./gradlew buildApk "-Papk=<new-instagram.apk>"`)
— only restructured targets need fingerprint adjustments. See
[DEVELOPERS.md](DEVELOPERS.md) for the build itself.
