package com.minimalist.launcher.ui.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.paint
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.minimalist.launcher.ui.theme.Palette
import com.minimalist.launcher.ui.theme.Spacing
import com.minimalist.launcher.ui.theme.Type
import java.io.File

/** File name used for the custom menu background in pop-up dialogs. */
const val MENU_BG_FILENAME = "custom_menu_bg.jpg"

/** File name used for the custom settings screen wallpaper. */
const val SETTINGS_BG_FILENAME = "custom_settings_bg.jpg"

/** Incremented when a wallpaper is updated so Composable collectors reload immediately. */
var menuWallpaperVersion by mutableIntStateOf(0)
var settingsWallpaperVersion by mutableIntStateOf(0)

/** Full-screen surface that keeps content clear of bars, cutouts and the keyboard. */
@Composable
fun Screen(
    modifier: Modifier = Modifier,
    settingsWallpaper: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val context = LocalContext.current
    val bgPainter = remember(settingsWallpaperVersion) {
        if (settingsWallpaper) {
            val file = File(context.filesDir, SETTINGS_BG_FILENAME)
            if (file.exists()) {
                val bitmap = BitmapFactory.decodeFile(file.absolutePath)
                bitmap?.let { BitmapPainter(it.asImageBitmap()) }
            } else null
        } else null
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .then(
                if (settingsWallpaper) Modifier.background(Palette.Background)
                else Modifier.background(Color.Transparent)
            ),
    ) {
        if (bgPainter != null) {
            Image(
                painter = bgPainter,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(Palette.Overlay),
            )
        }
        Column(
            modifier = modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = Spacing.gutter),
            content = content,
        )
    }
}

@Composable
fun Label(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = Type.body,
    textAlign: TextAlign? = null,
) {
    BasicText(text = text, modifier = modifier, style = if (textAlign != null) style.copy(textAlign = textAlign) else style)
}

/** A row of text with a large tap target. No icons, no ripple flourish. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TextAction(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: TextStyle = Type.body,
    enabled: Boolean = true,
    onLongClick: (() -> Unit)? = null,
    alignment: Alignment = Alignment.CenterStart,
) {
    Box(
        modifier = modifier
            .heightIn(min = Spacing.minTap)
            .combinedClickable(enabled = enabled, onClick = onClick, onLongClick = onLongClick),
        contentAlignment = alignment,
    ) {
        BasicText(text = text, style = if (enabled) style else style.copy(color = Palette.Faint))
    }
}

@Composable
fun ScreenTitle(text: String, onBack: () -> Unit) {
    TextAction(text = text, onClick = onBack, style = Type.title, modifier = Modifier.fillMaxWidth())
    Spacer(Modifier.height(12.dp))
}

@Composable
fun SectionHeader(text: String) {
    Spacer(Modifier.height(32.dp))
    Label(text = text.uppercase(), style = Type.label)
    Spacer(Modifier.height(4.dp))
}

@Composable
fun InputField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    style: TextStyle = Type.title,
    imeAction: ImeAction = ImeAction.Done,
    onImeAction: () -> Unit = {},
    capitalization: KeyboardCapitalization = KeyboardCapitalization.None,
    singleLine: Boolean = true,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth().heightIn(min = Spacing.minTap),
        textStyle = style,
        singleLine = singleLine,
        cursorBrush = SolidColor(Palette.Accent),
        keyboardOptions = KeyboardOptions(imeAction = imeAction, capitalization = capitalization, autoCorrectEnabled = false, keyboardType = keyboardType),
        keyboardActions = KeyboardActions(onAny = { onImeAction() }),
        decorationBox = { inner ->
            Box(contentAlignment = Alignment.CenterStart) {
                if (value.isEmpty()) BasicText(placeholder, style = style.copy(color = Palette.Faint))
                inner()
            }
        },
    )
}

/** One line with a label on the left and a quiet action on the right. */
@Composable
fun LabelledAction(label: String, action: String, onAction: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Label(label, modifier = Modifier.weight(1f), style = Type.body)
        TextAction(text = action, onClick = onAction, style = Type.small, modifier = Modifier.padding(start = 16.dp))
    }
}

