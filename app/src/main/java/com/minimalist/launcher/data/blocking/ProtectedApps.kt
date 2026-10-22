package com.minimalist.launcher.data.blocking

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.AlarmClock
import android.provider.Settings
import android.provider.Telephony
import android.telecom.TelecomManager

/**
 * Apps no feature may ever block: phone, emergency calls, SMS, clock and alarms, this launcher,
 * the keyboard and the system UI. The Play Store is left alone entirely.
 */
class ProtectedApps(private val context: Context) {

    @Volatile
    var packages: Set<String> = compute()
        private set

    @Volatile
    private var keyboard: String? = currentKeyboard()

    /** Re-reads the default dialer, SMS, clock and keyboard apps. Call when packages change. */
    fun refresh() {
        keyboard = currentKeyboard()
        packages = compute()
    }

    fun defaultDialer(): String? = context.getSystemService(TelecomManager::class.java)?.defaultDialerPackage

    fun defaultSms(): String? = runCatching { Telephony.Sms.getDefaultSmsPackage(context) }.getOrNull()

    /**
     * Windows that float over the real foreground app (notification shade, keyboard, system
     * dialogs). They neither end a session nor count as an open.
     */
    fun isOverlay(packageName: String): Boolean = packageName in OVERLAYS || packageName == keyboard

    private fun compute(): Set<String> = buildSet {
        add(context.packageName)
        addAll(KNOWN)
        context.getSystemService(TelecomManager::class.java)?.defaultDialerPackage?.let(::add)
        runCatching { Telephony.Sms.getDefaultSmsPackage(context) }.getOrNull()?.let(::add)
        currentKeyboard()?.let(::add)
        handlersOf(Intent(Intent.ACTION_DIAL))
        handlersOf(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:")))
        handlersOf(Intent(AlarmClock.ACTION_SHOW_ALARMS))
        handlersOf(Intent(AlarmClock.ACTION_SET_ALARM))
    }

    @Suppress("DEPRECATION") // The flags overload needs API 33; minSdk is 31.
    private fun MutableSet<String>.handlersOf(intent: Intent) {
        context.packageManager
            .queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
            .mapNotNullTo(this) { it.activityInfo?.packageName }
    }

    private fun currentKeyboard(): String? =
        Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
            ?.substringBefore('/')
            ?.takeIf { it.isNotBlank() }

    companion object {
        private val OVERLAYS = setOf("android", "com.android.systemui")

        val KNOWN = setOf(
            "android",
            "com.android.systemui",
            // Calls and emergency
            "com.android.phone",
            "com.android.server.telecom",
            "com.android.incallui",
            "com.android.dialer",
            "com.google.android.dialer",
            "com.android.contacts",
            "com.android.emergency",
            "com.oplus.sos",
            "com.coloros.sos",
            // SMS
            "com.android.mms",
            "com.android.mms.service",
            "com.android.messaging",
            "com.google.android.apps.messaging",
            // Clock and alarms
            "com.android.deskclock",
            "com.google.android.deskclock",
            "com.coloros.alarmclock",
            "com.oplus.alarmclock",
            // Never blocked or monitored
            "com.android.vending",
        )
    }
}
