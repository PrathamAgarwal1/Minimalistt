package com.minimalist.launcher.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/** How the launcher looks. Purely cosmetic, so changes apply at once. */
data class Appearance(
    val textScale: Float = 1f,
    val accent: String = "sage",
    val clockWeight: String = "thin",
    val centred: Boolean = true,
    /** Shown under the date: "screen", "alarm", "battery". */
    val glance: Set<String> = setOf("screen", "alarm"),
    /** The color theme / palette: "oled", "charcoal", "catppuccin", "nord", "gruvbox", "solarized", "paper", "custom" */
    val palette: String = "oled",
    val customBackground: String = "#000000",
    val customText: String = "#DCDCDC",
)

/** The favourites order shown on [epochDay]. */
data class StoredFavouriteOrder(val keys: List<String>, val epochDay: Long)

class SettingsRepository(private val dataStore: DataStore<Preferences>) {

    private object Keys {
        val favouriteOrder = stringPreferencesKey("favourite_order")
        val favouriteOrderDay = longPreferencesKey("favourite_order_day")
        val testMode = booleanPreferencesKey("test_mode")
        val textScale = floatPreferencesKey("text_scale")
        val accent = stringPreferencesKey("accent")
        val clockWeight = stringPreferencesKey("clock_weight")
        val centred = booleanPreferencesKey("centred")
        val glance = stringSetPreferencesKey("glance")
        val setupDone = booleanPreferencesKey("setup_done")
        val palette = stringPreferencesKey("palette")
        val customBackground = stringPreferencesKey("custom_background")
        val customText = stringPreferencesKey("custom_text")
    }

    /** Whether the first-run setup has been seen. */
    val setupDone: Flow<Boolean> = dataStore.data.map { it[Keys.setupDone] ?: false }.distinctUntilChanged()

    suspend fun setSetupDone() {
        dataStore.edit { it[Keys.setupDone] = true }
    }

    val appearance: Flow<Appearance> = dataStore.data.map { prefs ->
        val defaults = Appearance()
        Appearance(
            textScale = prefs[Keys.textScale] ?: defaults.textScale,
            accent = prefs[Keys.accent] ?: defaults.accent,
            clockWeight = prefs[Keys.clockWeight] ?: defaults.clockWeight,
            centred = prefs[Keys.centred] ?: defaults.centred,
            glance = prefs[Keys.glance] ?: defaults.glance,
            palette = prefs[Keys.palette] ?: defaults.palette,
            customBackground = prefs[Keys.customBackground] ?: defaults.customBackground,
            customText = prefs[Keys.customText] ?: defaults.customText,
        )
    }.distinctUntilChanged()

    suspend fun setAppearance(appearance: Appearance) {
        dataStore.edit {
            it[Keys.textScale] = appearance.textScale
            it[Keys.accent] = appearance.accent
            it[Keys.clockWeight] = appearance.clockWeight
            it[Keys.centred] = appearance.centred
            it[Keys.glance] = appearance.glance
            it[Keys.palette] = appearance.palette
            it[Keys.customBackground] = appearance.customBackground
            it[Keys.customText] = appearance.customText
        }
    }

    /** Debug builds only; see TimeScale. */
    val testMode: Flow<Boolean> = dataStore.data.map { it[Keys.testMode] ?: false }.distinctUntilChanged()

    val favouriteOrder: Flow<StoredFavouriteOrder> = dataStore.data.map { prefs ->
        StoredFavouriteOrder(
            keys = prefs[Keys.favouriteOrder]?.split(SEPARATOR)?.filter { it.isNotEmpty() }.orEmpty(),
            epochDay = prefs[Keys.favouriteOrderDay] ?: Long.MIN_VALUE,
        )
    }.distinctUntilChanged()

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
