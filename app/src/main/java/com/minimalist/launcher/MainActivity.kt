package com.minimalist.launcher

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import com.minimalist.launcher.modes.GuardService
import com.minimalist.launcher.ui.LauncherRoot
import com.minimalist.launcher.ui.theme.MinimalistTheme
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val homePresses = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private val openRequests = MutableSharedFlow<String>(replay = 1, extraBufferCapacity = 1)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        handleOpen(intent)
        setContent {
            MinimalistTheme {
                LauncherRoot(homePresses, openRequests)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // The launcher is in front: a safe moment to (re)start the guard service.
        GuardService.start(this)
        val container = (application as MinimalistApp).container
        lifecycleScope.launch { container.coordinator.tick() }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == Intent.ACTION_MAIN && intent.hasCategory(Intent.CATEGORY_HOME)) {
            homePresses.tryEmit(Unit)
        }
        handleOpen(intent)
    }

    private fun handleOpen(intent: Intent?) {
        intent?.getStringExtra(EXTRA_OPEN)?.let { openRequests.tryEmit(it) }
    }

    companion object {
        const val EXTRA_OPEN = "open"
        const val OPEN_HELD = "held"
    }
}
