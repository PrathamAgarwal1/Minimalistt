package com.minimalist.launcher.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface AppPrefsDao {

    @Query("SELECT * FROM app_prefs")
    fun observeAll(): Flow<List<AppPrefsEntity>>

    @Query("SELECT * FROM app_prefs WHERE app_key = :appKey")
    suspend fun get(appKey: String): AppPrefsEntity?

    @Query("SELECT COUNT(*) FROM app_prefs WHERE favourite = 1")
    suspend fun favouriteCount(): Int

    @Upsert
    suspend fun upsert(entity: AppPrefsEntity)

    @Query("DELETE FROM app_prefs WHERE app_key = :appKey AND hidden = 0 AND favourite = 0")
    suspend fun deleteIfEmpty(appKey: String)
}
