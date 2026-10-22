package com.minimalist.launcher.data.modes

import com.minimalist.launcher.system.TrustedClock
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

/** Everything about Put away, night, notification batching and urgent messages. */
data class ModesState(
    val putAwayUntil: Long? = null,
    /** The opening time of a night already opened early by a morning challenge. */
    val nightReleasedUntil: Long? = null,
    /** The day the morning screen is still to be shown on. */
    val morningScreenDay: Long? = null,
    val lastReminderFor: Long? = null,
    /** True while grayscale is on because of wind-down or night, so it can be put back. */
    val grayscaleByUs: Boolean = false,
    val dayModes: DayModeConfig = DayModeConfig(),
    val frictionBaseSeconds: Int = 5,
    /** Keys of [com.minimalist.launcher.domain.parts.AppParts] that are blocked, e.g. Reels. */
    val blockedParts: Set<String> = emptySet(),
    /** When the weekly review was last shown. */
    val lastReviewAt: Long? = null,
    /** False until the stored values have been read. */
    val loaded: Boolean = false,
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
        val nightReleasedUntil = longPreferencesKey("night_released_until")
        val morningScreenDay = longPreferencesKey("morning_screen_day")
        val lastReminderFor = longPreferencesKey("last_reminder_for")
        val grayscaleByUs = booleanPreferencesKey("grayscale_by_us")
        val nightEnabled = booleanPreferencesKey("night_enabled")
        val bedWeekday = intPreferencesKey("bed_weekday")
        val bedWeekend = intPreferencesKey("bed_weekend")
        val openWeekday = intPreferencesKey("open_weekday")
        val openWeekend = intPreferencesKey("open_weekend")
        val windDownMinutes = intPreferencesKey("wind_down_minutes")
        val grayscale = booleanPreferencesKey("grayscale")
        val redTint = booleanPreferencesKey("red_tint")
        val reminderMinutes = intPreferencesKey("reminder_minutes")
        val frictionBase = intPreferencesKey("friction_base_seconds")
        val blockedParts = stringSetPreferencesKey("blocked_parts")
        val lastReviewAt = longPreferencesKey("last_review_at")
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
        val allowedBrowser = stringPreferencesKey("allowed_browser")
    }

    val state: StateFlow<ModesState> = dataStore.data.map { prefs ->
        val defaults = ModesState()
        ModesState(
            putAwayUntil = prefs[Keys.putAwayUntil],
            nightReleasedUntil = prefs[Keys.nightReleasedUntil],
            morningScreenDay = prefs[Keys.morningScreenDay],
            lastReminderFor = prefs[Keys.lastReminderFor],
            grayscaleByUs = prefs[Keys.grayscaleByUs] ?: false,
            dayModes = DayModeConfig(
                nightEnabled = prefs[Keys.nightEnabled] ?: defaults.dayModes.nightEnabled,
                bedWeekday = prefs[Keys.bedWeekday] ?: defaults.dayModes.bedWeekday,
                bedWeekend = prefs[Keys.bedWeekend] ?: defaults.dayModes.bedWeekend,
                openWeekday = prefs[Keys.openWeekday] ?: defaults.dayModes.openWeekday,
                openWeekend = prefs[Keys.openWeekend] ?: defaults.dayModes.openWeekend,
                windDownMinutes = prefs[Keys.windDownMinutes] ?: defaults.dayModes.windDownMinutes,
                grayscale = prefs[Keys.grayscale] ?: defaults.dayModes.grayscale,
                redTint = prefs[Keys.redTint] ?: defaults.dayModes.redTint,
                reminderMinutes = prefs[Keys.reminderMinutes] ?: defaults.dayModes.reminderMinutes,
            ),
            frictionBaseSeconds = prefs[Keys.frictionBase] ?: defaults.frictionBaseSeconds,
            blockedParts = prefs[Keys.blockedParts] ?: emptySet(),
            lastReviewAt = prefs[Keys.lastReviewAt],
            loaded = true,
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
            allowedBrowser = prefs[Keys.allowedBrowser],
        )
    }.stateIn(scope, SharingStarted.Eagerly, ModesState())

    /** Starts Put away, or adds to the time left. It never ends early. */
    suspend fun putAway(duration: Duration, nowMillis: Long = TrustedClock.nowMillis()) {
        dataStore.edit { it[Keys.putAwayUntil] = DayModes.extendPutAway(it[Keys.putAwayUntil], nowMillis, duration) }
    }

    /** For automatic starts: makes sure Put away lasts until at least [untilMillis], without stacking. */
    suspend fun putAwayUntilAtLeast(untilMillis: Long) {
        dataStore.edit { it[Keys.putAwayUntil] = maxOf(it[Keys.putAwayUntil] ?: 0L, untilMillis) }
    }

    /** A morning challenge was solved: tonight's night is over, and the morning screen is due. */
    suspend fun releaseNight(opensAtMillis: Long, day: Long) {
        dataStore.edit {
            it[Keys.nightReleasedUntil] = opensAtMillis
            it[Keys.morningScreenDay] = day
        }
    }

    suspend fun setMorningScreenDay(day: Long?) = edit { prefs ->
        if (day == null) {
            prefs.remove(Keys.morningScreenDay)
        } else {
            prefs[Keys.morningScreenDay] = day
        }
    }

    suspend fun setLastReminderFor(millis: Long) = edit { it[Keys.lastReminderFor] = millis }

    suspend fun setGrayscaleByUs(byUs: Boolean) = edit { it[Keys.grayscaleByUs] = byUs }

    suspend fun setFrictionBase(seconds: Int) = edit { it[Keys.frictionBase] = seconds }

    suspend fun setBlockedParts(parts: Set<String>) = edit { it[Keys.blockedParts] = parts }

    suspend fun setLastReviewAt(millis: Long) = edit { it[Keys.lastReviewAt] = millis }

    suspend fun setStudyHandledUntil(millis: Long) = edit { it[Keys.studyHandledUntil] = millis }

    suspend fun setLastBatchAt(millis: Long) = edit { it[Keys.lastBatchAt] = millis }

    suspend fun setDayModes(config: DayModeConfig) = edit {
        it[Keys.nightEnabled] = config.nightEnabled
        it[Keys.bedWeekday] = config.bedWeekday
        it[Keys.bedWeekend] = config.bedWeekend
        it[Keys.openWeekday] = config.openWeekday
        it[Keys.openWeekend] = config.openWeekend
        it[Keys.windDownMinutes] = config.windDownMinutes
        it[Keys.grayscale] = config.grayscale
        it[Keys.redTint] = config.redTint
        it[Keys.reminderMinutes] = config.reminderMinutes
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


    suspend fun setAllowedBrowser(packageName: String?) = edit { prefs ->
        if (packageName == null) {
            prefs.remove(Keys.allowedBrowser)
        } else {
            prefs[Keys.allowedBrowser] = packageName
        }
    }

    /** Debug reset: ends Put away and tonight's night now. Settings stay. */
    suspend fun endAllModes(nightOpensAt: Long?) = edit {
        it.remove(Keys.putAwayUntil)
        if (nightOpensAt != null) {
            it[Keys.nightReleasedUntil] = nightOpensAt
        }
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

    suspend fun lastNightStartedAt(): Long? = dao.lastMode(com.minimalist.launcher.domain.modes.LockKind.Night.name)?.startedAt

    suspend fun recordFirstOpen(day: Long, atMillis: Long, early: Boolean) = dao.recordFirstOpen(day, atMillis, early)

    fun firstOpen(day: Long) = dao.observeFirstOpen(day)

    // Pickups

    suspend fun recordPickup(nowMillis: Long): List<Long> {
        dao.insertPickup(nowMillis)
        dao.prunePickups(nowMillis - Duration.ofDays(8).toMillis())
        return dao.pickupsSince(nowMillis - 3_600_000L)
    }

    // Reflections and habits

    fun reflection(day: Long): Flow<ReflectionEntity?> = dao.observeReflection(day)

    suspend fun saveReflection(day: Long, wentWell: String?) {
        dao.upsertReflection(ReflectionEntity(day, wentWell?.trim()?.ifEmpty { null }, firstTask = null))
    }

    fun habitsToday(): Flow<List<Pair<HabitEntity, Boolean>>> {
        val day = TrustedClock.today().toEpochDay()
        return combine(dao.observeHabits(), dao.observeChecks(day)) { habits, checks ->
            habits.map { it to (it.id in checks) }
        }
    }

    suspend fun addHabit(title: String, position: Int) {
        if (title.isNotBlank()) dao.insertHabit(HabitEntity(title = title.trim(), position = position))
    }

    suspend fun deleteHabit(id: Long) = dao.deleteHabit(id)

    suspend fun setHabitDone(id: Long, done: Boolean) {
        val day = TrustedClock.today().toEpochDay()
        if (done) dao.check(day, id) else dao.uncheck(day, id)
    }

    private suspend fun edit(block: (MutablePreferences) -> Unit) {
        dataStore.edit { block(it) }
    }
}
