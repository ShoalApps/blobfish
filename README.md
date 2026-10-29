<p align="center"><a href="https://shoalapps.github.io/"><img src="https://raw.githubusercontent.com/ShoalApps/shoalapps.github.io/main/assets/readme/blobfish.svg" alt="Blobfish - a Shoal app, with the rest of the Shoal family" width="100%"></a></p>

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

## Privacy and legal

No ads, no tracking, nothing sent to us unless you agree to anonymous error reports. See the [privacy policy](https://shoalapps.github.io/privacy.html) and [legal notices](https://shoalapps.github.io/legal.html).

## Support

Shoal is free, open source and has no ads. If you'd like to help it keep going, you can [support Shoal on Liberapay](https://liberapay.com/ShoalApps).

## Release notes

Update `app/src/main/assets/whats-new.json` with each release. Notes ship with the APK, appear once per installed version and can be reopened from Settings. Keep them specific to changes in that release.

## App language

The apps follow Android's system language by default, with an App language picker in Settings and right-to-left layout support. Translations are bundled and work offline. Coverage varies: many phrases are automatically translated, community catalogues may be partial, and missing strings fall back to British English. See `tools/README.md` and `app/src/main/assets/i18n/coverage.json` for maintenance and actual coverage.
