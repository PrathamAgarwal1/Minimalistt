package com.minimalist.launcher.data.modes

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.minimalist.launcher.data.db.AppDatabase
import com.minimalist.launcher.data.db.HabitEntity
import com.minimalist.launcher.data.db.ModeLogEntity
import com.minimalist.launcher.data.db.ReflectionEntity
import com.minimalist.launcher.domain.modes.ActiveLock
import com.minimalist.launcher.domain.modes.DayModeConfig
import com.minimalist.launcher.domain.modes.DayModes
import com.minimalist.launcher.domain.modes.StudyWindow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.Duration
import java.time.LocalDate

val Context.modesDataStore: DataStore<Preferences> by preferencesDataStore(name = "modes")

/** Everything about Put away, morning, night, notification batching and urgent messages. */
data class ModesState(
    val putAwayUntil: Long? = null,
    val morningStartedAt: Long? = null,
    val lastMorningDay: Long? = null,
    val dayModes: DayModeConfig = DayModeConfig(),
    /** Put away after unplugging from USB, for this many minutes. 0 is off. */
    val unplugMinutes: Int = 0,
    /** Put away after this many pickups in an hour. 0 is off. */
    val pickupThreshold: Int = 0,
    val pickupMinutes: Int = 30,
    val studyWindows: List<StudyWindow> = emptyList(),
    /** The end of the last study window that already started Put away. */
    val studyHandledUntil: Long? = null,
    /** Apps that keep working in every mode, e.g. UPI. Calls, SMS and clock always do. */
    val essentials: Set<String> = emptySet(),
    val audioApp: String? = null,
    val notesApp: String? = null,
    val batchingEnabled: Boolean = false,
    /** Minutes of the day when batched notifications arrive. */
    val batchTimes: List<Int> = listOf(13 * 60, 20 * 60),
    val lastBatchAt: Long? = null,
    val urgentPerSession: Int = 3,
    val allowedBrowser: String? = null,
)

