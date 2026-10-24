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

    fun color(name: String): Color = OPTIONS.firstOrNull { it.first == name }?.second ?: OPTIONS.first().second
}

val TEXT_SCALES = listOf("Small" to 0.9f, "Medium" to 1f, "Large" to 1.12f, "Larger" to 1.25f)
val CLOCK_WEIGHTS = listOf("thin" to FontWeight.Thin, "light" to FontWeight.Light, "regular" to FontWeight.Normal)

/** The chosen look, read by [Palette] and [Type] so every screen follows it. */
private object Look {
    var scale by mutableFloatStateOf(1f)
    var accent by mutableStateOf(Accents.OPTIONS.first().second)
    var clockWeight by mutableStateOf(FontWeight.Thin)
}

/**
 * Black, soft white and one muted accent for the active state. Nothing else.
 *
 * ── CUSTOMISATION ──────────────────────────────────────────────
 * Swap any hex code below to re-skin the entire launcher.
 * Example – Catppuccin Mocha:
 *   Background  = Color(0xFF1E1E2E)
 *   Text        = Color(0xFFCDD6F4)
 *   PrimaryText = Color(0xFFCDD6F4)
 *   Dim         = Color(0xFF6C7086)
 *   Faint       = Color(0xFF45475A)
 *   Line        = Color(0xFF313244)
 *   Overlay     = Color(0x80000000)   // 50 % black scrim
 *   DialogBg    = Color(0xFF181825)
 *   Accent      = (set via Accents object / settings)
 * ───────────────────────────────────────────────────────────────
 */
object Palette {
    val Background      = Color(0xFF000000)
    val Text            = Color(0xFFDCDCDC)
    /** High-contrast text colour used on top of images / overlays. */
    val PrimaryText     = Color(0xFFFFFFFF)
    val Dim             = Color(0xFF7A7A7A)
    val Faint           = Color(0xFF4A4A4A)
    val Line            = Color(0xFF1E1E1E)
    /** Semi-transparent scrim drawn over background images for readability. */
    val Overlay         = Color(0x80000000)
    /** Fallback surface for dialogs when no custom wallpaper is set. */
    val DialogBackground = Color(0xFF0A0A0A)
    val Accent: Color get() = Look.accent
}

/**
 * Custom font loaded from res/font/.  To use your own typeface:
 *   1.  Drop a .ttf or .otf file into  app/src/main/res/font/
 *   2.  Rename it to  custom_font.ttf  (or update the name below).
 *   3.  Rebuild — every text style will pick up the new face.
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
}
