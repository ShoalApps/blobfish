package io.github.shoalapps.blobfish.ui

import android.webkit.JavascriptInterface
import android.webkit.WebView
import io.github.shoalapps.blobfish.data.FeedItem
import io.github.shoalapps.blobfish.data.Page
import io.github.shoalapps.blobfish.data.Profile
import io.github.shoalapps.blobfish.data.Thread
import io.github.shoalapps.blobfish.data.TwitterParse
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/**
 * Twitter, read the way its website reads it. A hidden copy of the website (signed in with your
 * account, in [XView]) is steered to the page Blobfish needs; the data the website's own code then
 * receives is handed to Blobfish, which shows it in its own layout. Nothing is requested that the
 * website itself wouldn't request, so it keeps working however Twitter signs its requests.
 */
@OptIn(DelicateCoroutinesApi::class)
object XBridge {
    private val json = Json { ignoreUnknownKeys = true }
    private val lock = Mutex()
    private val waiting = mutableMapOf<String, CompletableDeferred<JsonObject>>()
    /** The latest response for each kind of request, for when the website answers from its cache. */
    private val latest = mutableMapOf<String, JsonObject>()
    private val latestAt = mutableMapOf<String, Long>()

    /** The [op] response that arrived in the last [maxAgeMs], if any (the website often loads several at once). */
    private fun fresh(op: String, maxAgeMs: Long): JsonObject? = synchronized(this) {
        latest[op]?.takeIf { System.currentTimeMillis() - (latestAt[op] ?: 0) < maxAgeMs }
    }

    /** Runs before the website's own scripts: passes each data response on to Blobfish. */
    const val CAPTURE_SCRIPT = """
        (function () {
          if (window.__blobfishCapture) return; window.__blobfishCapture = true;
          const open = XMLHttpRequest.prototype.open, send = XMLHttpRequest.prototype.send;
          XMLHttpRequest.prototype.open = function (m, u) { this.__blobfishUrl = String(u); return open.apply(this, arguments); };
          XMLHttpRequest.prototype.send = function () {
            this.addEventListener('load', () => {
              const m = this.__blobfishUrl.match(/\/graphql\/[^/]+\/(\w+)/);
              if (m && window.BlobfishX && this.status === 200) window.BlobfishX.onData(m[1], this.responseText);
              // Your account settings (which name you) come from an older kind of request.
              if (/\/1\.1\/account\/settings\.json/.test(this.__blobfishUrl) && window.BlobfishX && this.status === 200) window.BlobfishX.onData('AccountSettings', this.responseText);
            });
            return send.apply(this, arguments);
          };
        })();
    """

    /** Receives the website's data (on a WebView thread). */
    class Receiver {
        @JavascriptInterface fun onData(op: String, body: String) {
            if (io.github.shoalapps.blobfish.BuildConfig.DEBUG) android.util.Log.d("XBridge", "got $op (${body.length} chars)")
            val obj = runCatching { json.parseToJsonElement(body) as JsonObject }.getOrNull() ?: return
            if (op == "AccountSettings") (obj["screen_name"] as? kotlinx.serialization.json.JsonPrimitive)?.content?.let { me = it }
            synchronized(XBridge) {
                latest[op] = obj
                latestAt[op] = System.currentTimeMillis()
                waiting.remove(op)?.complete(obj)
            }
        }
    }

    private suspend fun view(): WebView = withContext(Dispatchers.Main) { XView.get(io.github.shoalapps.blobfish.Graph.app) }

    /** Does [action] on the hidden website and waits for its next [op] response. */
    private suspend fun request(op: String, timeoutMs: Long = 25_000, cachedOk: Boolean = false, action: suspend (WebView) -> Unit): JsonObject =
        request(listOf(op), timeoutMs, cachedOk, action)

    /** As above, for data the website has had several names for ([ops], any of them). */
    private suspend fun request(ops: List<String>, timeoutMs: Long = 25_000, cachedOk: Boolean = false, action: suspend (WebView) -> Unit): JsonObject = lock.withLock {
        val d = CompletableDeferred<JsonObject>()
        synchronized(this) { ops.forEach { waiting[it] = d } }
        val v = view()
        if (io.github.shoalapps.blobfish.BuildConfig.DEBUG) android.util.Log.d("XBridge", "want $ops")
        withContext(Dispatchers.Main) { action(v) }
        val got = withTimeoutOrNull(timeoutMs) { d.await() }
        synchronized(this) { ops.forEach { if (waiting[it] === d) waiting.remove(it) } }
        // A list the website showed from its cache is still the right list; a post or profile may not be.
        got ?: (if (cachedOk) synchronized(this) { ops.firstNotNullOfOrNull { latest[it] } } else null)
            ?: throw AtpException(504, "${XBrand.name} didn't answer. Check you're signed in, then try again.")
    }

    /** Asks the website for more of the list it's showing, by scrolling to its end. */
    private fun more(v: WebView) = v.evaluateJavascript("window.scrollTo(0, document.body.scrollHeight)", null)

