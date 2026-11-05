# Butterflyfish feature parity

Use this alongside the [Bluesky lexicon report](bluesky/REPORT.md) and [X tracker](x/README.md). A parser, an API endpoint and a complete user flow are separate milestones. Verify each flow with fixtures and a live test account before calling it complete.

| Area | Butterflyfish today | Bluesky work | X work |
| --- | --- | --- | --- |
| Accounts | Bluesky sessions, account switching and PDS configuration. X web session. | Verify OAuth scopes and refresh, then account lifecycle, handle, app-password and safety settings. | Map web sign-in and account switching. Evaluate official user-auth access before native account management. |
| Feeds and discovery | Bluesky Discover, Following and pinned feeds. X home and Following. | Feed discovery, management, suggestions, starter packs and list management. | Trends, topics, Lists and Communities. Verify which flows have documented access. |
| Search | Bluesky posts and people, with Top/Latest and cursor paging for posts. X search. | Add people paging and verify filters and typeahead. | Verify search filters, account results, media and paging. |
| Posts and threads | Bluesky text and image posts, replies, local drafts, likes and reposts. Native X timeline and composer paths. | Add quote composition, video upload, thread and quote controls, and complete reply-tree handling. Verify server drafts. | Verify quote posts, editing, media, polls, long posts, drafts and scheduling. Preserve service-specific eligibility. |
| Profiles and relationships | Bluesky profile editing, followers, following and follow actions. X profile reading. | Verify profile field preservation, avatar removal, pinned posts, profile tabs, verification and live status. | Audit profile editing, pinned posts, private accounts, follow requests, verification and profile tabs. |
| Saved content | Local Bluesky drafts. | Add private server bookmarks and verify add/remove and paging. | Verify authenticated bookmarks and folder lookup through the documented API. |
| Notifications | Bluesky notification list. | Add read state, grouping, filters, preferences and push registration. | Audit mentions, activity filters and delivery. |
| Messages | No shared native inbox. | Add requests, conversation paging, send, read state, mute and reporting using the chat service. | Evaluate documented DM access and separate any web-only messaging experiences. |
| Moderation and safety | Bluesky mute controls, content preferences and label handling. | Audit blocks, reports, muted words, moderation lists, labelers, hidden replies and context across feeds, search and threads. | Verify blocks, mutes, reports, sensitive media, reply controls and private-account restrictions. |
| Media and live | Image and video playback, image viewer and data saver. | Cover GIFs, captions, alt text, live badges and supported embeds. | Audit uploads, GIFs, captions, autoplay, live video and Spaces. |
| Platform-specific services | Separate Bluesky and X surfaces. | Track contact discovery, age assurance, account status and external integrations in the lexicon report. | Track Articles, monetisation, subscriptions, analytics and creator features. Mark official-app-only or entitlement-gated flows individually. |

## Implementation order

1. Make existing paths reliable. Cover pagination, retry, stale responses, action state, thread completeness and moderation before expanding the same surfaces.
2. Add daily-use Bluesky features. Start with bookmarks, search paging and sorting, notification state and quote composition. Follow with chat and feed/list management.
3. Validate the X access model for each feature. Keep credentials and content scoped to their service. Treat a documented endpoint, account entitlement and a working integration as separate checks.
4. Measure cold start, time to first feed item, scroll frame timing, memory and request counts on the same device and network before and after performance changes.

References: [Bluesky API](https://docs.bsky.app/docs/api/app-bsky-feed-get-feed), [Bluesky client](https://github.com/bluesky-social/social-app), [X API](https://docs.x.com/llms.txt).
