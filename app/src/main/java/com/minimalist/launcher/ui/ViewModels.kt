package com.minimalist.launcher.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.minimalist.launcher.AppContainer
import com.minimalist.launcher.MinimalistApp

/** Activity-scoped ViewModel built from the app's [AppContainer]. */
@Composable
inline fun <reified VM : ViewModel> appViewModel(crossinline create: (AppContainer) -> VM): VM {
    val container = (LocalContext.current.applicationContext as MinimalistApp).container
    return viewModel(factory = viewModelFactory { initializer { create(container) } })
}
