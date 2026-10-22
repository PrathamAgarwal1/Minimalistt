package com.minimalist.launcher.ui.theme

import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Black, soft white and one muted accent for the active state. Nothing else. */
object Palette {
    val Background = Color(0xFF000000)
    val Text = Color(0xFFDCDCDC)
    val Dim = Color(0xFF7A7A7A)
    val Faint = Color(0xFF4A4A4A)
    val Line = Color(0xFF1E1E1E)
    val Accent = Color(0xFF8FA89B)
}

/** Small, light type with plenty of air: the screen should feel quiet. */
object Type {
    private val base = TextStyle(fontFamily = FontFamily.Default, color = Palette.Text, letterSpacing = 0.2.sp)

    val clock = base.copy(fontSize = 52.sp, fontWeight = FontWeight.Thin, letterSpacing = 0.sp, lineHeight = 56.sp)
    val title = base.copy(fontSize = 20.sp, fontWeight = FontWeight.Light)
    val item = base.copy(fontSize = 19.sp, fontWeight = FontWeight.Light)
    val body = base.copy(fontSize = 15.sp, fontWeight = FontWeight.Light, lineHeight = 22.sp)
    val small = base.copy(fontSize = 13.sp, color = Palette.Dim, lineHeight = 18.sp)
    val label = base.copy(fontSize = 11.sp, color = Palette.Dim, letterSpacing = 1.5.sp)
}

object Spacing {
    val gutter = 32.dp
    val minTap = 48.dp
}

@Composable
fun MinimalistTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalTextSelectionColors provides TextSelectionColors(
            handleColor = Palette.Accent,
            backgroundColor = Palette.Accent.copy(alpha = 0.35f),
        ),
        content = content,
    )
}
