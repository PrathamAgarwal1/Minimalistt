package com.minimalist.launcher

import android.content.Context
import com.minimalist.launcher.data.apps.AppRepository
import com.minimalist.launcher.data.db.AppDatabase
import com.minimalist.launcher.data.settings.SettingsRepository
import com.minimalist.launcher.data.settings.settingsDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Process-wide singletons, built once in [MinimalistApp]. */
class AppContainer(context: Context) {

    val appContext: Context = context.applicationContext

    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val database: AppDatabase = AppDatabase.build(appContext)

    val settings = SettingsRepository(appContext.settingsDataStore)

    val apps = AppRepository(appContext, database.appPrefsDao(), applicationScope)
}
