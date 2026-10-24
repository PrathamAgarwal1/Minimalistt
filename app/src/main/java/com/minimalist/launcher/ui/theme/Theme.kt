package com.minimalist.launcher.ui.theme

import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import com.minimalist.launcher.data.settings.Appearance
import com.minimalist.launcher.ui.appContainer

/** The muted accents to choose from; the chosen one marks the active state. */
object Accents {
    val OPTIONS: List<Pair<String, Color>> = listOf(
        "sage" to Color(0xFF8FA89B),
        "sand" to Color(0xFFB8A687),
        "sky" to Color(0xFF8FA3B8),
        "rose" to Color(0xFFB89499),
        "lavender" to Color(0xFFA499B8),
        "white" to Color(0xFFF2F2F2),
    )

    /** Resolves a preset name ("sage") or a hex string ("#8FA89B" / "#FF8FA89B"). */
    fun color(name: String): Color {
        OPTIONS.firstOrNull { it.first == name }?.second?.let { return it }
        if (name.startsWith("#")) {
            return runCatching { Color(android.graphics.Color.parseColor(name)) }.getOrElse { OPTIONS.first().second }
        }
        return OPTIONS.first().second
    }

    /** True when [name] is a user-typed hex code rather than a preset name. */
    fun isCustom(name: String): Boolean = name.startsWith("#")
}

val TEXT_SCALES = listOf("Small" to 0.9f, "Medium" to 1f, "Large" to 1.12f, "Larger" to 1.25f)
val CLOCK_WEIGHTS = listOf("thin" to FontWeight.Thin, "light" to FontWeight.Light, "regular" to FontWeight.Normal)

data class ThemePalette(
    val id: String,
    val name: String,
    val background: Color,
    val text: Color,
    val dim: Color,
    val faint: Color,
    val line: Color,
    val dialogBg: Color,
)

object Palettes {
    val PRESETS: List<ThemePalette> = listOf(
        ThemePalette(
            id = "oled",
            name = "OLED",
            background = Color(0xFF000000),
            text = Color(0xFFDCDCDC),
            dim = Color(0xFF7A7A7A),
            faint = Color(0xFF4A4A4A),
            line = Color(0xFF1E1E1E),
            dialogBg = Color(0xFF0A0A0A),
        ),
        ThemePalette(
            id = "charcoal",
            name = "Charcoal",
            background = Color(0xFF161616),
            text = Color(0xFFE0E0E0),
            dim = Color(0xFF888888),
            faint = Color(0xFF444444),
            line = Color(0xFF282828),
            dialogBg = Color(0xFF1E1E1E),
        ),
        ThemePalette(
            id = "catppuccin",
            name = "Catppuccin",
            background = Color(0xFF1E1E2E),
            text = Color(0xFFCDD6F4),
            dim = Color(0xFF6C7086),
            faint = Color(0xFF45475A),
            line = Color(0xFF313244),
            dialogBg = Color(0xFF181825),
        ),
        ThemePalette(
            id = "nord",
            name = "Nord",
            background = Color(0xFF2E3440),
            text = Color(0xFFECEFF4),
            dim = Color(0xFF98A2B3),
            faint = Color(0xFF4C566A),
            line = Color(0xFF3B4252),
            dialogBg = Color(0xFF242933),
        ),
        ThemePalette(
            id = "gruvbox",
            name = "Gruvbox",
            background = Color(0xFF282828),
            text = Color(0xFFEBDBB2),
            dim = Color(0xFFA89984),
            faint = Color(0xFF504945),
            line = Color(0xFF3C3836),
            dialogBg = Color(0xFF1D2021),
        ),
        ThemePalette(
            id = "solarized",
            name = "Solarized",
            background = Color(0xFF002B36),
            text = Color(0xFF93A1A1),
            dim = Color(0xFF657B83),
            faint = Color(0xFF073642),
            line = Color(0xFF0B414F),
            dialogBg = Color(0xFF00212B),
        ),
        ThemePalette(
            id = "paper",
            name = "Paper",
            background = Color(0xFFF7F7F7),
            text = Color(0xFF1E1E1E),
            dim = Color(0xFF757575),
            faint = Color(0xFFBDBDBD),
            line = Color(0xFFE0E0E0),
            dialogBg = Color(0xFFFFFFFF),
        ),
    )

    fun isCustom(id: String): Boolean = id == "custom"

    fun parseHex(hex: String, fallback: Color): Color {
        return runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrElse { fallback }
    }

