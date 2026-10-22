package com.minimalist.launcher.data.apps

import android.content.ComponentName
import android.content.Context
import android.content.pm.LauncherApps
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import com.minimalist.launcher.data.db.AppPrefsDao
import com.minimalist.launcher.data.db.AppPrefsEntity
import com.minimalist.launcher.domain.FavouriteOrder
import com.minimalist.launcher.domain.LaunchableApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class AppRepository(
    private val context: Context,
    private val dao: AppPrefsDao,
    scope: CoroutineScope,
) {
    private val launcherApps = context.getSystemService(LauncherApps::class.java)
    private val userManager = context.getSystemService(UserManager::class.java)
    private val writeLock = Mutex()

    /** Every launchable activity across profiles; null until the first load finishes. */
    val installedApps: StateFlow<List<LaunchableApp>?> = packageChanges()
        .map { loadApps() }
        .flowOn(Dispatchers.IO)
        .stateIn(scope, SharingStarted.Eagerly, null)

    val prefs: Flow<List<AppPrefsEntity>> = dao.observeAll()

    /** Adds [app] to favourites. Returns false if the favourites list is already full. */
    suspend fun setFavourite(app: LaunchableApp, favourite: Boolean): Boolean = writeLock.withLock {
        val current = dao.get(app.key) ?: AppPrefsEntity(app.key, app.packageName)
        if (current.favourite == favourite) return@withLock true
        if (favourite && dao.favouriteCount() >= FavouriteOrder.MAX_FAVOURITES) return@withLock false
        save(current.copy(favourite = favourite, hidden = current.hidden && !favourite))
        true
    }

    /** Hidden apps never show in search, so hiding also removes the app from favourites. */
    suspend fun setHidden(appKey: String, packageName: String, hidden: Boolean) = writeLock.withLock {
        val current = dao.get(appKey) ?: AppPrefsEntity(appKey, packageName)
        save(current.copy(hidden = hidden, favourite = current.favourite && !hidden))
    }

    fun launch(app: LaunchableApp): Boolean {
        val component = ComponentName.unflattenFromString(app.key) ?: return false
        return runCatching {
            launcherApps.startMainActivity(component, userFor(app), null, null)
            true
        }.getOrElse { false }
    }

    fun openAppInfo(app: LaunchableApp) {
        val component = ComponentName.unflattenFromString(app.key) ?: return
        runCatching { launcherApps.startAppDetailsActivity(component, userFor(app), null, null) }
    }

    private suspend fun save(entity: AppPrefsEntity) {
        dao.upsert(entity)
        dao.deleteIfEmpty(entity.appKey)
    }

    private fun userFor(app: LaunchableApp): UserHandle =
        userManager.getUserForSerialNumber(app.userSerial) ?: Process.myUserHandle()

    private fun loadApps(): List<LaunchableApp> {
        val self = context.packageName
        val me = Process.myUserHandle()
        val seen = HashSet<String>()
        // Main profile first, so a clone of an app already listed is skipped.
        return launcherApps.profiles
            .sortedBy { if (it == me) 0 else 1 }
            .flatMap { user -> launcherApps.getActivityList(null, user).map { it to user } }
            .mapNotNull { (info, user) ->
                val component = info.componentName
                val key = component.flattenToString()
                if (component.packageName == self || !seen.add(key)) return@mapNotNull null
                LaunchableApp(
                    key = key,
                    packageName = component.packageName,
                    label = info.label.toString().trim().ifEmpty { component.packageName },
                    userSerial = userManager.getSerialNumberForUser(user),
                )
            }
            .sortedBy { it.label.lowercase() }
    }

    private fun packageChanges(): Flow<Unit> = callbackFlow {
        val callback = object : LauncherApps.Callback() {
            override fun onPackageRemoved(packageName: String, user: UserHandle) {
                trySend(Unit)
            }

            override fun onPackageAdded(packageName: String, user: UserHandle) {
                trySend(Unit)
            }

            override fun onPackageChanged(packageName: String, user: UserHandle) {
                trySend(Unit)
            }

            override fun onPackagesAvailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) {
                trySend(Unit)
            }

            override fun onPackagesUnavailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) {
                trySend(Unit)
            }
        }
        trySend(Unit)
        launcherApps.registerCallback(callback, Handler(Looper.getMainLooper()))
        awaitClose { launcherApps.unregisterCallback(callback) }
    }.conflate()
}
