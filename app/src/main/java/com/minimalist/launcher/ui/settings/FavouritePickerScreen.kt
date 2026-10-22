package com.minimalist.launcher.ui.settings

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.minimalist.launcher.ui.appViewModel
import com.minimalist.launcher.ui.components.InputField
import com.minimalist.launcher.ui.components.Screen
import com.minimalist.launcher.ui.components.ScreenTitle
import com.minimalist.launcher.ui.components.TextAction
import com.minimalist.launcher.ui.theme.Type

/** Pick a favourite by typing, same as search: there is no full app list. */
@Composable
fun FavouritePickerScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = appViewModel { SettingsViewModel(it) },
) {
    val query by viewModel.pickerQuery.collectAsStateWithLifecycle()
    val results by viewModel.pickerResults.collectAsStateWithLifecycle()
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    DisposableEffect(Unit) { onDispose { viewModel.setPickerQuery("") } }

    Screen {
        Spacer(Modifier.height(40.dp))
        ScreenTitle("Add a favourite", onBack)
        InputField(
            value = query,
            onValueChange = viewModel::setPickerQuery,
            placeholder = "Type an app name",
            modifier = Modifier.focusRequester(focus),
        )
        LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
            items(results, key = { it.key }) { app ->
                TextAction(
                    text = app.label,
                    onClick = {
                        viewModel.addFavourite(app)
                        onBack()
                    },
                    style = Type.item,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