/** A title with a quieter second line, an optional action on the right, and an optional tap. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TwoLineRow(
    title: String,
    subtitle: String,
    action: String? = null,
    onAction: () -> Unit = {},
    onClick: (() -> Unit)? = null,
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(
            modifier = Modifier
                .weight(1f)
                .heightIn(min = Spacing.minTap)
                .then(if (onClick != null) Modifier.combinedClickable(onClick = onClick) else Modifier)
                .padding(vertical = 6.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Label(title, style = Type.body)
            Label(subtitle, style = Type.small)
        }
        if (action != null) {
            TextAction(text = action, onClick = onAction, style = Type.small, modifier = Modifier.padding(start = 16.dp))
        }
    }
}

/** A row that opens a page: its name, a quiet line saying how things stand, and a chevron. */
@Composable
fun NavRow(title: String, summary: String? = null, onClick: () -> Unit, summaryColor: Color = Palette.Dim) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .combinedClickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Label(title, style = Type.body)
            if (summary != null) Label(summary, style = Type.small.copy(color = summaryColor))
        }
        Label("›", style = Type.item.copy(color = Palette.Faint), modifier = Modifier.padding(start = 16.dp))
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(Palette.Line))
}

/** One option in a set; the chosen one takes the accent. */
@Composable
fun Choice(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    TextAction(
        text = text,
        onClick = onClick,
        style = Type.body.copy(color = if (selected) Palette.Accent else Palette.Dim),
        modifier = modifier.padding(end = 20.dp),
    )
}

/** Asks for a line of text. */
@Composable
fun TextInputDialog(
    title: String,
    initial: String,
    placeholder: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    var value by remember { mutableStateOf(initial) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Palette.Background)
                .border(1.dp, Palette.Line)
                .padding(horizontal = Spacing.gutter, vertical = 20.dp),
        ) {
            Label(title, style = Type.small)
            InputField(
                value = value,
                onValueChange = { value = it },
                placeholder = placeholder,
                modifier = Modifier.focusRequester(focus),
                style = Type.body,
                capitalization = KeyboardCapitalization.Sentences,
                keyboardType = keyboardType,
                onImeAction = {
                    onSave(value)
                    onDismiss()
                },
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextAction("Cancel", onClick = onDismiss, style = Type.small)
                Spacer(Modifier.width(24.dp))
                TextAction(
                    "Save",
                    onClick = {
                        onSave(value)
                        onDismiss()
                    },
                )
            }
        }
    }
}

/** A quiet list of choices over the current screen, optionally with a custom background image. */
@Composable
fun OptionsDialog(
    title: String,
    options: List<Pair<String, () -> Unit>>,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current

    // Attempt to load the custom background image from internal storage.
    val bgPainter = remember(menuWallpaperVersion) {
        val file = File(context.filesDir, MENU_BG_FILENAME)
        if (file.exists()) {
            val bitmap = BitmapFactory.decodeFile(file.absolutePath)
            bitmap?.let { BitmapPainter(it.asImageBitmap()) }
        } else null
    }

    val hasCustomBg = bgPainter != null

    // Text styles: force high-contrast white when a background image is active.
    val titleStyle = if (hasCustomBg) Type.small.copy(color = Palette.PrimaryText) else Type.small
    val itemStyle  = if (hasCustomBg) Type.body.copy(color = Palette.PrimaryText) else Type.body
    val cancelStyle = if (hasCustomBg) Type.small.copy(color = Palette.PrimaryText) else Type.small

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Palette.Line),
        ) {
            if (bgPainter != null) {
                Image(
                    painter = bgPainter,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize(),
                )
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(Palette.Overlay),
                )
            } else {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(Palette.DialogBackground),
                )
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.gutter, vertical = 20.dp),
            ) {
                Label(title, style = titleStyle)
                Spacer(Modifier.height(8.dp))
                options.forEach { (label, action) ->
                    TextAction(
                        text = label,
                        onClick = {
                            onDismiss()
                            action()
                        },
                        style = itemStyle,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                TextAction(text = "Cancel", onClick = onDismiss, style = cancelStyle, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}