class ModesRepository(
    private val dataStore: DataStore<Preferences>,
    private val database: AppDatabase,
    scope: CoroutineScope,
) {
    private val dao = database.modesDao()

    private object Keys {
        val putAwayUntil = longPreferencesKey("put_away_until")
        val morningStartedAt = longPreferencesKey("morning_started_at")
        val lastMorningDay = longPreferencesKey("last_morning_day")
        val morningEnabled = booleanPreferencesKey("morning_enabled")
        val wakeWeekday = intPreferencesKey("wake_weekday")
        val wakeWeekend = intPreferencesKey("wake_weekend")
        val morningMinutes = intPreferencesKey("morning_minutes")
        val nightEnabled = booleanPreferencesKey("night_enabled")
        val bedWeekday = intPreferencesKey("bed_weekday")
        val bedWeekend = intPreferencesKey("bed_weekend")
        val unplugMinutes = intPreferencesKey("unplug_minutes")
        val pickupThreshold = intPreferencesKey("pickup_threshold")
        val pickupMinutes = intPreferencesKey("pickup_minutes")
        val studyWindows = stringPreferencesKey("study_windows")
        val studyHandledUntil = longPreferencesKey("study_handled_until")
        val essentials = stringSetPreferencesKey("essentials")
        val audioApp = stringPreferencesKey("audio_app")
        val notesApp = stringPreferencesKey("notes_app")
        val batchingEnabled = booleanPreferencesKey("batching_enabled")
        val batchTimes = stringPreferencesKey("batch_times")
        val lastBatchAt = longPreferencesKey("last_batch_at")
        val urgentPerSession = intPreferencesKey("urgent_per_session")
        val allowedBrowser = stringPreferencesKey("allowed_browser")
    }

    val state: StateFlow<ModesState> = dataStore.data.map { prefs ->
        val defaults = ModesState()
        ModesState(
            putAwayUntil = prefs[Keys.putAwayUntil],
            morningStartedAt = prefs[Keys.morningStartedAt],
            lastMorningDay = prefs[Keys.lastMorningDay],
            dayModes = DayModeConfig(
                morningEnabled = prefs[Keys.morningEnabled] ?: defaults.dayModes.morningEnabled,
                wakeWeekday = prefs[Keys.wakeWeekday] ?: defaults.dayModes.wakeWeekday,
                wakeWeekend = prefs[Keys.wakeWeekend] ?: defaults.dayModes.wakeWeekend,
                morningMinutes = prefs[Keys.morningMinutes] ?: defaults.dayModes.morningMinutes,
                nightEnabled = prefs[Keys.nightEnabled] ?: defaults.dayModes.nightEnabled,
                bedWeekday = prefs[Keys.bedWeekday] ?: defaults.dayModes.bedWeekday,
                bedWeekend = prefs[Keys.bedWeekend] ?: defaults.dayModes.bedWeekend,
            ),
            unplugMinutes = prefs[Keys.unplugMinutes] ?: defaults.unplugMinutes,
            pickupThreshold = prefs[Keys.pickupThreshold] ?: defaults.pickupThreshold,
            pickupMinutes = prefs[Keys.pickupMinutes] ?: defaults.pickupMinutes,
            studyWindows = StudyWindow.decode(prefs[Keys.studyWindows]),
            studyHandledUntil = prefs[Keys.studyHandledUntil],
            essentials = prefs[Keys.essentials] ?: emptySet(),
            audioApp = prefs[Keys.audioApp],
            notesApp = prefs[Keys.notesApp],
            batchingEnabled = prefs[Keys.batchingEnabled] ?: defaults.batchingEnabled,
            batchTimes = prefs[Keys.batchTimes]?.split(',')?.mapNotNull { it.toIntOrNull() }?.sorted() ?: defaults.batchTimes,
            lastBatchAt = prefs[Keys.lastBatchAt],
            urgentPerSession = prefs[Keys.urgentPerSession] ?: defaults.urgentPerSession,
            allowedBrowser = prefs[Keys.allowedBrowser],
        )
    }.stateIn(scope, SharingStarted.Eagerly, ModesState())

    /** Starts Put away, or adds to the time left. It never ends early. */
    suspend fun putAway(duration: Duration, nowMillis: Long = System.currentTimeMillis()) {
        dataStore.edit { it[Keys.putAwayUntil] = DayModes.extendPutAway(it[Keys.putAwayUntil], nowMillis, duration) }
    }

    /** For automatic starts: makes sure Put away lasts until at least [untilMillis], without stacking. */
    suspend fun putAwayUntilAtLeast(untilMillis: Long) {
        dataStore.edit { it[Keys.putAwayUntil] = maxOf(it[Keys.putAwayUntil] ?: 0L, untilMillis) }
    }

    suspend fun startMorning(nowMillis: Long, day: Long) {
        dataStore.edit {
            it[Keys.morningStartedAt] = nowMillis
            it[Keys.lastMorningDay] = day
        }
    }

    suspend fun setStudyHandledUntil(millis: Long) = edit { it[Keys.studyHandledUntil] = millis }

    suspend fun setLastBatchAt(millis: Long) = edit { it[Keys.lastBatchAt] = millis }

    suspend fun setDayModes(config: DayModeConfig) = edit {
        it[Keys.morningEnabled] = config.morningEnabled
        it[Keys.wakeWeekday] = config.wakeWeekday
        it[Keys.wakeWeekend] = config.wakeWeekend
        it[Keys.morningMinutes] = config.morningMinutes
        it[Keys.nightEnabled] = config.nightEnabled
        it[Keys.bedWeekday] = config.bedWeekday
        it[Keys.bedWeekend] = config.bedWeekend
    }

    suspend fun setUnplugMinutes(minutes: Int) = edit { it[Keys.unplugMinutes] = minutes }

    suspend fun setPickups(threshold: Int, minutes: Int) = edit {
        it[Keys.pickupThreshold] = threshold
        it[Keys.pickupMinutes] = minutes
    }

    suspend fun setStudyWindows(windows: List<StudyWindow>) = edit { it[Keys.studyWindows] = StudyWindow.encode(windows) }

    suspend fun setEssentials(packages: Set<String>) = edit { it[Keys.essentials] = packages }

    suspend fun setAudioApp(packageName: String?) = edit { prefs ->
        if (packageName == null) {
            prefs.remove(Keys.audioApp)
        } else {
            prefs[Keys.audioApp] = packageName
        }
    }

    suspend fun setNotesApp(packageName: String?) = edit { prefs ->
        if (packageName == null) {
            prefs.remove(Keys.notesApp)
        } else {
            prefs[Keys.notesApp] = packageName
        }
    }

    suspend fun setBatching(enabled: Boolean, times: List<Int>) = edit {
        it[Keys.batchingEnabled] = enabled
        it[Keys.batchTimes] = times.sorted().joinToString(",")
    }

    suspend fun setUrgentPerSession(count: Int) = edit { it[Keys.urgentPerSession] = count }

    suspend fun setAllowedBrowser(packageName: String?) = edit { prefs ->
        if (packageName == null) {
            prefs.remove(Keys.allowedBrowser)
        } else {
            prefs[Keys.allowedBrowser] = packageName
        }
    }

    /** Debug reset: ends every mode now. Settings stay. */
    suspend fun endAllModes() = edit {
        it.remove(Keys.putAwayUntil)
        it.remove(Keys.morningStartedAt)
    }

    // Mode log, so stats can show hours spent in each mode.

    /** Keeps the mode log in step with [lock]. Returns the mode that just ended, if any. */
    suspend fun syncModeLog(lock: ActiveLock?, nowMillis: Long): String? {
        val open = dao.openMode()
        if (open != null && open.kind == lock?.kind?.name) return null
        open?.let { dao.endMode(it.id, nowMillis) }
        if (lock != null) dao.insertMode(ModeLogEntity(kind = lock.kind.name, startedAt = nowMillis, endedAt = null))
        return open?.kind
    }

    suspend fun currentModeStartedAt(): Long? = dao.openMode()?.startedAt

    // Pickups

    suspend fun recordPickup(nowMillis: Long): List<Long> {
        dao.insertPickup(nowMillis)
        dao.prunePickups(nowMillis - Duration.ofDays(8).toMillis())
        return dao.pickupsSince(nowMillis - 3_600_000L)
    }

    // Reflections and habits

    fun reflection(day: Long): Flow<ReflectionEntity?> = dao.observeReflection(day)

    suspend fun saveReflection(day: Long, wentWell: String?, firstTask: String?) {
        dao.upsertReflection(ReflectionEntity(day, wentWell?.trim()?.ifEmpty { null }, firstTask?.trim()?.ifEmpty { null }))
    }

    /** Last night's "tomorrow's first task", offered as this morning's intention. */
    suspend fun yesterdaysFirstTask(): String? = dao.reflection(LocalDate.now().minusDays(1).toEpochDay())?.firstTask

    fun habitsToday(): Flow<List<Pair<HabitEntity, Boolean>>> {
        val day = LocalDate.now().toEpochDay()
        return combine(dao.observeHabits(), dao.observeChecks(day)) { habits, checks ->
            habits.map { it to (it.id in checks) }
        }
    }

    suspend fun addHabit(title: String, position: Int) {
        if (title.isNotBlank()) dao.insertHabit(HabitEntity(title = title.trim(), position = position))
    }

    suspend fun deleteHabit(id: Long) = dao.deleteHabit(id)

    suspend fun setHabitDone(id: Long, done: Boolean) {
        val day = LocalDate.now().toEpochDay()
        if (done) dao.check(day, id) else dao.uncheck(day, id)
    }

    private suspend fun edit(block: (MutablePreferences) -> Unit) {
        dataStore.edit { block(it) }
    }
}
