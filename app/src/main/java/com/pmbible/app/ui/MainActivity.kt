package com.pmbible.app.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pmbible.app.notifications.Destinations
import com.pmbible.app.ui.navigation.PmBibleRoot
import com.pmbible.app.ui.theme.PmBibleTheme
import com.pmbible.core.model.ThemeMode

class MainActivity : ComponentActivity() {
    /** Destination requested by a notification tap, consumed once navigation happens. */
    private val pendingOpen = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) pendingOpen.value = intent.getStringExtra(Destinations.EXTRA_OPEN)
        setContent {
            val app: AppViewModel = viewModel()
            val prefs by app.prefs.collectAsState()
            PmBibleTheme(prefs?.themeMode ?: ThemeMode.SYSTEM, prefs?.textScale ?: 1f) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    PmBibleRoot(app, pendingOpen.value, onOpenConsumed = { pendingOpen.value = null })
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra(Destinations.EXTRA_OPEN)?.let { pendingOpen.value = it }
    }
}
