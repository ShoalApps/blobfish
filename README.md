<p align="center"><a href="https://shoalapps.github.io/"><img src="https://raw.githubusercontent.com/ShoalApps/shoalapps.github.io/main/assets/readme/butterflyfish.svg" alt="Butterflyfish - a Shoal app, with the rest of the Shoal family" width="100%"></a></p>

*Formerly Blobfish.*

A Material You client for Bluesky, with an X tab, part of [Shoal](https://shoalapps.github.io): open-source Android apps
that take on your wallpaper's colours.

- Jetpack Compose and Material 3 Expressive: wallpaper colours, a navigation rail on big screens,
  segmented settings and Expressive motion
- Talks to Bluesky directly over the AT Protocol; browse Discover, profiles and threads signed out
- Unfinished posts stay as drafts, with their pictures, for each account and reply
- Sign in with an app password for your timeline, notifications, likes, reposts, follows and posting
- mu-style formatting: fenced code blocks with syntax highlighting and `inline code`
  and ~~strikethrough~~, alongside Bluesky's links, mentions and hashtags
- Updates itself from GitHub releases (Android asks first)

- **Hidden follows** save Bluesky profiles on this device and read their public posts without signing in to the feed request or creating a follow. Find them under Home → Hidden follows. Public reads still contact the feed provider.
- **Show only NSFW posts** in Moderation filters Bluesky feeds and post search by content labels while retaining existing moderation rules.

## Getting the APK

Every push to `main` builds a signed release. Install `Butterflyfish.apk` from the newest
[release](https://github.com/ShoalApps/butterflyfish/releases/latest).

## Privacy

No servers, analytics or ads. The app talks to Bluesky (and your own PDS when signed in), X (x.com) if you use the X tab, and, once a day,
asks GitHub whether there's a newer version. Your session is stored only on your phone.

## Licence

Free software under the **GNU General Public License v3.0 or later**. See [LICENSE](LICENSE).

Butterflyfish is not affiliated with, endorsed by or sponsored by Bluesky Social PBC.

## Privacy and legal

No ads, no tracking, nothing sent to us unless you agree to anonymous error reports. See the [privacy policy](https://shoalapps.github.io/privacy.html) and [legal notices](https://shoalapps.github.io/legal.html).

## Support

Shoal is free, open source and has no ads. If you'd like to help it keep going, you can [support Shoal on Liberapay](https://liberapay.com/ShoalApps).

## Release notes

Update `app/src/main/assets/whats-new.json` with each release. Notes ship with the APK, appear once per installed version and can be reopened from Settings. Keep them specific to changes in that release.

## App language

The apps follow Android's system language by default, with an App language picker in Settings and right-to-left layout support. Translations are bundled and work offline. Coverage varies: many phrases are automatically translated, community catalogues may be partial, and missing strings fall back to British English. See `tools/README.md` and `app/src/main/assets/i18n/coverage.json` for maintenance and actual coverage.

## Staying up to date

The [feature parity tracker](compat/FEATURES.md) records current coverage and the work ahead for Bluesky and X.

Settings → **Staying up to date** shows the bundled Bluesky schema date and source commit,
partial feature coverage, features still to add, and local counts of unrecognised type names.
Unknown Bluesky embeds, feed reasons and facets keep the readable post and offer **Open on
Bluesky**. Counts stay on the device, contain no post content, and are capped at 100 names per service.

The six-hour Bluesky follower snapshots public `bluesky-social/atproto` lexicons, records its
commit and `bluesky-social/social-app`'s commit, reports differences, and accepts updates only
after the release build and JVM tests pass. A bot push explicitly dispatches the release workflow.
New data can flow automatically; new screens and rendering still need building. New features
are informational; removal of declared supported data fails validation. See
[`compat/bluesky/REPORT.md`](compat/bluesky/REPORT.md) and the single **Bluesky changed: features to add** issue.

X has no public client source: reading follows responses delivered to X's website. A daily
watcher can record verified public API tracker commits and update one **X changes to check**
issue; it never copies their code or automatically edits the app. X home feeds stay in session memory only; viewer name, handle
and picture are cached privately and cleared on explicit X sign-out.
