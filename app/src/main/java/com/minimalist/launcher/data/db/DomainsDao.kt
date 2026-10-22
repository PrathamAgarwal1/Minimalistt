package com.minimalist.launcher.data.db

import androidx.room.Dao
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DomainsDao {

    @Query("SELECT domain FROM blocked_domains ORDER BY domain")
    fun observe(): Flow<List<String>>

    @Query("INSERT OR IGNORE INTO blocked_domains (domain) VALUES (:domain)")
    suspend fun add(domain: String)

    @Query("DELETE FROM blocked_domains WHERE domain = :domain")
    suspend fun remove(domain: String)
}
