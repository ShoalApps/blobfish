package io.github.shoalapps.blobfish.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView

/**
 * X (Twitter) as its own website, signed in on X's own login page. X's API is paid and scraping it
 * breaks its terms, so this shows x.com as a browser would. One view for the app's lifetime, so
 * switching tabs keeps your place.
 */
object XView {
    private var view: WebView? = null
    private val hosts = listOf("x.com", "twitter.com", "twimg.com", "t.co", "abs.twimg.com")
    private fun owns(host: String) = hosts.any { host == it || host.endsWith(".$it") }

    /** x.com serves its full mobile site to Chrome but a limited one to in-app browsers. */
    private fun chromeAgent(webView: String, wide: Boolean): String {
        if (wide) {
            // Its phone layout breaks on tablets and Chromebooks; the desktop site fits them.
            val chrome = Regex("Chrome/([\\d.]+)").find(webView)?.groupValues?.get(1) ?: "130.0.0.0"
            return "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/$chrome Safari/537.36"
        }
        return webView.replace("; wv)", ")").replace(Regex(" Version/\\d+\\.\\d+"), "")
    }

    @SuppressLint("SetJavaScriptEnabled")
    fun get(context: Context): WebView = view ?: WebView(context.applicationContext).apply {
        if (context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0) WebView.setWebContentsDebuggingEnabled(true)
        setBackgroundColor(android.graphics.Color.BLACK)
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.mediaPlaybackRequiresUserGesture = false
        val wide = context.resources.configuration.screenWidthDp >= 900
        settings.userAgentString = chromeAgent(settings.userAgentString, wide)
        settings.useWideViewPort = wide
        settings.loadWithOverviewMode = wide
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
        webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val uri = request.url
                if (uri.scheme == "https" && owns(uri.host.orEmpty())) return false
                runCatching { view.context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                return true
            }

            override fun onPageFinished(view: WebView, url: String?) { CookieManager.getInstance().flush() }
        }
        webChromeClient = WebChromeClient()
        // "Call it Twitter": the bird and the old name on X's own pages too.
        if (androidx.webkit.WebViewFeature.isFeatureSupported(androidx.webkit.WebViewFeature.DOCUMENT_START_SCRIPT)) {
            val twitter = io.github.shoalapps.blobfish.Graph.store.settings.value.twitterNames
            androidx.webkit.WebViewCompat.addDocumentStartJavaScript(this, "window.__blobfishTwitter = $twitter;\n" + TWITTER_SCRIPT, setOf("https://x.com", "https://twitter.com"))
        }
        loadUrl("https://x.com/home")
        view = this
    }

    fun open(context: Context, url: String) = get(context).loadUrl(url)

    /** "Call it Twitter" changed: the page's start script is fixed when the view is made, so make it again. */
    fun rebrand() {
        view?.let { (it.parent as? ViewGroup)?.removeView(it); it.destroy() }
        view = null
    }

    /**
     * Runs on every x.com page; does nothing unless "Call it Twitter" is on (Blobfish sets
     * window.__blobfishTwitter before the page loads). Swaps X's logo for the bird and "X" in the
     * page title for "Twitter".
     */
    private val TWITTER_SCRIPT = """
        (function () {
          if (!window.__blobfishTwitter || window.__blobfishTwitterRan) return;
          window.__blobfishTwitterRan = true;
          const bird = "M21.543 7.104c.015.211.015.423.015.636 0 6.507-4.954 14.01-14.01 14.01v-.003A13.94 13.94 0 0 1 0 19.539a9.88 9.88 0 0 0 7.287-2.041 4.93 4.93 0 0 1-4.6-3.42 4.916 4.916 0 0 0 2.223-.084A4.926 4.926 0 0 1 .96 9.167v-.062a4.887 4.887 0 0 0 2.235.616A4.928 4.928 0 0 1 1.67 3.148 13.98 13.98 0 0 0 11.82 8.292a4.929 4.929 0 0 1 8.39-4.49 9.868 9.868 0 0 0 3.128-1.196 4.941 4.941 0 0 1-2.165 2.724A9.828 9.828 0 0 0 24 4.555a10.019 10.019 0 0 1-2.457 2.549z";
          function swap() {
            // X's logo, in the shapes it has used: matched by its start, since X redraws it now and then.
            const logo = /^(m21\.[67]\d* 21\.[57]|M21\.742 21\.75|M18\.244 2\.25|M18\.901 1\.153)/i;
            for (const p of document.querySelectorAll('svg path')) {
              if (!logo.test(p.getAttribute('d') || '')) continue;
              const svg = p.closest('svg');
              svg.setAttribute('viewBox', '0 0 24 24');
              p.setAttribute('d', bird);
              svg.style.color = '#1D9BF0'; svg.style.fill = '#1D9BF0';
            }
            const t = document.title.replace(/^X - The Everything App/, 'Twitter').replace(/ \/ X${'$'}/, ' / Twitter');
            if (t !== document.title) document.title = t;
          }
          const start = () => { swap(); new MutationObserver(swap).observe(document.documentElement, { childList: true, subtree: true }); };
          if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', start); else start();
        })();
    """.trimIndent()

    val signedIn: Boolean get() = CookieManager.getInstance().getCookie("https://x.com")?.contains("auth_token=") == true

    fun signOut() {
        val cm = CookieManager.getInstance()
        cm.getCookie("https://x.com")?.split(';')?.map { it.substringBefore('=').trim() }?.forEach { name ->
            cm.setCookie("https://x.com", "$name=; Max-Age=0; Path=/; Domain=.x.com")
        }
        cm.flush()
        view?.loadUrl("https://x.com/home")
    }
}

@Composable
fun XScreen() {
    val context = LocalContext.current
    val web = remember { XView.get(context) }
    var canGoBack by remember { mutableStateOf(false) }
    BackHandler(enabled = canGoBack) { web.goBack() }
    DisposableEffect(Unit) {
        web.onResume()
        val tick = object : Runnable { override fun run() { canGoBack = web.canGoBack(); web.postDelayed(this, 500) } }
        web.post(tick)
        onDispose {
            web.removeCallbacks(tick)
            web.evaluateJavascript("document.querySelectorAll('video').forEach(v=>v.pause())", null)
            web.onPause()
        }
    }
    // The page sits in a holder of this screen's own. During a tab change the next X screen can
    // take the page before this one is cleaned up, so this one only lets go of its own holder;
    // taking the page back would leave it detached, blank and at zero size.
    AndroidView(
        factory = { ctx ->
            android.widget.FrameLayout(ctx).apply {
                (web.parent as? ViewGroup)?.removeView(web)
                addView(web, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            }
        },
        onRelease = { holder -> if (web.parent === holder) holder.removeView(web) },
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
    )
}
