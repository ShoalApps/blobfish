package io.github.shoalapps.blobfish.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import io.github.shoalapps.blobfish.AppLanguage

fun openOriginalMedia(context: Context, url: String) {
    if (Uri.parse(url).host in setOf("x.com", "www.x.com", "twitter.com", "www.twitter.com")) {
        context.startActivity(Intent(context, OriginalMediaActivity::class.java).putExtra("url", url))
    } else openLink(context, url)
}

/** Twitter's own player handles Spaces, broadcasts and cards without exposing account cookies. */
class OriginalMediaActivity : ComponentActivity() {
    override fun attachBaseContext(base: Context) { super.attachBaseContext(AppLanguage.wrap(base)) }
    private lateinit var web: WebView
    private lateinit var root: FrameLayout
    private var fullscreen: View? = null
    private var fullscreenCallback: WebChromeClient.CustomViewCallback? = null
    private fun exitFullscreen() {
        fullscreen?.let { root.removeView(it) }
        fullscreen = null
        web.visibility = View.VISIBLE
        fullscreenCallback?.onCustomViewHidden()
        fullscreenCallback = null
    }
    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val url = intent.getStringExtra("url") ?: return finish()
        if (Uri.parse(url).scheme != "https" || Uri.parse(url).host !in setOf("x.com", "www.x.com", "twitter.com", "www.twitter.com")) return finish()
        root = FrameLayout(this)
        val column = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        column.addView(Button(this).apply { text = AppLanguage.translate("Close"); setOnClickListener { finish() } })
        web = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = true
            // Same cookie store and Chrome identity as sign-in, in a separate visible view.
            settings.userAgentString = XView.get(this@OriginalMediaActivity).settings.userAgentString
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    val host = request.url.host.orEmpty()
                    return request.url.scheme != "https" || !(host == "x.com" || host.endsWith(".x.com") || host == "twitter.com" || host.endsWith(".twitter.com") || host == "t.co")
                }
            }
            webChromeClient = object : WebChromeClient() {
                override fun onShowCustomView(view: View, callback: CustomViewCallback) {
                    if (fullscreen != null) { callback.onCustomViewHidden(); return }
                    fullscreen = view; fullscreenCallback = callback
                    web.visibility = View.GONE
                    root.addView(view, FrameLayout.LayoutParams(-1, -1))
                }
                override fun onHideCustomView() = exitFullscreen()
            }
        }
        column.addView(web, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(column, FrameLayout.LayoutParams(-1, -1))
        setContentView(root)
        web.loadUrl(url)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { if (fullscreen != null) exitFullscreen() else if (web.canGoBack()) web.goBack() else finish() }
        })
    }
    override fun onResume() { super.onResume(); if (::web.isInitialized) web.onResume() }
    override fun onPause() {
        if (::web.isInitialized) { web.evaluateJavascript("document.querySelectorAll('video,audio').forEach(m=>m.pause())", null); web.onPause() }
        super.onPause()
    }
    override fun onDestroy() {
        if (::web.isInitialized) { exitFullscreen(); (web.parent as? ViewGroup)?.removeView(web); web.destroy() }
        super.onDestroy()
    }
}
