package com.minimalist.launcher.ui.blocking

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.minimalist.launcher.domain.DurationFormat
import com.minimalist.launcher.domain.blocking.BlockUnit
import com.minimalist.launcher.ui.appViewModel
import com.minimalist.launcher.ui.components.Choice
import com.minimalist.launcher.ui.components.InputField
import com.minimalist.launcher.ui.components.Label
import com.minimalist.launcher.ui.components.Screen
import com.minimalist.launcher.ui.components.ScreenTitle
import com.minimalist.launcher.ui.components.SectionHeader
import com.minimalist.launcher.ui.components.TextAction
import com.minimalist.launcher.ui.theme.Palette
import com.minimalist.launcher.ui.theme.Type

private val PRESETS = listOf(
    BlockUnit.Hours to 1,
    BlockUnit.Hours to 3,
    BlockUnit.Hours to 8,
    BlockUnit.Days to 1,
    BlockUnit.Days to 3,
    BlockUnit.Weeks to 1,
    BlockUnit.Weeks to 2,
    BlockUnit.Months to 1,
    BlockUnit.Months to 3,
)

@Composable
fun BlockDurationScreen(
    packageName: String,
    onBack: () -> Unit,
    onDone: () -> Unit,
    viewModel: BlockingViewModel = appViewModel { BlockingViewModel(it) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val label = remember(packageName) { viewModel.labelFor(packageName) }
    val currentUntil = state.blocks.firstOrNull { it.packageName == packageName }?.untilMillis
    var amount by remember { mutableStateOf("2") }
    var unit by remember { mutableStateOf(BlockUnit.Days) }

    fun block(unit: BlockUnit, amount: Int) {
        viewModel.block(packageName, unit, amount)
        onDone()
    }

    Screen {
        Spacer(Modifier.height(40.dp))
        ScreenTitle("Block $label", onBack)
        Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            if (currentUntil != null) {
                Label(
                    "Blocked for another ${DurationFormat.remaining(currentUntil - state.nowMillis)}. Anything you pick is added on top.",
                    style = Type.small,
                )
            } else {
                Label("Pick how long. You can always add more later.", style = Type.small)
            }
            if (state.testMode) {
                Label("Test mode: each hour counts as one second.", style = Type.small.copy(color = Palette.Accent))
            }
            Spacer(Modifier.height(8.dp))
            PRESETS.forEach { (presetUnit, presetAmount) ->
                TextAction(
                    text = presetUnit.label(presetAmount),
                    onClick = { block(presetUnit, presetAmount) },
                    style = Type.item,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            SectionHeader("Custom")
            Row(verticalAlignment = Alignment.CenterVertically) {
                InputField(
                    value = amount,
                    onValueChange = { value -> amount = value.filter(Char::isDigit).take(3) },
                    placeholder = "0",
                    modifier = Modifier.width(56.dp),
                    style = Type.item,
                    keyboardType = KeyboardType.Number,
                )
                BlockUnit.entries.forEach { option ->
                    Choice(option.plural, selected = option == unit, onClick = { unit = option })
                }
            }
            val customAmount = amount.toIntOrNull() ?: 0
            TextAction(
                text = if (customAmount > 0) "Block for ${unit.label(customAmount)}" else "Block",
                onClick = { block(unit, customAmount) },
                enabled = customAmount > 0,
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}
