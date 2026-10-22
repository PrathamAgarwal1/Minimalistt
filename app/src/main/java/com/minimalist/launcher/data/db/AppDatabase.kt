package com.minimalist.launcher.data.db

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        AppPrefsEntity::class,
        AppBlockEntity::class,
        ScheduleEntity::class,
        AppLimitEntity::class,
        DailyUsageEntity::class,
        FrictionAppEntity::class,
        FrictionLogEntity::class,
        ModeLogEntity::class,
        ReflectionEntity::class,
        HabitEntity::class,
        HabitCheckEntity::class,
        HeldNotificationEntity::class,
        VipContactEntity::class,
        UrgentLogEntity::class,
        BlockedDomainEntity::class,
        PickupEntity::class,
    ],
    version = 3,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2), AutoMigration(from = 2, to = 3)],
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun appPrefsDao(): AppPrefsDao

    abstract fun blockingDao(): BlockingDao

    abstract fun usageDao(): UsageDao

    abstract fun frictionDao(): FrictionDao

    abstract fun modesDao(): ModesDao

    abstract fun notificationsDao(): NotificationsDao

    abstract fun domainsDao(): DomainsDao

    companion object {
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "minimalist.db").build()
    }
}
