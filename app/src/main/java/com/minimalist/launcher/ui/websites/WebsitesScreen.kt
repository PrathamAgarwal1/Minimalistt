package com.minimalist.launcher.ui.websites

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.minimalist.launcher.domain.websites.BrowserBars
import com.minimalist.launcher.ui.appContainer
import com.minimalist.launcher.ui.components.Choice
import com.minimalist.launcher.ui.components.InputField
import com.minimalist.launcher.ui.components.Label
import com.minimalist.launcher.ui.components.LabelledAction
import com.minimalist.launcher.ui.components.Screen
import com.minimalist.launcher.ui.components.ScreenTitle
import com.minimalist.launcher.ui.components.SectionHeader
import com.minimalist.launcher.ui.components.TextAction
import com.minimalist.launcher.ui.theme.Palette
import com.minimalist.launcher.ui.theme.Type
import kotlinx.coroutines.launch

@Composable
fun WebsitesScreen(onBack: () -> Unit) {
    val container = appContainer()
    val scope = rememberCoroutineScope()
    val state by container.modes.state.collectAsStateWithLifecycle()
    val custom by container.websites.customDomains.collectAsStateWithLifecycle()
    val installed by container.apps.installedApps.collectAsStateWithLifecycle()
    val browsers = remember(installed) { container.browsers.packages.sortedBy { container.labelFor(it).lowercase() } }
    var domain by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    Screen {
        Spacer(Modifier.height(40.dp))
        ScreenTitle("Websites", onBack)
        Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            SectionHeader("One browser")
            Label(
                "Choose the one browser you'll use; every other browser stays blocked. " +
                    "Pick the one apps open for sign-ins, or those sign-ins get blocked too.",
                style = Type.small,
            )
            Choice("Any browser", selected = state.allowedBrowser == null, onClick = {
                scope.launch { container.modes.setAllowedBrowser(null) }
            }, modifier = Modifier.fillMaxWidth())
            browsers.forEach { packageName ->
                val reads = packageName in BrowserBars.VIEW_IDS
                Choice(
                    container.labelFor(packageName) + if (reads) "" else " (sites can't be checked)",
                    selected = state.allowedBrowser == packageName,
                    onClick = { scope.launch { container.modes.setAllowedBrowser(packageName) } },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Label("Sites are checked in Chrome, Brave, Edge and Firefox, incognito tabs included.", style = Type.small)

            SectionHeader("Blocked websites")
            Label(
                "While an app is blocked, its website is too: Instagram and instagram.com, YouTube and youtube.com, youtu.be, m.youtube.com.",
                style = Type.small,
            )
            if (custom.isEmpty()) Label("No extra sites.", style = Type.small)
            custom.sorted().forEach { site ->
                LabelledAction(label = site, action = "Remove", onAction = { scope.launch { container.websites.remove(site) } })
            }
            InputField(
                value = domain,
                onValueChange = {
                    domain = it.take(100)
                    error = null
                },
                placeholder = "Add a site, e.g. reddit.com",
                style = Type.body,
                keyboardType = KeyboardType.Uri,
                onImeAction = {
                    val input = domain
                    scope.launch {
                        if (container.websites.add(input)) domain = "" else error = "That doesn't look like a website."
                    }
                },
            )
            error?.let { Label(it, style = Type.small.copy(color = Palette.Accent)) }
            TextAction(text = "Block site", enabled = domain.isNotBlank(), onClick = {
                val input = domain
                scope.launch {
                    if (container.websites.add(input)) domain = "" else error = "That doesn't look like a website."
                }
            })
            Label("Needs Minimalist blocking switched on in Accessibility.", style = Type.small)
            Spacer(Modifier.height(24.dp))
        }
    }
}
