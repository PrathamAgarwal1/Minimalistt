package com.minimalist.launcher.modes

import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings

/**
 * Turns the screen grey through the colour correction setting. Needs WRITE_SECURE_SETTINGS,
 * granted once over adb; without it nothing happens.
 */
object Grayscale {

    private const val ENABLED = "accessibility_display_daltonizer_enabled"
    private const val MODE = "accessibility_display_daltonizer"
    private const val MONOCHROME = 0

    const val GRANT_COMMAND = "adb shell pm grant com.minimalist.launcher android.permission.WRITE_SECURE_SETTINGS"

    fun canControl(context: Context): Boolean =
        context.checkSelfPermission("android.permission.WRITE_SECURE_SETTINGS") == PackageManager.PERMISSION_GRANTED

    fun isOn(context: Context): Boolean {
        val resolver = context.contentResolver
        return Settings.Secure.getInt(resolver, ENABLED, 0) == 1 && Settings.Secure.getInt(resolver, MODE, -1) == MONOCHROME
    }

    fun set(context: Context, on: Boolean): Boolean = canControl(context) && runCatching {
        val resolver = context.contentResolver
        if (on) Settings.Secure.putInt(resolver, MODE, MONOCHROME)
        Settings.Secure.putInt(resolver, ENABLED, if (on) 1 else 0)
    }.getOrDefault(false)
}