    fun resolve(id: String, customBg: String, customText: String): ThemePalette {
        if (id == "custom") {
            val bg = parseHex(customBg, Color(0xFF000000))
            val text = parseHex(customText, Color(0xFFDCDCDC))
            return ThemePalette(
                id = "custom",
                name = "Custom",
                background = bg,
                text = text,
                dim = text.copy(alpha = 0.6f),
                faint = text.copy(alpha = 0.35f),
                line = text.copy(alpha = 0.15f),
                dialogBg = bg,
            )
        }
        return PRESETS.firstOrNull { it.id == id } ?: PRESETS.first()
    }
}

/** The chosen look, read by [Palette] and [Type] so every screen follows it. */
private object Look {
    var scale by mutableFloatStateOf(1f)
    var accent by mutableStateOf(Accents.OPTIONS.first().second)
    var clockWeight by mutableStateOf(FontWeight.Thin)
    var background by mutableStateOf(Color(0xFF000000))
    var text by mutableStateOf(Color(0xFFDCDCDC))
    var dim by mutableStateOf(Color(0xFF7A7A7A))
    var faint by mutableStateOf(Color(0xFF4A4A4A))
    var line by mutableStateOf(Color(0xFF1E1E1E))
    var dialogBg by mutableStateOf(Color(0xFF0A0A0A))
}

object Palette {
    val Background: Color get() = Look.background
    val Text: Color get() = Look.text
    /** High-contrast text colour used on top of images / overlays. */
    val PrimaryText: Color = Color(0xFFFFFFFF)
    val Dim: Color get() = Look.dim
    val Faint: Color get() = Look.faint
    val Line: Color get() = Look.line
    /** Semi-transparent scrim drawn over background images for readability. */
    val Overlay: Color = Color(0x80000000)
    /** Fallback surface for dialogs when no custom wallpaper is set. */
    val DialogBackground: Color get() = Look.dialogBg
    val Accent: Color get() = Look.accent
}

/**
 * Custom font loaded from res/font/. To use your own typeface:
 *   1. Drop a .ttf or .otf file into  app/src/main/res/font/
 *   2. Rename it to  custom_font.ttf  (or update the name below).
 *   3. Rebuild — every text style will pick up the new face.
 *
 * If no custom font is bundled, [FontFamily.Default] is used automatically.
 * The resource ID is resolved at runtime so the project compiles without
 * a font file present.
 */
object CustomFontHolder {
    var fontFamily: FontFamily = FontFamily.Default
        private set

    /** Call once from [MinimalistTheme] to initialise the font safely. */
    fun init(context: android.content.Context) {
        if (fontFamily != FontFamily.Default) return  // already loaded
        val id = context.resources.getIdentifier("custom_font", "font", context.packageName)
        if (id != 0) {
            fontFamily = FontFamily(Font(id))
        }
    }
}

/** Small, light type with plenty of air: the screen should feel quiet. */
object Type {
    private fun base() = TextStyle(fontFamily = CustomFontHolder.fontFamily, color = Palette.Text, letterSpacing = 0.2.sp)
    private fun size(value: Float) = (value * Look.scale).sp

    val clock get() = base().copy(fontSize = size(52f), fontWeight = Look.clockWeight, letterSpacing = 0.sp, lineHeight = size(56f))
    val title get() = base().copy(fontSize = size(20f), fontWeight = FontWeight.Light)
    val item get() = base().copy(fontSize = size(19f), fontWeight = FontWeight.Light)
    val body get() = base().copy(fontSize = size(15f), fontWeight = FontWeight.Light, lineHeight = size(22f))
    val small get() = base().copy(fontSize = size(13f), color = Palette.Dim, lineHeight = size(18f))
    val label get() = base().copy(fontSize = size(11f), color = Palette.Dim, letterSpacing = 1.5.sp)
}

object Spacing {
    val gutter = 32.dp
    val minTap = 48.dp
}

@Composable
fun MinimalistTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    CustomFontHolder.init(context)
    val container = appContainer()
    LaunchedEffect(Unit) {
        container.settings.appearance.collect { apply(it) }
    }
    CompositionLocalProvider(
        LocalTextSelectionColors provides TextSelectionColors(
            handleColor = Palette.Accent,
            backgroundColor = Palette.Accent.copy(alpha = 0.35f),
        ),
        content = content,
    )
}

private fun apply(appearance: Appearance) {
    Look.scale = appearance.textScale
    Look.accent = Accents.color(appearance.accent)
    Look.clockWeight = CLOCK_WEIGHTS.firstOrNull { it.first == appearance.clockWeight }?.second ?: FontWeight.Thin

    val palette = Palettes.resolve(appearance.palette, appearance.customBackground, appearance.customText)
    Look.background = palette.background
    Look.text = palette.text
    Look.dim = palette.dim
    Look.faint = palette.faint
    Look.line = palette.line
    Look.dialogBg = palette.dialogBg
}
