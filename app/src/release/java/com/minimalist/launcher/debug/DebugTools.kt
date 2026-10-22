package com.minimalist.launcher.debug

import com.minimalist.launcher.MinimalistApp

/** Release builds have no debug reset. */
object DebugTools {
    @Suppress("UNUSED_PARAMETER")
    fun install(app: MinimalistApp) = Unit
}
