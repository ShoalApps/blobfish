package io.github.swordfishapps.social.ui

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
        loadUrl("https://x.com/home")
        view = this
    }

    fun open(context: Context, url: String) = get(context).loadUrl(url)

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
            (web.parent as? ViewGroup)?.removeView(web)
        }
    }
    AndroidView(factory = { (web.parent as? ViewGroup)?.removeView(web); web }, modifier = Modifier.fillMaxSize().statusBarsPadding())
}
