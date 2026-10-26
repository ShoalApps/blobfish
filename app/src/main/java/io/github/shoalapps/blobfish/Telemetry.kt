package io.github.shoalapps.blobfish

import android.app.Activity
import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.sentry.Sentry
import io.sentry.android.core.SentryAndroid

/**
 * Error reports, only with your agreement. Crashes and errors go to Shoal's Sentry project so bugs
 * can be fixed: the error, the app and Android version and the phone model. No account details,
 * no names, no history, no screenshots, no IP address kept. Off unless you say yes; change it in
 * Settings. See https://shoalapps.github.io/privacy.html
 */
object Telemetry {
    private const val PREFS = "telemetry"
    /** Whether reports are on (for the Settings switch). */
    var enabled by mutableStateOf(false)
        private set

    private fun prefs(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Reporting is only possible in builds made with Shoal's Sentry address. */
    val available get() = BuildConfig.SENTRY_DSN.isNotBlank()

    fun init(app: Application) {
        enabled = available && prefs(app).getBoolean("consent", false)
        if (enabled) start(app)
    }

    private fun start(app: Context) {
        SentryAndroid.init(app) { o ->
            o.dsn = BuildConfig.SENTRY_DSN
            o.isSendDefaultPii = false
            o.isAttachScreenshot = false
            o.isAttachViewHierarchy = false
            o.isEnableUserInteractionBreadcrumbs = false
            o.isEnableUserInteractionTracing = false
            o.maxBreadcrumbs = 30
            o.release = "blobfish@" + BuildConfig.VERSION_NAME
            o.environment = if (BuildConfig.DEBUG) "debug" else "release"
            o.setTag("app", "blobfish")
            // Strip anything that could identify the person.
            o.setBeforeSend { event, _ -> event.user = null; event.serverName = null; event }
        }
    }

    fun setEnabled(context: Context, on: Boolean) {
        prefs(context).edit().putBoolean("consent", on).putBoolean("asked", true).apply()
        enabled = on && available
        if (enabled) start(context.applicationContext) else Sentry.close()
    }

    /** A handled error worth knowing about (does nothing unless reports are on). */
    fun report(e: Throwable) { if (enabled) Sentry.captureException(e) }

    /** Asks once, on first launch: send error reports? */
    fun askOnce(activity: Activity) {
        if (!available || prefs(activity).getBoolean("asked", false)) return
        android.app.AlertDialog.Builder(activity, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("Help fix bugs?")
            .setMessage("Blobfish can send anonymous crash and error reports to Shoal (through Sentry) so problems get fixed. They include the error, the app and Android version and the phone model, never your accounts, history or anything you've looked at. You can change this in Settings.")
            .setPositiveButton("Send reports") { _, _ -> setEnabled(activity, true) }
            .setNegativeButton("No thanks") { _, _ -> setEnabled(activity, false) }
            .setNeutralButton("Privacy policy") { _, _ ->
                runCatching { activity.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://shoalapps.github.io/privacy.html"))) }
            }
            .setCancelable(false)
            .show()
    }
}
