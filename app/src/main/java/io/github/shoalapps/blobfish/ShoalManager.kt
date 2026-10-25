package io.github.shoalapps.blobfish

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Shoal installs and updates every Shoal app (silently with Shizuku or root). When
 * it's installed it takes over updates; "Check for updates" opens it on this app.
 */
object ShoalManager {
    const val PACKAGE = "io.github.shoalapps.shoal"
    private const val ACTION = "io.github.shoalapps.shoal.CHECK_UPDATES"
    private const val DOWNLOAD = "https://github.com/ShoalApps/shoal/releases/latest/download/Shoal.apk"

    fun installed(context: Context): Boolean =
        runCatching { context.packageManager.getPackageInfo(PACKAGE, 0) }.isSuccess

    /** Opens the Manager on this app, or offers to get it. */
    fun checkForUpdates(context: Context) {
        if (installed(context)) {
            runCatching {
                context.startActivity(
                    Intent(ACTION).setPackage(PACKAGE).putExtra("package", context.packageName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }.onSuccess { return }
        }
        AlertDialog.Builder(context, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("Get Shoal")
            .setMessage("Shoal installs and updates all the Shoal apps, and can do it silently with Shizuku or root.")
            .setPositiveButton("Download") { _, _ -> runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(DOWNLOAD))) } }
            .setNegativeButton("Not now", null)
            .show()
    }
}
