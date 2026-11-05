# Post parsing audit

Atp's post/feed parsing is now in `BlueskyParse`. `as?` tests accept only objects,
arrays or primitives of the expected kind. String helpers additionally require
`isString`; numeric content uses `toIntOrNull`. Optional malformed fields default
or get a fallback note. Required post record/text/author/URI/CID failures return
null, dropping only that entry. No `!!`, throwing `as`, `.jsonObject` or
`.jsonPrimitive` remain in the pure post parser. A per-post Exception boundary
also isolates failures in injected account overlays. Date parsing is guarded.
Image fallback URLs no longer assert non-null. RichText’s guaranteed regex capture
is now checked without an assertion too. Quote parsing is one level deep;
additional quote embeds receive a fallback note. Facet offsets are converted to
bounded character indices by RichText, and unknown feature IDs retain plain text.

Atp's thread parent traversal no longer asserts the post's JSON type. Its remaining
assertions/casts belong to session creation/refresh and blob upload, not post
parsing. Those casts either sit in existing runCatching blocks or fail the explicit
sign-in/upload operation; they cannot discard a feed because of one malformed post.
Profile parsing reports an incomplete-profile error instead of asserting an author.

TwitterParse uses safe shape access throughout, bounds quoted-post recursion to
nine levels, and scans unrecognised __typename names (not content) to a bounded
walk depth. Unexpected timeline entries produce no post; valid siblings remain.
The one former thread-chain assertion is replaced by a nullable fold.

Tests are synthetic and have not been run. No live account response is committed.
