package com.minimalist.launcher.ui.blocking

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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.minimalist.launcher.domain.blocking.AppLimit
import com.minimalist.launcher.ui.appViewModel
import com.minimalist.launcher.ui.components.Choice
import com.minimalist.launcher.ui.components.Label
import com.minimalist.launcher.ui.components.Screen
import com.minimalist.launcher.ui.components.ScreenTitle
import com.minimalist.launcher.ui.components.SectionHeader
import com.minimalist.launcher.ui.components.TextAction
import com.minimalist.launcher.ui.theme.Type

private val MINUTE_OPTIONS = listOf(null, 15, 30, 45, 60, 90, 120)
private val OPEN_OPTIONS = listOf(null, 1, 3, 5, 10, 20)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LimitEditScreen(
    packageName: String,
    onBack: () -> Unit,
    viewModel: BlockingViewModel = appViewModel { BlockingViewModel(it) },
) {
    val existing = remember(packageName) { viewModel.limitFor(packageName) }
    val label = remember(packageName) { viewModel.labelFor(packageName) }
    var minutes by remember { mutableStateOf(if (existing != null) existing.maxMinutes else 30) }
    var opens by remember { mutableStateOf(existing?.maxOpens) }

    Screen {
        Spacer(Modifier.height(40.dp))
        ScreenTitle("Limit $label", onBack)
        Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Label("Once a limit is reached, the app stays closed until midnight.", style = Type.small)

            SectionHeader("Time per day")
            FlowRow {
                MINUTE_OPTIONS.forEach { option ->
                    Choice(option?.let(BlockingText::minutes) ?: "No limit", selected = option == minutes, onClick = { minutes = option })
                }
            }

            SectionHeader("Opens per day")
            FlowRow {
                OPEN_OPTIONS.forEach { option ->
                    Choice(option?.toString() ?: "No limit", selected = option == opens, onClick = { opens = option })
                }
            }

            Spacer(Modifier.height(24.dp))
            TextAction(
                text = "Save",
                onClick = {
                    viewModel.saveLimit(AppLimit(packageName, minutes, opens))
                    onBack()
                },
            )
            if (existing != null) {
                TextAction(
                    text = "Remove limit",
                    onClick = {
                        viewModel.removeLimit(packageName)
                        onBack()
                    },
                    style = Type.small,
                )
            }
        }
    }
}
