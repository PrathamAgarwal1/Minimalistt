package com.minimalist.launcher.ui.placeholder

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.minimalist.launcher.ui.components.Label
import com.minimalist.launcher.ui.components.Screen
import com.minimalist.launcher.ui.components.ScreenTitle
import com.minimalist.launcher.ui.theme.Type

/** Stands in for features that arrive in later build phases. */
@Composable
fun ComingLaterScreen(title: String, body: String, onBack: () -> Unit) {
    Screen {
        Spacer(Modifier.height(40.dp))
        ScreenTitle(title, onBack)
        Label(body, style = Type.body)
    }
}
