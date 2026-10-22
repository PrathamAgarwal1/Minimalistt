package com.minimalist.launcher

import android.app.Application
import com.minimalist.launcher.debug.DebugTools
import com.minimalist.launcher.system.TrustedClock

class MinimalistApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        TrustedClock.init(this)
        container = AppContainer(this)
        DebugTools.install(this)
    }
}