    /** Your home timeline: "For you", or "Following" (newest first). A non-null [cursor] means "more". */
    suspend fun home(following: Boolean, cursor: String?): Page<FeedItem> {
        val op = if (following) "HomeLatestTimeline" else "HomeTimeline"
        val root = request(op, cachedOk = true) { v ->
            if (cursor != null) more(v)
            else {
                // The website opens on whichever tab you last used, so press the one wanted.
                val label = if (following) "Following" else "For you"
                v.loadUrl("https://x.com/home")
                v.postDelayed({ v.evaluateJavascript("(function f(n){ const t = [...document.querySelectorAll('[role=tab]')].find(e => e.innerText.trim() === '$label'); if (t) { if (t.getAttribute('aria-selected') !== 'true') t.click(); } else if (n < 60) setTimeout(() => f(n + 1), 250); })(0)", null) }, 500)
            }
        }
        return TwitterParse.timeline(root).let { if (it.items.isEmpty() && cursor != null) it.copy(cursor = null) else it }
    }

    /** Someone's profile, by handle. */
    suspend fun profile(handle: String): Profile {
        val root = request("UserByScreenName") { v -> v.loadUrl("https://x.com/$handle") }
        return TwitterParse.profile(root) ?: throw AtpException(404, "Couldn't find @$handle on ${XBrand.name}")
    }

    /**
     * Their posts, posts and replies ([kind] "replies") or media ("media"). Opening a profile
     * loads its posts too, so those are used when they've just arrived.
     */
    suspend fun posts(handle: String, cursor: String?, kind: String = "posts"): Page<FeedItem> {
        // Twitter renames these now and then; any of the names will do.
        val (ops, path) = when (kind) {
            "replies" -> listOf("UserTweetsAndReplies", "UserRepliesTimeline") to "/$handle/with_replies"
            "media" -> listOf("UserMedia", "UserMediaTimeline") to "/$handle/media"
            else -> listOf("UserTweets", "UserOriginalsTimeline") to "/$handle"
        }
        val onPage = withContext(Dispatchers.Main) { view().url?.substringBefore('?')?.trimEnd('/')?.endsWith(path, ignoreCase = true) == true }
        val root = (if (cursor == null && onPage) ops.firstNotNullOfOrNull { fresh(it, 20_000) } else null)
            ?: request(ops, cachedOk = cursor != null) { v -> if (cursor != null) more(v) else v.loadUrl("https://x.com$path") }
        return TwitterParse.timeline(root).let { if (it.items.isEmpty() && cursor != null) it.copy(cursor = null) else it }
    }

    /** Follows or unfollows someone, with the button on their profile (and its confirmation). */
    suspend fun follow(handle: String, on: Boolean): Boolean = lock.withLock {
        withContext(Dispatchers.Main) {
            val v = view()
            if (!loadAndAwait(v, "https://x.com/$handle", "/$handle")) return@withContext false
            pressOnPage(v, if (on) listOf("-follow") else listOf("-unfollow", "confirmationSheetConfirm"))
        }
    }

    /** Presses buttons whose test id ends with each of [suffixes], in turn, waiting for each to appear. */
    private suspend fun pressOnPage(v: WebView, suffixes: List<String>, scopeJs: String = "document", exact: Boolean = false, expect: String? = null): Boolean {
        val d = CompletableDeferred<Boolean>()
        val w = CompletableDeferred<JsonObject>()
        synchronized(this) { waiting["__press"] = w }
        val ids = suffixes.joinToString(",") { org.json.JSONObject.quote(it) }
        v.evaluateJavascript("""
            (function () { const ids = [$ids]; let tries = 0;
              const step = (i) => {
                // All pressed: done, or (with an expected button) done once that button has appeared.
                if (i >= ids.length) {
                  const want = ${org.json.JSONObject.quote(expect ?: "")};
                  if (!want) return window.BlobfishX.onData('__press', JSON.stringify({ok: true}));
                  let checks = 0;
                  const check = () => { const r = ($scopeJs); if (r && r.querySelector('[data-testid="' + want + '"]')) return window.BlobfishX.onData('__press', JSON.stringify({ok: true}));
                    if (++checks < 20) setTimeout(check, 250); else window.BlobfishX.onData('__press', JSON.stringify({ok: false})); };
                  return check();
                }
                // The first button is looked for within the scope (e.g. one post); later ones (confirmations) anywhere.
                const root = i === 0 ? ($scopeJs) : document;
                const b = root && root.querySelector('[data-testid${if (exact) "" else "$"}="' + ids[i] + '"]');
                if (!b) { if (++tries < 40) return setTimeout(() => step(i), 250); return window.BlobfishX.onData('__press', JSON.stringify({ok: false})); }
                // A moment between presses: X's confirmation sheet ignores taps while it slides in.
                b.click(); tries = 0; setTimeout(() => step(i + 1), 1200);
              };
              setTimeout(() => step(0), 800);
            })();
        """, null)
        return (withTimeoutOrNull(20_000) { w.await() }?.get("ok") as? kotlinx.serialization.json.JsonPrimitive)?.content == "true"
    }

