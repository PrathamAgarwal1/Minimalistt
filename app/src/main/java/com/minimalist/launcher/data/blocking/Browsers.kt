package com.minimalist.launcher.data.blocking

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri

/** Every app that opens web links. Only one of them is allowed when a browser is chosen. */
class Browsers(private val context: Context) {

    @Volatile
    var packages: Set<String> = compute()
        private set

    fun refresh() {
        packages = compute()
    }

    @Suppress("DEPRECATION") // The flags overload needs API 33; minSdk is 31.
    private fun compute(): Set<String> = runCatching {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com")).addCategory(Intent.CATEGORY_BROWSABLE)
        context.packageManager.queryIntentActivities(intent, PackageManager.MATCH_ALL)
            .mapNotNull { it.activityInfo?.packageName }
            .filter { it != context.packageName && it != "android" }
            .toSet()
    }.getOrDefault(emptySet())
}
