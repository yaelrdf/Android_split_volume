package dev.yaelrdf.splitvolume.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.yaelrdf.splitvolume.Profile
import dev.yaelrdf.splitvolume.Side
import dev.yaelrdf.splitvolume.SplitController
import kotlin.math.roundToInt

@Composable
fun VolumeControls(
    profile: Profile,
    active: Boolean,
    onLevel: (Side, Int) -> Unit,
    onSwap: () -> Unit,
    modifier: Modifier = Modifier,
    onPing: ((Side) -> Unit)? = null,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Side.entries.forEach { side -> SideRow(side, profile.level(side), onLevel) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = onSwap) { Text("Swap L ⇄ R") }
            onPing?.let { ping ->
                Side.entries.forEach { side ->
                    TextButton(onClick = { ping(side) }, enabled = active) { Text("Ping ${side.name}") }
                }
            }
        }
    }
}

@Composable
private fun SideRow(side: Side, level: Int, onLevel: (Side, Int) -> Unit) {
    // While dragging, show the finger's position rather than waiting for the stored value to catch up.
    var dragging by remember { mutableStateOf<Float?>(null) }
    val shown = dragging?.roundToInt() ?: level
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            side.name,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(28.dp),
        )
        IconButton(onClick = { onLevel(side, level - 1) }) { Text("−", style = MaterialTheme.typography.titleLarge) }
        Slider(
            value = dragging ?: level.toFloat(),
            onValueChange = {
                dragging = it
                onLevel(side, it.roundToInt())
            },
            onValueChangeFinished = { dragging = null },
            valueRange = 0f..100f,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = { onLevel(side, level + 1) }) { Text("+", style = MaterialTheme.typography.titleLarge) }
        Text("$shown%", textAlign = TextAlign.End, modifier = Modifier.width(44.dp))
    }
}

fun statusText(state: SplitController.State, profile: Profile?): String = when (state) {
    is SplitController.State.Active -> "Active on ${state.deviceName}"
    is SplitController.State.Waiting -> "Connecting to ${state.deviceName}…"
    is SplitController.State.Error -> state.message
    SplitController.State.Idle ->
        if (profile?.deviceAddress == null) "Choose your headphones below to get started."
        else "Waiting for ${profile.deviceName ?: "your headphones"} to connect. Levels you set now are used next time."
}
