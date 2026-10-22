package com.minimalist.launcher.ui.blocking

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.minimalist.launcher.AppContainer
import com.minimalist.launcher.data.protection.Change
import com.minimalist.launcher.domain.blocking.AppLimit
import com.minimalist.launcher.domain.blocking.BlockUnit
import com.minimalist.launcher.domain.blocking.Schedule
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class BlockRow(val packageName: String, val label: String, val untilMillis: Long)

data class ScheduleRow(val schedule: Schedule, val appLabels: List<String>)

data class LimitRow(val label: String, val limit: AppLimit)

data class FrictionRow(val packageName: String, val label: String)

data class BlockingState(
    val nowMillis: Long = System.currentTimeMillis(),
    val friction: List<FrictionRow> = emptyList(),
    val blocks: List<BlockRow> = emptyList(),
    val schedules: List<ScheduleRow> = emptyList(),
    val limits: List<LimitRow> = emptyList(),
    val testMode: Boolean = false,
)

class BlockingViewModel(private val container: AppContainer) : ViewModel() {

    private val blocking = container.blocking

    private val seconds = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(1_000)
        }
    }

    val state: StateFlow<BlockingState> = combine(
        blocking.rules,
        container.apps.installedApps,
        container.timeScale,
        container.friction.apps,
        seconds,
    ) { rules, _, scale, frictionApps, now ->
        BlockingState(
            nowMillis = now,
            friction = frictionApps.map { FrictionRow(it, container.labelFor(it)) }.sortedBy { it.label.lowercase() },
            blocks = rules.blocks
                .filterValues { it > now }
                .map { (packageName, until) -> BlockRow(packageName, container.labelFor(packageName), until) }
                .sortedBy { it.untilMillis },
            schedules = rules.schedules.map { schedule -> ScheduleRow(schedule, schedule.packages.map(container::labelFor).sorted()) },
            limits = rules.limits.values.map { LimitRow(container.labelFor(it.packageName), it) }.sortedBy { it.label.lowercase() },
            testMode = scale.testMode,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BlockingState())

    fun blockUntil(packageName: String): Long? = blocking.rules.value.blocks[packageName]?.takeIf { it > System.currentTimeMillis() }

    fun limitFor(packageName: String): AppLimit? = blocking.rules.value.limits[packageName]

    fun labelFor(packageName: String) = container.labelFor(packageName)

    fun block(packageName: String, unit: BlockUnit, amount: Int) {
        val duration = container.timeScale.value.block(unit.duration.multipliedBy(amount.toLong()))
        viewModelScope.launch { blocking.extendBlock(packageName, duration) }
    }

    // Everything that could loosen protection goes through the change gate and its cooldown.

    fun removeBlock(packageName: String) = container.changes.submit(Change.RemoveBlock(packageName))

    fun saveLimit(limit: AppLimit) = container.changes.submit(Change.SaveLimit(limit))

    fun removeLimit(packageName: String) = container.changes.submit(Change.RemoveLimit(packageName))

    suspend fun loadSchedule(id: Long): Schedule? = blocking.schedule(id)

    fun saveSchedule(schedule: Schedule) = container.changes.submit(Change.SaveSchedule(schedule))

    fun setScheduleEnabled(id: Long, enabled: Boolean) = container.changes.submit(Change.SetScheduleEnabled(id, enabled))

    fun deleteSchedule(id: Long) = container.changes.submit(Change.DeleteSchedule(id))

    fun removeFriction(packageName: String) = container.changes.submit(Change.RemoveFriction(packageName))

    fun setTestMode(enabled: Boolean) = container.changes.submit(Change.SetTestMode(enabled))
}
