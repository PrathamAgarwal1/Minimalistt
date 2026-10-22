package com.minimalist.launcher.debug

import android.content.Context
import android.content.IntentFilter
import com.minimalist.launcher.MinimalistApp

/**
 * Debug builds: `adb shell am broadcast -a <applicationId>.DEBUG_RESET` clears every block.
 * The plain command sends an implicit broadcast, which only a receiver registered at runtime
 * hears, so one is registered here as well as in the debug manifest.
 */
object DebugTools {

    fun resetAction(context: Context) = "${context.packageName}.DEBUG_RESET"

    fun install(app: MinimalistApp) {
        app.registerReceiver(
            DebugResetReceiver(),
            IntentFilter(resetAction(app)),
            DUMP_PERMISSION,
            null,
            Context.RECEIVER_EXPORTED,
        )
    }

    private const val DUMP_PERMISSION = "android.permission.DUMP"
}
