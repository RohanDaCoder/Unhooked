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
    <a href="docs/BUILD_FROM_SOURCE.md">Build from source</a>
    ·
    <a href="docs/ARCHITECTURE.md">How it works</a>
    ·
    <a href="https://gitlab.com/RohanDaCoder/Unhooked/-/issues">Issues</a>
  </p>
</div>

## What it does

| Keep using Instagram for                        | Remove or block                               |
| ----------------------------------------------- | --------------------------------------------- |
| Direct Messages (including Reels shared in DMs) | Endless Home feed                             |
| Stories                                         | Reels surfaces                                |
| Search, profiles and deep links                 | Explore and suggested-account recommendations |
| Notifications                                   | Ads, shopping preloads, and telemetry         |

All content blocks are **individual runtime toggles** — long-press the Home
tab at the bottom-left of the main tab bar to open the Unhooked settings page
(a one-time hint points you at it on your first signed-in launch). If the
Home tab itself is hidden, the leftmost remaining tab carries the long-press
gesture. A single APK covers every combination.

## Features

| Feature                            | Default | Toggleable | How                                 |
| ---------------------------------- | ------- | ---------- | ----------------------------------- |
| **Home Feed**                      | Blocked | Yes        | Network-level blocking              |
| **Following feed only**            | Off     | Yes        | Feed-request header rewrite         |
| **Explore**                        | Blocked | Yes        | Network-level blocking              |
| **Reels**                          | Blocked | Yes        | Network-level blocking + Tab hidden |
| **Friends row in Reels**           | Blocked | Yes        | View visibility hidden              |
| **Stories**                        | Visible | Yes        | Network-level blocking              |
| **Suggested accounts**             | Blocked | Yes        | Network-level + feed-parse blocking (feed and story tray) |
| **Instants (+ button in DMs)**     | Blocked | Yes        | View visibility hidden              |
| **Notes (text bubbles above DMs)** | Blocked | Yes        | View visibility hidden              |
| **Ads**                            | Blocked | Yes        | Network-level + feed-parse blocking |
| **Heart button (notifications)**   | Visible | Yes        | View visibility hidden              |
| **Instagram popups**               | Hidden  | Yes        | Popup suppression                   |
| **Analytics & telemetry**          | Blocked | No         | Always blocked                      |
| **Shopping / commerce preloads**   | Blocked | No         | Always blocked                      |

**Following feed only** is a softer alternative to blocking the Home Feed
entirely: leave the feed unblocked and turn this on to get a chronological feed
of just the accounts you follow, with the recommended/ranked posts dropped.

The settings open on an **overview** with one row per area — Blocked surfaces,
Navigation bar, Feed, Landing page, Display and popups, Privacy, Updates and
Support — each with a one-line summary of its state; tapping a row opens that
section's page in the same window, and Back returns to the overview. The
overview also carries a **Permanent lock** pill: once enabled, the surfaces
hidden at that moment stay hidden for the installation and only a reinstall
unlocks them (tightening stays possible).

The **navigation bar** page previews the bottom bar in its live order as you
toggle the icons (Home included — while another icon stays on the bar to carry
the long-press settings gesture). **Force dark** (disables Instagram's forced
HDR window, on by default), **Allow screenshots** (off by default, in Privacy)
and the cold-start **landing page** have their own pages too.

**Quoicouview mode** (experimental, off by default, first row of the settings):
Messages becomes the app's main page with the stories tray on top of the inbox
— under its search bar — and Home is turned off. Stories open from there the
way they do from Home, the notifications button moves into the inbox header,
and the story camera opens from the tray's "Your story" (+) or with a swipe to
the right. A one-time card presents the mode after the update that brings it.
Changing anything restarts the app via Done or Back so blocks always apply
cleanly; settings persist in SharedPreferences (`unhooked_prefs`).

## Installation

Download the latest APK from the
[Releases](https://gitlab.com/RohanDaCoder/Unhooked/-/releases) page and check
the release notes for the Instagram version it was built against.

- **Classic APK** — replaces the stock Instagram app (same package id, so the
  stock app must be uninstalled first; its data is wiped).
- **Clone APK** — installs alongside the stock app as a separate package
  (`com.instagram.android.unhooked`), so both can run at the same time.

Android will ask you to allow installations from unknown sources for whatever
app you downloaded the APK with (browser or file manager); this is normal for
apps distributed outside the Play Store. After installing, log in through
Instagram as usual.

To build the APK yourself instead — including the clone variant with `-Pclone`
— see [docs/BUILD_FROM_SOURCE.md](docs/BUILD_FROM_SOURCE.md).

## Updates

Unhooked checks GitLab for a newer release on every launch and shows a
download prompt when your build is out of date. The **automatic update check**
is on by default and can be turned off in the settings page (Updates section).
After an update, the first launch shows a **What's new** card with that
version's release notes.

## Safety

Unhooked does not collect, proxy, store, or transmit your credentials or any
personal data. It does not bypass login, add tracking, or scrape accounts —
the patches are open source and documented, so you can inspect what is
changed. For maximum trust, [build the APK yourself](docs/BUILD_FROM_SOURCE.md).

Unhooked is unofficial and unaffiliated with Instagram or Meta. It installs
outside the Play Store, so Android may show installation warnings, and using
a modified client may violate Instagram's terms of service. Use it at your
own risk.

## Contact

- Discord: `rohan_ohio`
- Email: [alzamirohan@proton.me](mailto:alzamirohan@proton.me)

## For developers

- Building from source, signing, and CI: [docs/BUILD_FROM_SOURCE.md](docs/BUILD_FROM_SOURCE.md)
- Patch internals and project structure: [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)

## Contributing

Fork it, open a pull request, or copy the code — contributions welcome. See
[CONTRIBUTING.md](CONTRIBUTING.md).

## License

Released under the GNU General Public License v3.0 — see [LICENSE](LICENSE).
Built with the [Morphe](https://morphe.software) patcher and adapting patch
ideas from [Piko](https://github.com/crimera/piko); see [NOTICE](NOTICE).

_Unhooked is a fork of [Feurstagram](https://github.com/jean-voila/FeurStagram),
independently maintained and unaffiliated with Instagram or Meta._
