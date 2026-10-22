package com.minimalist.launcher.ui.blocking

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.minimalist.launcher.data.friction.FrictionRepository
import com.minimalist.launcher.domain.friction.AppFeeling
import com.minimalist.launcher.domain.friction.WantingLiking
import com.minimalist.launcher.ui.appContainer
import com.minimalist.launcher.ui.components.Label
import com.minimalist.launcher.ui.components.Screen
import com.minimalist.launcher.ui.components.ScreenTitle
import com.minimalist.launcher.ui.components.SectionHeader
import com.minimalist.launcher.ui.components.TwoLineRow
import com.minimalist.launcher.ui.theme.Palette
import com.minimalist.launcher.ui.theme.Type
import kotlinx.coroutines.flow.map

/** Apps you reach for hard but don't enjoy, from the last week of urge and worth ratings. */
@Composable
fun WantingLikingScreen(onBack: () -> Unit, onBlock: (String) -> Unit) {
    val container = appContainer()
    val feelings by remember {
        container.friction.pullsSince(FrictionRepository.daysAgo(6)).map(WantingLiking::summarize)
    }.collectAsStateWithLifecycle(emptyList())
    val flagged = feelings.filter { it.wantingOutpacesLiking }

    Screen {
        Spacer(Modifier.height(40.dp))
        ScreenTitle("Wanting vs liking", onBack)
        Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Label("Urge is rated before a slowed-down app opens, worth when you leave it. 1 is low, 5 is high.", style = Type.small)
            if (feelings.isEmpty()) {
                Spacer(Modifier.height(16.dp))
                Label("Nothing rated in the last 7 days.", style = Type.body)
            }
            if (flagged.isNotEmpty()) {
                SectionHeader("Wanted more than enjoyed")
                flagged.forEach { feeling ->
                    TwoLineRow(
                        title = container.labelFor(feeling.packageName),
                        subtitle = describe(feeling),
                        action = "Block this",
                        onAction = { onBlock(feeling.packageName) },
                    )
                }
                Label("A strong pull and little enjoyment: a block may serve you better.", style = Type.small.copy(color = Palette.Accent))
            }
            val rest = feelings.filterNot { it.wantingOutpacesLiking }
            if (rest.isNotEmpty()) {
                SectionHeader("Everything else")
                rest.forEach { feeling ->
                    TwoLineRow(title = container.labelFor(feeling.packageName), subtitle = describe(feeling))
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

private fun describe(feeling: AppFeeling): String = listOfNotNull(
    feeling.averageUrge?.let { "urge %.1f".format(it) },
    feeling.averageWorth?.let { "worth %.1f".format(it) },
    "${feeling.opens} ${if (feeling.opens == 1) "open" else "opens"}",
    feeling.resisted.takeIf { it > 0 }?.let { "$it resisted" },
).joinToString(" · ")
