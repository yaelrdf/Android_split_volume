package dev.yaelrdf.splitvolume.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import dev.yaelrdf.splitvolume.SplitController
import dev.yaelrdf.splitvolume.app
import dev.yaelrdf.splitvolume.syncWithConnection
import kotlinx.coroutines.launch

/** The "sliders" for the notification: a small dialog over whatever you were doing. */
class QuickControlsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SplitVolumeTheme {
                val profile by app.store.profile.collectAsStateWithLifecycle(initialValue = null)
                val state by app.controller.state.collectAsStateWithLifecycle()
                val scope = rememberCoroutineScope()
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("L/R volume", style = MaterialTheme.typography.titleLarge)
                        Text(statusText(state, profile), style = MaterialTheme.typography.bodyMedium)
                        profile?.let { p ->
                            VolumeControls(
                                profile = p,
                                active = state is SplitController.State.Active,
                                onLevel = { side, v -> scope.launch { app.store.update { it.withLevel(side, v) } } },
                                onSwap = { scope.launch { app.store.update { it.swapped() } } },
                            )
                        }
                        TextButton(
                            onClick = {
                                startActivity(Intent(this@QuickControlsActivity, MainActivity::class.java))
                                finish()
                            },
                            modifier = Modifier.align(Alignment.End),
                        ) { Text("More settings") }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Being in the foreground is what lets us start the session if a background start was refused.
        lifecycleScope.launch { syncWithConnection(this@QuickControlsActivity) }
    }
}
