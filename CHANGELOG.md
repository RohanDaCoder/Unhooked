# Changelog

All notable changes to Unhooked are documented in this file.
Entries under Unreleased ship with the next version bump.

## Unreleased

### Added

- **Quoicouview mode** (ported from upstream; experimental, off by default,
  `quoicouview_mode`). Messages becomes the app's main page with the stories
  tray on top of the inbox — under its search bar — and Home is turned off
  (the Home and Messages toggles and the landing page are greyed out while it
  is on). The tray is Instagram's own, adopted as a row of the inbox list, so
  it scrolls, recycles and keeps its live content; stories open from there
  the way they do from Home, the viewer stacked over the inbox; the
  notifications button moves to the inbox header; and the story camera opens
  from the tray's "Your story" (+) or with a swipe to the right, as from Home.
  A one-time card presents the mode after this update, with a screenshot the
  *Quoicouview assets* patch adds to the APK. The switch is the first row of
  the settings overview, under "Experimental feature"; it is unavailable when
  the permanent lock froze Messages hidden.
- **Permanent lock** (ported from upstream). The settings overview gains a
  *Permanent lock* pill next to Done: once enabled, every surface hidden at
  that moment stays hidden for the installation — only a reinstall unlocks,
  while tightening blocks stays possible. The lock freezes the content blocks
  and the navigation-bar icons, and the landing picker can no longer hand
  back a frozen surface.
- **Settings open on an overview of sections** (ported from upstream). One row
  per area (Blocked surfaces, Navigation bar, Feed, Landing page, Display and
  popups, Privacy, Updates, Support), each with Instagram's own outline icon,
  a one-line summary of its state and a chevron; tapping swaps the column for
  that section's page in the same window, with a back arrow and the pinned
  action row kept, and Back returns to the overview. The Navigation bar page
  previews the bar in its live order, redrawn as the switches move, and greys
  out toggles for tabs the running Instagram build does not have. Unhooked's
  Privacy page keeps the Allow screenshots toggle.
- **Material 3 Expressive switches** (ported from upstream). Settings rows now
  use a switch drawn in code — a squircle track whose corners tighten as it
  fills, a thumb that grows into a rounded square when pressed — with
  selected, pressed and disabled states.
