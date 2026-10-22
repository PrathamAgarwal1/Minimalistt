package com.minimalist.launcher.data.protection

import com.minimalist.launcher.domain.blocking.AppLimit
import com.minimalist.launcher.domain.blocking.Schedule
import com.minimalist.launcher.domain.earn.EarnRules
import com.minimalist.launcher.domain.modes.DayModeConfig
import com.minimalist.launcher.domain.modes.StudyWindow
import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek

/** A settings change that may weaken protection, so it goes through [ChangeGate]. */
sealed interface Change {
    data class RemoveBlock(val packageName: String) : Change
    data class SaveLimit(val limit: AppLimit) : Change
    data class RemoveLimit(val packageName: String) : Change
    data class SaveSchedule(val schedule: Schedule) : Change
    data class SetScheduleEnabled(val id: Long, val enabled: Boolean) : Change
    data class DeleteSchedule(val id: Long) : Change
    data class RemoveFriction(val packageName: String) : Change
    data class SetDayModes(val config: DayModeConfig) : Change
    data class SetUnplug(val minutes: Int) : Change
    data class SetPickups(val threshold: Int, val minutes: Int) : Change
    data class SetStudyWindows(val windows: List<StudyWindow>) : Change
    data class SetEssentials(val packages: Set<String>) : Change
    data class SetAudioApp(val packageName: String?) : Change
    data class SetNotesApp(val packageName: String?) : Change
    data class AddVip(val name: String, val phone: String) : Change
    data class SetBatching(val enabled: Boolean, val times: List<Int>) : Change
    data class SetAllowedBrowser(val packageName: String?) : Change
    data class RemoveDomain(val domain: String) : Change
    data class SetTestMode(val enabled: Boolean) : Change
    data class SetFrictionBase(val seconds: Int) : Change
    data class SetEarnRules(val rules: EarnRules) : Change
    data class RemoveEarnedApp(val packageName: String) : Change
    data class SetBlockedParts(val parts: Set<String>) : Change
}

/** Stores a queued change as JSON so it survives restarts until its cooldown ends. */
object ChangeCodec {

    fun encode(change: Change): String = JSONObject().apply {
        put("type", change::class.simpleName)
        when (change) {
            is Change.RemoveBlock -> put("package", change.packageName)
            is Change.SaveLimit -> {
                put("package", change.limit.packageName)
                change.limit.maxMinutes?.let { put("minutes", it) }
                change.limit.maxOpens?.let { put("opens", it) }
            }
            is Change.RemoveLimit -> put("package", change.packageName)
            is Change.SaveSchedule -> put("schedule", schedule(change.schedule))
            is Change.SetScheduleEnabled -> {
                put("id", change.id)
                put("enabled", change.enabled)
            }
            is Change.DeleteSchedule -> put("id", change.id)
            is Change.RemoveFriction -> put("package", change.packageName)
            is Change.SetDayModes -> put("config", dayModes(change.config))
            is Change.SetUnplug -> put("minutes", change.minutes)
            is Change.SetPickups -> {
                put("threshold", change.threshold)
                put("minutes", change.minutes)
            }
            is Change.SetStudyWindows -> put("windows", StudyWindow.encode(change.windows))
            is Change.SetEssentials -> put("packages", JSONArray(change.packages.toList()))
            is Change.SetAudioApp -> change.packageName?.let { put("package", it) }
            is Change.SetNotesApp -> change.packageName?.let { put("package", it) }
            is Change.AddVip -> {
                put("name", change.name)
                put("phone", change.phone)
            }
            is Change.SetBatching -> {
                put("enabled", change.enabled)
                put("times", JSONArray(change.times))
            }
            is Change.SetAllowedBrowser -> change.packageName?.let { put("package", it) }
            is Change.RemoveDomain -> put("domain", change.domain)
            is Change.SetTestMode -> put("enabled", change.enabled)
            is Change.SetFrictionBase -> put("seconds", change.seconds)
            is Change.SetEarnRules -> {
                put("stepsPerUnit", change.rules.stepsPerUnit)
                put("minutesPerSteps", change.rules.minutesPerSteps)
                put("focusMinutes", change.rules.focusMinutes)
                put("minutesPerFocus", change.rules.minutesPerFocus)
            }
            is Change.RemoveEarnedApp -> put("package", change.packageName)
            is Change.SetBlockedParts -> put("parts", JSONArray(change.parts.toList()))
        }
    }.toString()

