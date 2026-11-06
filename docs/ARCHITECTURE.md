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
│       ├── quoicouview/          # Quoicouview mode + its screenshot asset
│       ├── update/               # Install-packages permission (in-app update)
│       ├── debug/                # ADB settings bridge (dev only, -Pdebug)
│       └── clone/                # Side-by-side package/label rename
└── extensions/                   # Runtime code (Java), compiled and merged in
    └── .../com/unhooked/extension/
        ├── Block.java            # URI blocking rules + feed-item type filter
        ├── LimitFeed.java        # Following-feed header rewrite
        ├── Config.java           # SharedPreferences toggles
        ├── Settings.java         # Settings UI: overview, section pages, dialogs
        ├── ExpressiveSwitch.java # Resource-free Material 3 switch, drawn in code
        ├── HomeTabWatcher.java   # Tab-bar watcher (long-press entry + hiders + redirect)
        ├── Onboarding.java       # One-time long-press hint on first signed-in launch
        ├── Hiders.java           # Nav-tab (incl. Home) / Notes / Instants hiders,
        │                         #   landing redirect, settings-tab pick
        ├── HiddenTabSwipeSkipper.java  # Keeps swipes off hidden tabs' pages
        ├── Quoicouview.java      # Quoicouview mode: inbox as the main page,
        │                         #   stories tray adopted as a row of its list
        ├── QuoicouviewIntro.java # One-time card presenting Quoicouview mode
        ├── StoriesTrayRow.java   # The stories tray as a recyclable inbox row
        ├── Display.java          # Window colour-mode control (Force dark / SDR)
        ├── Screenshots.java      # Window flag control (Allow screenshots bypass)
        ├── CacheCleaner.java     # Cache wipe + relaunch after a settings change
        ├── RestartActivity.java  # One-shot relay that performs the relaunch
        ├── Toasts.java           # Popup suppression for blocked-surface noise
        ├── DebugBridge.java      # Dev-only ADB bridge over the settings (-Pdebug builds)
        └── UpdateChecker.java    # On-launch GitLab release check
```

## UI rules

Unhooked's own screens (settings, update flow, onboarding cards) follow
**Material 3 Expressive in a monochrome dark scheme** — white is the only
accent, emphasis comes from weight and space rather than colour. Two hard
rules follow from being merged into Instagram's APK by a patcher:

- **No bundled resources.** There is no `res/` of its own: every drawable is
  a `GradientDrawable` built at runtime, every layout written in code. Icons
  are borrowed from Instagram's own set by drawable name.
- **One place for tokens.** Palette, type scale, corner radii and the
  page/dialog scaffolding live in `Settings.java`; other classes draw from it
  and add nothing of their own.

Settings rows are grouped as connected lists (rounded outer corners, small
gap between rows, no dividers), every root goes through
`Settings.applyPageInsets(...)` for edge-to-edge insets, and long content
scrolls instead of pushing the action row off screen.

## Patch internals

**Settings hook.** A fingerprint matches the main tab-bar binder and injects a
watcher on it. The watcher resolves the `feed_tab` id via
`Resources.getIdentifier(...)`, grabs the Home tab once laid out, and installs
a long-press listener that opens the settings page (when Home is hidden, the
leftmost visible tab carries the gesture). The same hook installs the
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

**Quoicouview mode.** The inbox is an `IgRecyclerViewAdapter`-style list built
from row types. The patch registers one more row type — `StoriesTrayRow` — when
the adapter is built, and every list update places the stories row right after
the search bar's row. The row shows Instagram's own stories tray (the feed's
`reels_tray_container`, kept alive off screen in the main pager), so it scrolls
and recycles like any inbox row and keeps its live content. Story taps go
through the feed's tray controller; the patch answers the controller's
"is the feed visible" checks for the tray and forwards the inbox's resume and
pause to the controller, which is normally a lifecycle listener of the feed
fragment. The notifications button moves into the inbox header, and a screenshot
for the one-time intro card is added to the APK by the Quoicouview assets
patch.

## Debug bridge

`DebugBridge.java` is a **development-only remote control over the settings**,
compiled into the APK only when the Debug bridge patch runs (`-Pdebug` builds;
a release build carries the class but never registers anything). It installs an
exported `BroadcastReceiver` on the application context, so every command is
one `adb shell am broadcast` whose reply prints straight back to stdout — no
logcat scraping, no synthetic taps:

```sh
adb shell am broadcast -p com.instagram.android -a com.unhooked.debug.DUMP     # all settings
adb shell am broadcast -p com.instagram.android -a com.unhooked.debug.SET \
    --es key block_ads --ez value true                                          # set a toggle
