package com.minimalist.launcher.system

import android.content.Context
import android.content.SharedPreferences
import android.os.SystemClock
import android.provider.Settings
import com.minimalist.launcher.domain.blocking.TimeAnchor
import com.minimalist.launcher.domain.blocking.TrustedTime
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * The time every block, pass, mode and cooldown is measured by. It moves with real elapsed
 * time, so changing the phone's clock doesn't end anything early. The home screen clock still
 * shows the phone's own time.
 */
object TrustedClock {

    private const val SAVE_EVERY_MILLIS = 60_000L

    private lateinit var prefs: SharedPreferences
    @Volatile
    private var anchor = TimeAnchor(System.currentTimeMillis(), SystemClock.elapsedRealtime(), -1)
    @Volatile
    private var lastSaved = 0L

    fun init(context: Context) {
        prefs = context.getSharedPreferences("trusted_clock", Context.MODE_PRIVATE)
        val boot = runCatching { Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT) }.getOrDefault(-1)
        val savedBoot = prefs.getInt("boot", Int.MIN_VALUE)
        anchor = if (savedBoot == boot && prefs.contains("wall")) {
            TimeAnchor(prefs.getLong("wall", 0), prefs.getLong("elapsed", 0), boot)
        } else {
            val last = if (prefs.contains("last")) prefs.getLong("last", 0) else null
            TrustedTime.afterBoot(last, System.currentTimeMillis(), SystemClock.elapsedRealtime(), boot)
        }
        save(anchor.anchorWall, force = true)
    }

    fun nowMillis(): Long {
        val (now, next) = TrustedTime.read(anchor, System.currentTimeMillis(), SystemClock.elapsedRealtime())
        val moved = next != anchor
        anchor = next
        save(now, force = moved && now - lastSaved > SAVE_EVERY_MILLIS)
        return now
    }

    fun zonedNow(): ZonedDateTime = Instant.ofEpochMilli(nowMillis()).atZone(ZoneId.systemDefault())

    fun today(): LocalDate = zonedNow().toLocalDate()

    private fun save(now: Long, force: Boolean) {
        if (!::prefs.isInitialized) return
        if (!force && now - lastSaved < SAVE_EVERY_MILLIS) return
        lastSaved = now
        prefs.edit()
            .putInt("boot", anchor.bootCount)
            .putLong("wall", anchor.anchorWall)
            .putLong("elapsed", anchor.anchorElapsed)
            .putLong("last", now)
            .apply()
    }
}