- **Hide the Home icon** (ported from upstream issue #127). A Home row joins
  the Navigation bar section. Long-pressing a tab is the only way into these
  settings, so Home only goes once another icon stays on the bar to take the
  gesture over — hiding every icon keeps Home. With Home hidden, Back from the
  first tab shown leaves the app instead of walking onto the hidden feed, and
  the cold-start landing opens the first landable tab.
- **Suggested accounts are dropped from the story tray** (ported from
  upstream). Instagram mixes suggestions into the home story tray as ordinary
  `reel_type: suggested_user` entries of the tray response itself, so the
  network block on `/discover/…` never saw them; they are now filtered at
  parse time, on the Suggested accounts toggle.

### Changed

- **The pinned stock base is now Instagram 454.0.0.13.77** (was 446.0.0.49.77).
  `fetchBase`, CI's `INPUT_APK_URL` and the registry pin all moved; upload the
  `base-454.apkm` bundle to the package registry before the next CI run.
- **Every settings toggle now says what it does.** The descriptions under the
  toggles were rewritten in plain words, and each one is specific to its own
  switch: seven of the ten "Blocked surfaces" rows shared a single line reading
  "Hide this surface in Instagram." The navigation-bar rows each name their own
  icon. Descriptions now state what you will actually see — that the Home tab
  stays but loads nothing, that hiding the Reels *icon* is a separate setting
  under Navigation bar, that Stories you already opened survive until a restart
  — instead of how the patch works internally. "Force dark" drops the
  "(disable HDR)" suffix, "Friends in Reels" is now "Friends row in Reels", and
  "Notifications button" is now "Heart button".

### Fixed

- **Ad blocking works on current Instagram again** (ported from upstream).
  Sponsored posts reach the feed wrapped in the same `media_or_ad` unit as
  organic ones, so the unit type cannot tell them apart; the media's own JSON
  fields (`injected`, `dr_ad_type`) now can. Also adapts the sponsored-media
  hooks to Instagram 452–454 (the parser moved into a codec, then back inline;
  a null check now sits between the field-name load and its hash dispatch) and
  the network block to 451's `startRequest` becoming a Systrace wrapper.
- **The scroll fade on the settings and update pages is flush with the action
  row** (ported from upstream): the fade no longer floats above the buttons.
- **A toggle with no description now shows none, instead of a vague one.** The
  generic catch-all line is gone, so a switch added later without a description
  of its own renders with only its label rather than silently claiming to hide
  something unspecified.

### Docs

- The feature table documents the **Friends row in Reels**, **Heart button** and
  **Instagram popups** toggles, which shipped without ever being listed.

## 1.0.3

### Added

- **Release notes ship in the update prompt.** The GitLab release description
  is now cut from this changelog, so "Update available" and "What's new"
  show these notes instead of a fixed template. A release without a matching
  section fails CI rather than shipping empty notes.
- **`fetchBase` Gradle task.** `./gradlew fetchBase` downloads the pinned
  stock Instagram base from this project's Package Registry
  (`-PbaseVersion=<v>` overrides the pin); second runs are a cache hit.
- **Shared IntelliJ run configurations** (`.idea/runConfigurations/`):
  Setup Tools, Build Classic/Clone, Install Clone, Compile Patches.

### Changed

- **CI builds both flavors in parallel.** `build-apks` split into
  `build-classic` + `build-clone` on separate runners; per-flavor gates stay
  in-job, the cross-flavor release-cert pin moved to `publish-release`.
- **Docs restructured.** The Readme is user-only with a fork credit;
  developer content moved 1:1 into `docs/DEVELOPERS.md` (build/sign/debug)
  and `docs/ARCHITECTURE.md` (internals). The inherited upstream website
  and all Feurstagram-branded images were removed; fresh screenshots are
  still to be captured from an Unhooked build.

## 1.0.0

First Unhooked release, on Instagram 446.0.0.49.77. Clean break from
Feurstagram: reinstall required, previous settings do not migrate.

### Changed

- **Rebrand: Feurstagram is now Unhooked.** Code namespaces
  (`com.unhooked.*`), clone package (`com.instagram.android.unhooked`),
  prefs (`unhooked_prefs`), build output (`unhooked.apk`), and all
  docs strings renamed.
- **Build moved into Gradle.** `build.sh` is deleted; the pipeline is now
  `./gradlew buildApk "-Papk=<...>"` (`-Pclone`, `-Pinstall`,
  `-Pdebug`), with the patch receipt still in `build/patch-report.json`.
- **Home is now GitLab (`RohanDaCoder/Unhooked`).** The in-app updater reads
  the GitLab Releases API; release assets keep the classic/clone APK pair
  (`-clone.apk` for the clone build). GitHub issue forms are replaced by
  `.gitlab` templates; the Morphe patcher dependency still comes from
  GitHub Packages.
- **Own versioning.** Releases are `v<semver>` from `gradle.properties`,
  stamped into the APK at build time. The updater and "What's new" compare
  that stamp instead of Instagram's `versionName`, so multiple Unhooked
  releases can ship on one IG base. The settings page shows both:
  `Unhooked X.Y.Z · Instagram A.B.C.D`.
- **Tool jars are Gradle-managed.** `./gradlew setupTools` fetches
  APKEditor and the Morphe CLI into `tools/` (pinned versions + SHA-256 in
  `gradle/libs.versions.toml`); the patch tasks depend on it, so a fresh
  clone builds with no manual downloads.

### Removed

- **Donation links.** Sponsor badges and the in-app Donate buttons are gone.
- **Permanent lock removed.** Every setting is freely reversible again;
  nothing freezes, and there is no reinstall to undo a change.
- **Follow prompt removed.** The first-launch card inviting users to follow
  the official account is gone; "What's new" shows directly after an update.

### Added

- **Allow screenshots** toggle (Settings → Privacy, off by default): strips
  FLAG_SECURE from Instagram's windows so screenshots and screen recordings
  work in view-once media and disappearing DMs, forces recents thumbnails on,
  and drops the screenshot-taken report so the sender is not notified.
- **GitLab CI on main.** `.gitlab-ci.yml` runs preflight → build → verify
  → publish on every `dev` → `main` merge: both flavors are built and gated
  on the patch report plus the per-flavor `.gitlab/expected-patches-*.txt`
  lists, signatures and package identities are verified (with an optional
  release-cert pin), and a new `v<VERSION>` GitLab Release is published
  with both APKs as assets — which is what the in-app updater reads.
  Re-merges without a version bump just build. See
  `docs/BUILD_FROM_SOURCE.md` for the required CI variables.

## Upstream history (Feurstagram)

Releases below predate the fork and describe Feurstagram by jean-voila;
names, links, and issue numbers refer to that project
([github.com/jean-voila/Feurstagram](https://github.com/jean-voila/Feurstagram)).

## v446-0-0-49-77

### Added

- **Follow the official account.** The first launch after installing or updating
  shows a card inviting the user to follow the official account, where every
  release is announced. _Follow_ opens the profile inside the app, _Not now_
  closes it. "After an install or update" is read from the package's
  `lastUpdateTime`, not its versionName, so a re-release on the same Instagram
  base counts too; it is shown once per install or update, and the "What's new"
  card only runs once it has been dismissed.
- **Website: Instagram without Reels pages, guides and a press kit.** A landing page for
  "Instagram without Reels" comparing the ways to hide Reels, guides (removing Reels on
  Android, Instagram without the feed, DFInstagram alternative, Feurstagram vs ScrollGuard vs
  WallHabit, the EU's findings on Instagram's addictive design), a press kit, a French
  version of the main pages, structured data, `hreflang` pairs and a `sitemap.xml`.
- The website shows the total download count, loaded from GitHub when the page opens, and
  the official contact address.

### Fixed

- **Deep links keep working on Instagram 446.** Instagram 446 reshaped the family-app scope
  trust check that the signature-check bypass disables: the static
  `boolean(KeyHash, KeyHash, Z)` became an instance method taking the caller identity and
  reading both key hashes from it. The patch failed to apply, which would have sent shared
  posts, reels and profiles back to the home feed. It now matches either form, the new one by
  structure alone.
- The website's canonical and Open Graph URLs pointed at `/Feurstagram/`, which returns 404 on
  GitHub Pages; they now use `/FeurStagram/`.

### Changed

- **The settings page opens with an animation.** Long-pressing Home now brings the
  settings page in with a short rise-and-fade instead of it appearing at once.
- Updated to Instagram 446.0.0.49.77.
- The README's download badge shows the total number of downloads, and the README lists the
  official contact address.

## v445-0-0-45-83

### Fixed

- **Ad and "suggested" filtering in the feed works again.** The patch that drops
  ad/promo units injected inline into the timeline stopped applying on Instagram
  444: its fingerprint required the `in_feed_survey` unit type, which Instagram
  removed from the feed-item parser. The patch failed silently and every inline
  ad/netego unit came back. The fingerprint now anchors on four load-bearing
  tokens instead of seven.
- **Hidden navigation tabs can no longer be reached by swiping.** Hiding a tab
  only removed its icon; the page stayed in the swipe pager, so a flick still
  opened Search, Messages or Reels. A swipe released toward a hidden tab is now
  re-aimed at the nearest visible one, mid-flight, so it stays a single
  continuous gesture rather than landing on the hidden page and cutting away.
- **The landing page can no longer point at a hidden surface.** Search stayed
  selectable as the cold-start page even when its tab was hidden, which handed
  back the surface the user meant to remove. Options for hidden tabs are now
  greyed out, and an already-stored one falls back to the home feed.

- **The updater no longer raises two install prompts.** A second Android
  "update this app?" dialog was queued behind the first and surfaced once the
  install had already finished, reporting a failure for an APK that was no
  longer there. Only one confirmation is ever raised now, a second update can't
  start while one is running, and any installer session left over from an
  earlier attempt is abandoned first.

### Added

- **Rebuilt update flow, as full-screen pages.** "Update available" shows the
  release's own notes (headings, bullets and all); choosing Update opens a
  progress page with a real progress bar, transferred size, percentage and a
  Cancel. Leaving that page does not stop the download — it carries on behind an
  ongoing notification with the same progress and the same Cancel. Neither
  screen is a popup any more.
- **What's new.** The first launch after an update shows a card with that
  version's release notes, pulled from its GitHub release. Shown once per
  version, and not gated on the update-check toggle — that toggle is about being
  nagged to update, not about being told what an update did.
- **Design guidelines** written down in `docs/DESIGN.md`, so the Material 3
  Expressive monochrome system is followed the next time the UI changes.
- **Debug builds** (`./gradlew buildApk … -Pdebug`): the settings become readable and
  writable over ADB broadcasts, so on-device testing no longer needs the UI.
  Development only, never enabled in a release.

### Changed

- **Feurstagram's own screens are redesigned in Material 3 Expressive,
  monochrome.** No more purple: white is the only accent. Options are grouped
  into connected lists with large outer corners, buttons are pills, section
  labels are sentence case, and the headline is larger and tighter. Settings,
  the update pages, the confirmations and the first-run guide all share it.
- **Every Feurstagram screen now fits any device.** They pad themselves against
  the system bars and the display cutout, so the action buttons are no longer
  hidden behind a three-button navigation bar; the scrolling area shrinks to
  what is actually on screen instead of being cropped; and the content column is
  capped at a readable width on tablets, foldables and wide screens.
- Updated to Instagram 445.0.0.45.83.
- The pipeline now reports which patches applied (`build/patch-report.json`), so a
  fingerprint that stops matching is visible instead of silent.
- The update download no longer goes through the system `DownloadManager`; it is
  fetched in-app so its progress can be shown, cancelled, and mirrored into a
  notification when the page is closed.

## v444-0-0-46-85

### Added

- **Force dark (disable HDR)** toggle (Settings → Display, on by default): stops
  Instagram forcing its window into HDR mode, which lifted the black floor and
  washed out the dark UI on HDR OLED screens. Turn it off to restore Instagram's
  HDR behaviour.

### Fixed

- **Deep links** into Instagram now open their target (shared posts, reels,
  profiles) instead of dropping to the home feed, via a new signature-check
  bypass that makes Instagram trust the re-signed build. Thanks @na-ji.

### Changed

- Updated to Instagram 444.0.0.46.85.

## Previous releases

See [GitLab Releases](https://gitlab.com/RohanDaCoder/Unhooked/-/releases) for
Unhooked APK downloads, and
[Feurstagram's GitHub Releases](https://github.com/jean-voila/Feurstagram/releases)
for the upstream history.