    fun decode(text: String): Change? = runCatching {
        val json = JSONObject(text)
        val pkg = json.optString("package").takeIf { json.has("package") }
        when (json.getString("type")) {
            "RemoveBlock" -> Change.RemoveBlock(pkg!!)
            "SaveLimit" -> Change.SaveLimit(
                AppLimit(pkg!!, json.optInt("minutes").takeIf { json.has("minutes") }, json.optInt("opens").takeIf { json.has("opens") }),
            )
            "RemoveLimit" -> Change.RemoveLimit(pkg!!)
            "SaveSchedule" -> Change.SaveSchedule(schedule(json.getJSONObject("schedule")))
            "SetScheduleEnabled" -> Change.SetScheduleEnabled(json.getLong("id"), json.getBoolean("enabled"))
            "DeleteSchedule" -> Change.DeleteSchedule(json.getLong("id"))
            "RemoveFriction" -> Change.RemoveFriction(pkg!!)
            "SetDayModes" -> Change.SetDayModes(dayModes(json.getJSONObject("config")))
            "SetUnplug" -> Change.SetUnplug(json.getInt("minutes"))
            "SetPickups" -> Change.SetPickups(json.getInt("threshold"), json.getInt("minutes"))
            "SetStudyWindows" -> Change.SetStudyWindows(StudyWindow.decode(json.getString("windows")))
            "SetEssentials" -> Change.SetEssentials(json.getJSONArray("packages").strings().toSet())
            "SetAudioApp" -> Change.SetAudioApp(pkg)
            "SetNotesApp" -> Change.SetNotesApp(pkg)
            "AddVip" -> Change.AddVip(json.getString("name"), json.getString("phone"))
            "SetBatching" -> Change.SetBatching(json.getBoolean("enabled"), json.getJSONArray("times").ints())
            "SetAllowedBrowser" -> Change.SetAllowedBrowser(pkg)
            "RemoveDomain" -> Change.RemoveDomain(json.getString("domain"))
            "SetTestMode" -> Change.SetTestMode(json.getBoolean("enabled"))
            "SetFrictionBase" -> Change.SetFrictionBase(json.getInt("seconds"))
            "SetEarnRules" -> Change.SetEarnRules(
                EarnRules(json.getInt("stepsPerUnit"), json.getInt("minutesPerSteps"), json.getInt("focusMinutes"), json.getInt("minutesPerFocus")),
            )
            "RemoveEarnedApp" -> Change.RemoveEarnedApp(pkg!!)
            "SetBlockedParts" -> Change.SetBlockedParts(json.getJSONArray("parts").strings().toSet())
            else -> null
        }
    }.getOrNull()

    private fun schedule(schedule: Schedule) = JSONObject().apply {
        put("id", schedule.id)
        put("name", schedule.name)
        put("start", schedule.startMinute)
        put("end", schedule.endMinute)
        put("days", JSONArray(schedule.days.map { it.value }))
        put("packages", JSONArray(schedule.packages.toList()))
        put("enabled", schedule.enabled)
    }

    private fun schedule(json: JSONObject) = Schedule(
        id = json.getLong("id"),
        name = json.getString("name"),
        startMinute = json.getInt("start"),
        endMinute = json.getInt("end"),
        days = json.getJSONArray("days").ints().map { DayOfWeek.of(it) }.toSet(),
        packages = json.getJSONArray("packages").strings().toSet(),
        enabled = json.getBoolean("enabled"),
    )

    private fun dayModes(config: DayModeConfig) = JSONObject().apply {
        put("nightEnabled", config.nightEnabled)
        put("bedWeekday", config.bedWeekday)
        put("bedWeekend", config.bedWeekend)
        put("openWeekday", config.openWeekday)
        put("openWeekend", config.openWeekend)
        put("windDownMinutes", config.windDownMinutes)
        put("grayscale", config.grayscale)
        put("redTint", config.redTint)
        put("reminderMinutes", config.reminderMinutes)
    }

    private fun dayModes(json: JSONObject): DayModeConfig {
        val defaults = DayModeConfig()
        return DayModeConfig(
            nightEnabled = json.optBoolean("nightEnabled", defaults.nightEnabled),
            bedWeekday = json.optInt("bedWeekday", defaults.bedWeekday),
            bedWeekend = json.optInt("bedWeekend", defaults.bedWeekend),
            openWeekday = json.optInt("openWeekday", defaults.openWeekday),
            openWeekend = json.optInt("openWeekend", defaults.openWeekend),
            windDownMinutes = json.optInt("windDownMinutes", defaults.windDownMinutes),
            grayscale = json.optBoolean("grayscale", defaults.grayscale),
            redTint = json.optBoolean("redTint", defaults.redTint),
            reminderMinutes = json.optInt("reminderMinutes", defaults.reminderMinutes),
        )
    }

    private fun JSONArray.strings() = (0 until length()).map { getString(it) }

    private fun JSONArray.ints() = (0 until length()).map { getInt(it) }
}
