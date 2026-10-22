package com.minimalist.launcher.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.minimalist.launcher.data.settings.Appearance
import com.minimalist.launcher.ui.appContainer
import com.minimalist.launcher.ui.components.Choice
import com.minimalist.launcher.ui.components.Label
import com.minimalist.launcher.ui.components.Screen
import com.minimalist.launcher.ui.components.ScreenTitle
import com.minimalist.launcher.ui.components.SectionHeader
import com.minimalist.launcher.ui.components.TextAction
import com.minimalist.launcher.ui.theme.Accents
import com.minimalist.launcher.ui.theme.CLOCK_WEIGHTS
import com.minimalist.launcher.ui.theme.TEXT_SCALES
import com.minimalist.launcher.ui.theme.Type
import kotlinx.coroutines.launch

private val GLANCE_ITEMS = listOf("screen" to "Screen time", "alarm" to "Next alarm", "battery" to "Battery")

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AppearanceScreen(onBack: () -> Unit) {
    val container = appContainer()
    val scope = rememberCoroutineScope()
    val look by container.settings.appearance.collectAsStateWithLifecycle(Appearance())
    fun set(next: Appearance) {
        scope.launch { container.settings.setAppearance(next) }
    }

    Screen {
        Spacer(Modifier.height(40.dp))
        ScreenTitle("Appearance", onBack)
        Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            SectionHeader("Text size")
            FlowRow {
                TEXT_SCALES.forEach { (name, scale) ->
                    Choice(name, selected = look.textScale == scale, onClick = { set(look.copy(textScale = scale)) })
                }
            }

            SectionHeader("Accent")
            Label("The one colour, for whatever is active or chosen.", style = Type.small)
            FlowRow {
                Accents.OPTIONS.forEach { (name, _) ->
                    Choice(name.replaceFirstChar { it.uppercase() }, selected = look.accent == name, onClick = { set(look.copy(accent = name)) })
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
