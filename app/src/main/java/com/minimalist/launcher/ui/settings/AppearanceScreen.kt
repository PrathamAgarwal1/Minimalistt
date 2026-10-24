package com.minimalist.launcher.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.minimalist.launcher.data.settings.Appearance
import com.minimalist.launcher.ui.appContainer
import com.minimalist.launcher.ui.components.Choice
import com.minimalist.launcher.ui.components.InputField
import com.minimalist.launcher.ui.components.Label
import com.minimalist.launcher.ui.components.MENU_BG_FILENAME
import com.minimalist.launcher.ui.components.SETTINGS_BG_FILENAME
import com.minimalist.launcher.ui.components.Screen
import com.minimalist.launcher.ui.components.ScreenTitle
import com.minimalist.launcher.ui.components.SectionHeader
import com.minimalist.launcher.ui.components.TextAction
import com.minimalist.launcher.ui.components.menuWallpaperVersion
import com.minimalist.launcher.ui.components.settingsWallpaperVersion
import com.minimalist.launcher.ui.theme.Accents
import com.minimalist.launcher.ui.theme.CLOCK_WEIGHTS
import com.minimalist.launcher.ui.theme.Palettes
import com.minimalist.launcher.ui.theme.TEXT_SCALES
import com.minimalist.launcher.ui.theme.Type
import java.io.File
import kotlinx.coroutines.launch

private val GLANCE_ITEMS = listOf("screen" to "Screen time", "alarm" to "Next alarm", "battery" to "Battery")

