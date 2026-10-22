package com.minimalist.launcher

import android.app.Application

class MinimalistApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
