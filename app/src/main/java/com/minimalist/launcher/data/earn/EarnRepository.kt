package com.minimalist.launcher.data.earn

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.minimalist.launcher.data.blocking.UsageTracker
import com.minimalist.launcher.data.db.AppDatabase
import com.minimalist.launcher.data.db.DailyEarningEntity
import com.minimalist.launcher.domain.blocking.TimeScale
import com.minimalist.launcher.domain.earn.EarnRules
import com.minimalist.launcher.domain.earn.Earning
import com.minimalist.launcher.domain.earn.StepState
import com.minimalist.launcher.domain.modes.LockKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import java.time.ZoneId

data class EarnState(
    /** Apps that open only while there is earned time left. */
    val apps: Set<String> = emptySet(),
    val rules: EarnRules = EarnRules(),
    val steps: StepState = StepState(LocalDate.now().toEpochDay(), 0, null),
)

/** Today's balance for the earned-time apps. */
data class EarnToday(
    val steps: Int,
    val focusSessions: Int,
    val earnedMinutes: Int,
    val usedMillis: Long,
    val leftMillis: Long,
)

class EarnRepository(
    private val dataStore: DataStore<Preferences>,
    private val database: AppDatabase,
    private val usage: UsageTracker,
    scope: CoroutineScope,
) {
    private val stats = database.statsDao()
    private val lock = Mutex()

    private object Keys {
        val apps = stringSetPreferencesKey("earned_apps")
        val stepsPerUnit = intPreferencesKey("earn_steps_per_unit")
        val minutesPerSteps = intPreferencesKey("earn_minutes_per_steps")
        val focusMinutes = intPreferencesKey("earn_focus_minutes")
        val minutesPerFocus = intPreferencesKey("earn_minutes_per_focus")
        val stepsDay = longPreferencesKey("steps_day")
        val stepsToday = intPreferencesKey("steps_today")
        val stepsLastReading = longPreferencesKey("steps_last_reading")
    }

    val state: StateFlow<EarnState> = dataStore.data.map { prefs ->
        val defaults = EarnRules()
        EarnState(
            apps = prefs[Keys.apps] ?: emptySet(),
            rules = EarnRules(
                stepsPerUnit = prefs[Keys.stepsPerUnit] ?: defaults.stepsPerUnit,
                minutesPerSteps = prefs[Keys.minutesPerSteps] ?: defaults.minutesPerSteps,
                focusMinutes = prefs[Keys.focusMinutes] ?: defaults.focusMinutes,
                minutesPerFocus = prefs[Keys.minutesPerFocus] ?: defaults.minutesPerFocus,
            ),
            steps = StepState(
                day = prefs[Keys.stepsDay] ?: LocalDate.now().toEpochDay(),
                stepsToday = prefs[Keys.stepsToday] ?: 0,
                lastReading = prefs[Keys.stepsLastReading],
            ),
        )
    }.stateIn(scope, SharingStarted.Eagerly, EarnState())

    suspend fun addApp(packageName: String) = dataStore.edit { it[Keys.apps] = (it[Keys.apps] ?: emptySet()) + packageName }

    suspend fun removeApp(packageName: String) = dataStore.edit { it[Keys.apps] = (it[Keys.apps] ?: emptySet()) - packageName }

    suspend fun setRules(rules: EarnRules) = dataStore.edit {
        it[Keys.stepsPerUnit] = rules.stepsPerUnit
        it[Keys.minutesPerSteps] = rules.minutesPerSteps
        it[Keys.focusMinutes] = rules.focusMinutes
        it[Keys.minutesPerFocus] = rules.minutesPerFocus
    }

    /** A reading from the step counter sensor. */
    suspend fun onStepReading(reading: Long) = lock.withLock {
        val today = LocalDate.now().toEpochDay()
        val next = Earning.afterReading(state.value.steps, reading, today)
        dataStore.edit {
            it[Keys.stepsDay] = next.day
            it[Keys.stepsToday] = next.stepsToday
            it[Keys.stepsLastReading] = reading
        }
    }

    fun stepsToday(): Int = state.value.steps.takeIf { it.day == LocalDate.now().toEpochDay() }?.stepsToday ?: 0

    suspend fun today(scale: TimeScale): EarnToday {
        val current = state.value
        val dayStart = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val now = System.currentTimeMillis()
        val putAways = stats.modes(LockKind.PutAway.name, dayStart, now + 1)
            .filter { it.startedAt >= dayStart && it.endedAt != null }
            .map { it.startedAt to (it.endedAt ?: it.startedAt) }
        val focus = Earning.focusSessions(putAways, current.rules, scale)
        val steps = stepsToday()
        val earned = Earning.earnedMinutes(steps, focus, current.rules, scale)
        val used = current.apps.sumOf { usage.usageToday(it).foregroundMs }
        return EarnToday(steps, focus, earned, used, Earning.leftMillis(earned, used, scale))
    }

    /** Time left for [packageName] if it's an earned-time app, else null. */
    suspend fun leftFor(packageName: String, scale: TimeScale): Long? =
        if (packageName in state.value.apps) today(scale).leftMillis else null

    /** Keeps today's row in the stats table current. */
    suspend fun saveToday(scale: TimeScale) {
        val today = today(scale)
        stats.upsertEarning(DailyEarningEntity(LocalDate.now().toEpochDay(), today.steps, today.focusSessions, today.earnedMinutes))
    }
}
