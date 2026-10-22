package com.minimalist.launcher.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Per-app launcher flags, keyed by component so they survive uninstall and reinstall. */
@Entity(tableName = "app_prefs", indices = [Index("package_name")])
data class AppPrefsEntity(
    @PrimaryKey @ColumnInfo(name = "app_key") val appKey: String,
    @ColumnInfo(name = "package_name") val packageName: String,
    @ColumnInfo(name = "hidden") val hidden: Boolean = false,
    @ColumnInfo(name = "favourite") val favourite: Boolean = false,
)