    /** A post with the posts above it and its replies. */
    suspend fun thread(id: String): Thread {
        val root = request("TweetDetail") { v -> v.loadUrl("https://x.com/i/status/$id") }
        return TwitterParse.thread(root, id) ?: throw AtpException(404, "That post isn't available")
    }

    /** Your handle, once read. */
    @Volatile var me: String? = null
        private set

    /** Your own handle, from your account settings (which the website loads on every page). */
    suspend fun myHandle(): String? = me ?: runCatching {
        request("AccountSettings", timeoutMs = 15_000, cachedOk = true) { v -> v.loadUrl("https://x.com/home") }
        me
    }.getOrNull()

    /**
     * Presses a post's buttons on the website, as you would: like/unlike, repost/undo. [testIds]
     * are pressed in turn (a repost needs its confirm). Returns whether they were all found.
     */
    suspend fun press(id: String, vararg testIds: String, expect: String? = null): Boolean = lock.withLock {
        withContext(Dispatchers.Main) {
            val v = view()
            if (!loadAndAwait(v, "https://x.com/i/status/$id", "/status/$id")) return@withContext false
            // The post itself, not the ones above it or the replies: its timestamp links to it.
            val scope = "[...document.querySelectorAll('article')].find(a => a.querySelector('a[href*=\"/status/$id\"] time')) || document.querySelector('article')"
            pressOnPage(v, testIds.toList(), scope, exact = true, expect = expect)
        }
    }

    /** Likes or unlikes a post; true once the website shows it done. */
    suspend fun like(id: String, on: Boolean) = press(id, if (on) "like" else "unlike", expect = if (on) "unlike" else "like")

    /** Reposts a post or undoes it (each with its confirmation); true once the website shows it done. */
    suspend fun repost(id: String, on: Boolean) =
        press(id, if (on) "retweet" else "unretweet", if (on) "retweetConfirm" else "unretweetConfirm", expect = if (on) "unretweet" else "retweet")

    /** Loads [url] and waits (up to 15 s) for that new page, with a post or profile on it, to be showing. */
    private suspend fun loadAndAwait(v: WebView, url: String, fragment: String): Boolean {
        // Mark the page being left, so the old one (even at the same address) isn't mistaken for the new.
        val marked = CompletableDeferred<Unit>()
        v.evaluateJavascript("window.__blobfishOld = true") { marked.complete(Unit) }
        marked.await()
        v.loadUrl(url)
        repeat(60) {
            kotlinx.coroutines.delay(250)
            val d = CompletableDeferred<String?>()
            v.evaluateJavascript("(window.__blobfishOld ? 'old' : 'new') + '|' + location.pathname + '|' + document.readyState + '|' + !!document.querySelector('article, [data-testid=primaryColumn] [data-testid$=follow]')") { d.complete(it?.trim('"')) }
            val r = d.await().orEmpty().split('|')
            if (r.size == 4 && r[0] == "new" && r[1].contains(fragment, ignoreCase = true) && r[2] != "loading" && r[3] == "true") return true
        }
        return false
    }

    /**
     * Posts (or replies) through the website's own composer, prefilled by its share link, then
     * presses Post. Returns whether it went.
     */
    suspend fun compose(text: String, replyToId: String?): Boolean = lock.withLock {
        // A prefilled intent does not guarantee the website attached the parent post.
        // Never submit a reply until we can verify its parent before posting.
        if (replyToId != null) return@withLock false
        withContext(Dispatchers.Main) {
            val v = view()
            val url = "https://x.com/intent/post?text=" + java.net.URLEncoder.encode(text, "UTF-8") + (replyToId?.let { "&in_reply_to=$it" } ?: "")
            v.loadUrl(url)
            val d = CompletableDeferred<Boolean>()
            synchronized(XBridge) { waiting["__press"] = CompletableDeferred<JsonObject>().also { w -> kotlinx.coroutines.GlobalScope.launch { d.complete((withTimeoutOrNull(20_000) { w.await() }?.get("ok") as? kotlinx.serialization.json.JsonPrimitive)?.content == "true") } } }
            v.postDelayed({
                v.evaluateJavascript("""
                    (function () { let tries = 0; const tick = () => {
                      const b = document.querySelector('[data-testid="tweetButton"]');
                      if (b && !b.disabled && b.getAttribute('aria-disabled') !== 'true') { b.click(); setTimeout(() => window.BlobfishX.onData('__press', JSON.stringify({ok: true})), 1500); }
                      else if (++tries < 60) setTimeout(tick, 250); else window.BlobfishX.onData('__press', JSON.stringify({ok: false}));
                    }; tick(); })();
                """, null)
            }, 1500)
            d.await()
        }
    }

    /** Search, newest posts first. */
    suspend fun search(query: String, cursor: String?): Page<FeedItem> {
        val root = request("SearchTimeline") { v ->
            if (cursor != null) more(v) else v.loadUrl("https://x.com/search?q=" + java.net.URLEncoder.encode(query, "UTF-8") + "&src=typed_query&f=live")
        }
        return TwitterParse.timeline(root).let { if (it.items.isEmpty() && cursor != null) it.copy(cursor = null) else it }
    }
}

private typealias AtpException = io.github.shoalapps.blobfish.data.AtpException
