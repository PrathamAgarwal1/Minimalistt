package com.minimalist.launcher.blocking

import android.view.accessibility.AccessibilityNodeInfo

/**
 * Keeps Android Settings usable for Wi-Fi, sound and the rest, but closes the pages that
 * could switch protection off: Quiet's own app info, accessibility and notification
 * access, Device Admin, the uninstall prompt, Date & time and Developer options.
 */
object SettingsGuard {

    val PACKAGES = setOf(
        "com.android.settings",
        "com.coloros.settings",
        "com.oplus.settings",
        "com.coloros.safecenter",
        "com.oplus.safecenter",
        "com.coloros.securitypermission",
        "com.android.permissioncontroller",
        "com.google.android.permissioncontroller",
        "com.android.packageinstaller",
        "com.google.android.packageinstaller",
    )

    private const val APP_NAME = "quiet"

    /** Words that, next to Quiet's name, mean the page can stop or remove it. */
    private val WITH_NAME = listOf(
        "uninstall",
        "force stop",
        "deactivate",
        "clear data",
        "clear storage",
        "storage",
        "quiet blocking",
        "quiet notifications",
        "device admin",
    )

    /** Pages closed whatever they mention. */
    private val ALWAYS = listOf(
        "set time automatically",
        "set date automatically",
        "automatic date & time",
        "automatic date and time",
        "use network-provided time",
        "usb debugging",
        "wireless debugging",
    )

    fun isGuarded(pageText: String): Boolean {
        val text = pageText.lowercase()
        if (ALWAYS.any { it in text }) return true
        return APP_NAME in text && WITH_NAME.any { it in text }
    }

    /** All text and descriptions on screen, read breadth-first and capped. */
    fun visibleText(root: AccessibilityNodeInfo, limit: Int = 400): String {
        val out = StringBuilder()
        val queue = ArrayDeque<AccessibilityNodeInfo>().apply { add(root) }
        var seen = 0
        while (queue.isNotEmpty() && seen < limit) {
            val node = queue.removeFirst()
            seen++
            node.text?.let { out.append(it).append('\n') }
            node.contentDescription?.let { out.append(it).append('\n') }
            for (i in 0 until node.childCount) node.getChild(i)?.let(queue::addLast)
        }
        return out.toString()
    }
}
