package com.minimalist.launcher.data.blocking

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import com.minimalist.launcher.domain.websites.BrowserBars

/**
 * Every real browser. Only one of them is allowed when a browser is chosen. Apps that merely
 * open some links (office apps, readers, shops) don't count: a browser opens any web address and
 * says it's a browser, or is one Quiet already knows.
 */
class Browsers(private val context: Context) {

    @Volatile
    var packages: Set<String> = compute()
        private set

    fun refresh() {
        packages = compute()
    }

    @Suppress("DEPRECATION") // The flags overload needs API 33; minSdk is 31.
    private fun compute(): Set<String> = runCatching {
        val pm = context.packageManager
        val anyAddress = Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com")).addCategory(Intent.CATEGORY_BROWSABLE)
        val openAnything = pm.queryIntentActivities(anyAddress, PackageManager.MATCH_ALL or PackageManager.GET_RESOLVED_FILTER)
            .filter { info ->
                val filter = info.filter
                filter == null || (filter.countDataAuthorities() == 0 && filter.countDataPaths() == 0 && filter.countDataTypes() == 0)
            }
            .mapNotNull { it.activityInfo?.packageName }
            .toSet()
        val saysBrowser = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_BROWSER), PackageManager.MATCH_ALL)
            .mapNotNull { it.activityInfo?.packageName }
            .toSet()
        openAnything
            .filter { it in saysBrowser || it in BrowserBars.VIEW_IDS }
            .filter { it != context.packageName && it != "android" }
            .toSet()
    }.getOrDefault(emptySet())
}
