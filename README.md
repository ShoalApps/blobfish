# Blobfish

A Material You client for Bluesky, part of [Shoal](https://shoalapps.github.io): open-source Android apps
that take on your wallpaper's colours.

- Jetpack Compose and Material 3 Expressive: wallpaper colours, a navigation rail on big screens,
  segmented settings and Expressive motion
- Talks to Bluesky directly over the AT Protocol; browse Discover, profiles and threads signed out
- Sign in with an app password for your timeline, notifications, likes, reposts, follows and posting
- mu-style formatting: fenced code blocks with syntax highlighting, `inline code`, **bold**, *italic*
  and ~~strikethrough~~, alongside Bluesky's links, mentions and hashtags
- Updates itself from GitHub releases (Android asks first)

## Getting the APK

Every push to `main` builds a signed release. Install `Blobfish.apk` from the newest
[release](https://github.com/ShoalApps/blobfish/releases/latest).

## Privacy

No servers, analytics or ads. The app talks to Bluesky (and your own PDS when signed in) and, once a day,
asks GitHub whether there's a newer version. Your session is stored only on your phone.

## Licence

Free software under the **GNU General Public License v3.0 or later**. See [LICENSE](LICENSE).

Blobfish is not affiliated with, endorsed by or sponsored by Bluesky Social PBC.
