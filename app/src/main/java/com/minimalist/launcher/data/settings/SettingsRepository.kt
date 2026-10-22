package com.minimalist.launcher.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/** Today's intention, valid only on the day it was written. */
data class Intention(val text: String, val epochDay: Long)

/** The favourites order shown on [epochDay]. */
data class StoredFavouriteOrder(val keys: List<String>, val epochDay: Long)

class SettingsRepository(private val dataStore: DataStore<Preferences>) {

    private object Keys {
        val intentionText = stringPreferencesKey("intention_text")
        val intentionDay = longPreferencesKey("intention_day")
        val favouriteOrder = stringPreferencesKey("favourite_order")
        val favouriteOrderDay = longPreferencesKey("favourite_order_day")
        val testMode = booleanPreferencesKey("test_mode")
    }

    /** Debug builds only; see TimeScale. */
    val testMode: Flow<Boolean> = dataStore.data.map { it[Keys.testMode] ?: false }.distinctUntilChanged()

    val intention: Flow<Intention?> = dataStore.data.map { prefs ->
        val text = prefs[Keys.intentionText]?.takeIf { it.isNotBlank() } ?: return@map null
        Intention(text, prefs[Keys.intentionDay] ?: Long.MIN_VALUE)
    }.distinctUntilChanged()

    val favouriteOrder: Flow<StoredFavouriteOrder> = dataStore.data.map { prefs ->
        StoredFavouriteOrder(
            keys = prefs[Keys.favouriteOrder]?.split(SEPARATOR)?.filter { it.isNotEmpty() }.orEmpty(),
            epochDay = prefs[Keys.favouriteOrderDay] ?: Long.MIN_VALUE,
        )
    }.distinctUntilChanged()

    suspend fun setIntention(text: String, epochDay: Long) {
        dataStore.edit {
            it[Keys.intentionText] = text.trim()
            it[Keys.intentionDay] = epochDay
        }
    }

    suspend fun setFavouriteOrder(order: StoredFavouriteOrder) {
        dataStore.edit {
            it[Keys.favouriteOrder] = order.keys.joinToString(SEPARATOR)
            it[Keys.favouriteOrderDay] = order.epochDay
        }
    }

    suspend fun setTestMode(enabled: Boolean) {
        dataStore.edit { it[Keys.testMode] = enabled }
    }

    private companion object {
        const val SEPARATOR = "\n"
    }
}
