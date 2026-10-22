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
    ],
    version = 2,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2)],
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun appPrefsDao(): AppPrefsDao

    abstract fun blockingDao(): BlockingDao

    abstract fun usageDao(): UsageDao

    companion object {
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "minimalist.db").build()
    }
}
