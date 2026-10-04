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

| Keep using Instagram for                        | Remove or block                               |
| ----------------------------------------------- | --------------------------------------------- |
| Direct Messages (including Reels shared in DMs) | Endless Home feed                             |
| Stories                                         | Reels surfaces                                |
| Search, profiles and deep links                 | Explore and suggested-account recommendations |
| Notifications                                   | Ads, shopping preloads, and telemetry         |

All content blocks are **individual runtime toggles** — long-press the Home
tab at the bottom-left of the main tab bar to open the Unhooked settings page
(a one-time hint points you at it on your first signed-in launch). A single
APK covers every combination.

> Screenshots and the settings tutorial were removed with the old branding —
> fresh ones will be captured from an Unhooked build.

## Features

| Feature                            | Default | Toggleable | How                                 |
| ---------------------------------- | ------- | ---------- | ----------------------------------- |
| **Home Feed**                      | Blocked | Yes        | Network-level blocking              |
| **Following feed only**            | Off     | Yes        | Feed-request header rewrite         |
| **Explore**                        | Blocked | Yes        | Network-level blocking              |
| **Reels**                          | Blocked | Yes        | Network-level blocking + Tab hidden |
| **Friends row in Reels**           | Blocked | Yes        | View visibility hidden              |
| **Stories**                        | Visible | Yes        | Network-level blocking              |
| **Suggested accounts**             | Blocked | Yes        | Network-level + feed-parse blocking |
| **Instants (+ button in DMs)**     | Blocked | Yes        | View visibility hidden              |
| **Notes (text bubbles above DMs)** | Blocked | Yes        | View visibility hidden              |
| **Ads**                            | Blocked | Yes        | Network-level + feed-parse blocking |
| **Heart button (notifications)**    | Visible | Yes        | View visibility hidden              |
| **Instagram popups**               | Hidden  | Yes        | Popup suppression                   |
| **Analytics & telemetry**          | Blocked | No         | Always blocked                      |
| **Shopping / commerce preloads**   | Blocked | No         | Always blocked                      |

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

- Building from source, signing, and debugging: [docs/DEVELOPERS.md](docs/DEVELOPERS.md)
- Patch internals, project structure, and blocked paths: [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)

## Contributing

Fork it, open a pull request, or copy the code — contributions welcome. See
[CONTRIBUTING.md](CONTRIBUTING.md) and [SECURITY.md](SECURITY.md).

## License

Released under the GNU General Public License v3.0 — see [LICENSE](LICENSE).
Built with the [Morphe](https://morphe.software) patcher and adapting patch
ideas from [Piko](https://github.com/crimera/piko); see [NOTICE](NOTICE).

_Unhooked is a fork of [Feurstagram](https://github.com/jean-voila/Feurstagram),
independently maintained and unaffiliated with Instagram or Meta._
