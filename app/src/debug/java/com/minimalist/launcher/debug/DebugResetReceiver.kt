package com.minimalist.launcher.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.minimalist.launcher.MinimalistApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Clears every block, schedule, limit and today's counters. Debug builds only. */
class DebugResetReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != DebugTools.resetAction(context)) return
        val container = (context.applicationContext as MinimalistApp).container
        val pending = goAsync()
        container.applicationScope.launch {
            try {
                container.blocking.clearEverything()
                container.blockNotice.value = null
                withContext(Dispatchers.Main) {
                    Toast.makeText(context.applicationContext, "Minimalist: all blocks cleared", Toast.LENGTH_SHORT).show()
                }
            } finally {
                pending.finish()
            }
        }
    }
}