adb shell am broadcast -p com.instagram.android -a com.unhooked.debug.NAV       # tab bar + pager map
adb shell am broadcast -p com.instagram.android -a com.unhooked.debug.TRACE --ez value true
adb shell am broadcast -p com.instagram.android -a com.unhooked.debug.RESTART
```

The full action set is `PING, SET, GET, DUMP, NAV, TRACE, UPDATE, WHATSNEW,
RESET, RESTART, SETTINGS`. `TRACE` is the main endpoint-research tool: it makes
`Block` log every request URI it sees (`logcat -s FeurNet:I`), which is how a
block that applies cleanly but never fires gets its new address found.
Debug builds also report a spoofed installed version (`0.0.0`) so the whole
in-app update flow can be exercised against the live GitLab release. The
receiver is dynamic — the app must be running for commands to land.

## Blocked network paths

Matched with `String.contains()` on the URI path — adjust
`extensions/.../Block.java` if Instagram changes URL shapes:

| Path / pattern                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                             | Purpose                              | Toggleable |
| -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------ | ---------- |
| `/feed/timeline/`                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                          | Home feed posts                      | Yes        |
| `/feed/reels_tray`                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                         | Stories tray                         | Yes        |
| `/discover/topical_explore`                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                | Explore tab content                  | Yes        |
| `/clips/home/`, `/clips/discover`, `/clips/get_blend_medias/`                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                              | Reels feed + discovery + Blend reels | Yes        |
| `/discover/ayml/`, `/discover/sectioned_ayml/`, `/discover/chaining/`, `/discover/recommended_accounts_for_category/`, `/discover/suggested_businesses/`, `/discover/recs_from_friends_suggestions/`, `/discover/recs_from_friends_user_info/`, `/discover/surface_with_su/`, `/discover/fetch_suggestion_details/`, `/discover/account_discovery/`, `/discover/reshare_suggestions/`, `/fbsearch/accounts_recs/`, `/friendships/feed_favorites_suggestions/`, `/friendships/share_to_friends_story_suggested_users/`, `/direct_v2/search_friending_suggestions/`, `/business/discovery/suggest_business/` | Suggested-account recommendations    | Yes        |
| `/api/v1/ads/` (all `ads/*`), `/feed/async_ads_ranking/`, `/feed/shop_everything_feed_of_ads*`, `/feed/user_interests_contextual_feed_of_ads/`, `/discover/chaining_experience_contextual_ads/`, `/discover/chaining_experience_notification_ads/`, `/direct_v2/ads_for_ctd_ads_thread_view/`, `/direct_v2/should_show_ad_responses_tab/`, `/profile_ads/get_profile_ads/`, `/stories/stories_high_intent_discovery_ads/`, `/stories/stories_intent_aware_ads/`, `/commerce/product_collections/ads_collection_page/`                                                                                      | Sponsored ads                        | Yes        |
| `/logging/`                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                | Client event logging                 | No         |
| `/async_ads_privacy/`                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                      | Ad-related tracking                  | No         |
| `/async_critical_notices/`                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                 | Engagement nudge analytics           | No         |
| `/api/v1/media/.../seen/` (path contains `/api/v1/media/` and `/seen`)                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                     | Post "seen" tracking                 | No         |
| `/api/v1/fbupload/`                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                        | Telemetry upload                     | No         |
| `/api/v1/stats/`                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                           | Performance / usage stats            | No         |
| `/api/v1/commerce/`, `/api/v1/shopping/`, `/api/v1/sellable_items/`                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                        | Shopping / commerce preloads         | No         |

Note: despite the name, `/feed/reels_tray` is the stories tray endpoint in
Instagram internals.

## New Instagram versions

A new Instagram version usually needs only a rebuild against the new base —
only restructured targets need fingerprint adjustments. The stock base is
self-hosted in this project's Package Registry as
`unhooked-base/<ig-version>/base-<major>.apkm`; see
[BUILD_FROM_SOURCE.md](BUILD_FROM_SOURCE.md) for acquiring a base and
[AGENTS.md](../AGENTS.md) for the release flow.