private val HEX_PATTERN = Regex("^#([0-9A-Fa-f]{6}|[0-9A-Fa-f]{8})$")

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AppearanceScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val container = appContainer()
    val scope = rememberCoroutineScope()
    val look by container.settings.appearance.collectAsStateWithLifecycle(Appearance())
    fun set(next: Appearance) {
        scope.launch { container.settings.setAppearance(next) }
    }

    val isCustomPalette = Palettes.isCustom(look.palette)
    var customBgInput by remember(look.customBackground) {
        mutableStateOf(look.customBackground)
    }
    var customTextInput by remember(look.customText) {
        mutableStateOf(look.customText)
    }

    val isCustomAccent = Accents.isCustom(look.accent)
    var hexInput by remember(look.accent) {
        mutableStateOf(if (isCustomAccent) look.accent else "#")
    }

    val hasMenuWallpaper = remember(menuWallpaperVersion) {
        File(context.filesDir, MENU_BG_FILENAME).exists()
    }
    val hasSettingsWallpaper = remember(settingsWallpaperVersion) {
        File(context.filesDir, SETTINGS_BG_FILENAME).exists()
    }

    val menuPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri ->
        if (uri != null) {
            context.contentResolver.openInputStream(uri)?.use { input ->
                File(context.filesDir, MENU_BG_FILENAME).outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            menuWallpaperVersion++
        }
    }

    val settingsPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri ->
        if (uri != null) {
            context.contentResolver.openInputStream(uri)?.use { input ->
                File(context.filesDir, SETTINGS_BG_FILENAME).outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            settingsWallpaperVersion++
        }
    }

    Screen(settingsWallpaper = true) {
        Spacer(Modifier.height(40.dp))
        ScreenTitle("Appearance", onBack)
        Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            SectionHeader("Text size")
            FlowRow {
                TEXT_SCALES.forEach { (name, scale) ->
                    Choice(name, selected = look.textScale == scale, onClick = { set(look.copy(textScale = scale)) })
                }
            }

            SectionHeader("Palette")
            Label("Background and typography color theme.", style = Type.small)
            FlowRow {
                Palettes.PRESETS.forEach { preset ->
                    Choice(preset.name, selected = look.palette == preset.id, onClick = { set(look.copy(palette = preset.id)) })
                }
                Choice("Custom", selected = isCustomPalette, onClick = {
                    set(look.copy(palette = "custom"))
                })
            }
            if (isCustomPalette) {
                Spacer(Modifier.height(8.dp))
                Label("Custom Background (#RRGGBB)", style = Type.small)
                InputField(
                    value = customBgInput,
                    onValueChange = { raw ->
                        val cleaned = if (raw.startsWith("#")) raw else "#$raw"
                        customBgInput = cleaned.take(9)
                        if (HEX_PATTERN.matches(cleaned)) {
                            set(look.copy(customBackground = cleaned))
                        }
                    },
                    placeholder = "#000000",
                    style = Type.body,
                    keyboardType = KeyboardType.Ascii,
                )
                Spacer(Modifier.height(8.dp))
                Label("Custom Text (#RRGGBB)", style = Type.small)
                InputField(
                    value = customTextInput,
                    onValueChange = { raw ->
                        val cleaned = if (raw.startsWith("#")) raw else "#$raw"
                        customTextInput = cleaned.take(9)
                        if (HEX_PATTERN.matches(cleaned)) {
                            set(look.copy(customText = cleaned))
                        }
                    },
                    placeholder = "#DCDCDC",
                    style = Type.body,
                    keyboardType = KeyboardType.Ascii,
                )
            }

            SectionHeader("Accent")
            Label("The one colour, for whatever is active or chosen.", style = Type.small)
            FlowRow {
                Accents.OPTIONS.forEach { (name, _) ->
                    Choice(name.replaceFirstChar { it.uppercase() }, selected = look.accent == name, onClick = { set(look.copy(accent = name)) })
                }
                Choice("Custom", selected = isCustomAccent, onClick = {
                    if (!isCustomAccent) set(look.copy(accent = "#8FA89B"))
                })
            }
            if (isCustomAccent) {
                Label("Hex code, e.g. #8FA89B", style = Type.small)
                InputField(
                    value = hexInput,
                    onValueChange = { raw ->
                        // Allow typing only valid hex characters after #
                        val cleaned = if (raw.startsWith("#")) raw else "#$raw"
                        hexInput = cleaned.take(9)  // # + up to 8 hex chars
                        if (HEX_PATTERN.matches(cleaned)) {
                            set(look.copy(accent = cleaned))
                        }
                    },
                    placeholder = "#RRGGBB",
                    style = Type.body,
                    keyboardType = KeyboardType.Ascii,
                )
            }

            SectionHeader("Wallpapers")
            Label("Custom background images for settings and pop-up menus.", style = Type.small)
            Spacer(Modifier.height(8.dp))

            Label("Settings screen wallpaper", style = Type.body)
            FlowRow {
                Choice(
                    label = if (hasSettingsWallpaper) "Change image" else "Choose image",
                    selected = false,
                    onClick = { settingsPicker.launch("image/*") },
                )
                if (hasSettingsWallpaper) {
                    Choice(
                        label = "Remove",
                        selected = false,
                        onClick = {
                            File(context.filesDir, SETTINGS_BG_FILENAME).delete()
                            settingsWallpaperVersion++
                        },
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            Label("Pop-up menu wallpaper", style = Type.body)
            FlowRow {
                Choice(
                    label = if (hasMenuWallpaper) "Change image" else "Choose image",
                    selected = false,
                    onClick = { menuPicker.launch("image/*") },
                )
                if (hasMenuWallpaper) {
                    Choice(
                        label = "Remove",
                        selected = false,
                        onClick = {
                            File(context.filesDir, MENU_BG_FILENAME).delete()
                            menuWallpaperVersion++
                        },
                    )
                }
            }

            SectionHeader("Clock")
            FlowRow {
                CLOCK_WEIGHTS.forEach { (name, _) ->
                    Choice(name.replaceFirstChar { it.uppercase() }, selected = look.clockWeight == name, onClick = { set(look.copy(clockWeight = name)) })
                }
            }

            SectionHeader("Home screen")
            FlowRow {
                Choice("Centred", selected = look.centred, onClick = { set(look.copy(centred = true)) })
                Choice("Left", selected = !look.centred, onClick = { set(look.copy(centred = false)) })
            }
            Label("Under the date", style = Type.body)
            FlowRow {
                GLANCE_ITEMS.forEach { (key, name) ->
                    val on = key in look.glance
                    Choice(name, selected = on, onClick = { set(look.copy(glance = if (on) look.glance - key else look.glance + key)) })
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
