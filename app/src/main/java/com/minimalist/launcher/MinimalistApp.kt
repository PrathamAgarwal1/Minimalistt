package com.minimalist.launcher

import android.app.Application
import com.minimalist.launcher.debug.DebugTools

class MinimalistApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        DebugTools.install(this)
    }
}
