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

/** Black, white and one muted accent for the active state. Nothing else. */
object Palette {
    val Background = Color(0xFF000000)
    val Text = Color(0xFFEDEDED)
    val Dim = Color(0xFF8C8C8C)
    val Faint = Color(0xFF2E2E2E)
    val Accent = Color(0xFF8FA89B)
}

object Type {
    private val base = TextStyle(fontFamily = FontFamily.Default, color = Palette.Text)

    val clock = base.copy(fontSize = 72.sp, fontWeight = FontWeight.Light, letterSpacing = (-1).sp)
    val title = base.copy(fontSize = 28.sp, fontWeight = FontWeight.Normal)
    val item = base.copy(fontSize = 24.sp, fontWeight = FontWeight.Normal)
    val body = base.copy(fontSize = 17.sp, lineHeight = 24.sp)
    val small = base.copy(fontSize = 14.sp, color = Palette.Dim)
}

object Spacing {
    val gutter = 24.dp
    val minTap = 56.dp
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
