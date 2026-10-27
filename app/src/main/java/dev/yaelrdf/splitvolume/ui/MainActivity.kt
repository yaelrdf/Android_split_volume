package dev.yaelrdf.splitvolume.ui

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.companion.AssociationInfo
import android.companion.AssociationRequest
import android.companion.BluetoothDeviceFilter
import android.companion.CompanionDeviceManager
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import dev.yaelrdf.splitvolume.Profile
import dev.yaelrdf.splitvolume.SplitController
import dev.yaelrdf.splitvolume.app
import dev.yaelrdf.splitvolume.syncWithConnection
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    // Bumped whenever permissions or battery settings may have changed, so the setup list re-reads them.
    private var refreshTick by mutableIntStateOf(0)

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { refresh() }
    private val associationLauncher =
        registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { SplitVolumeTheme { MainScreen() } }
    }

    override fun onResume() {
        super.onResume()
        refresh()
        lifecycleScope.launch { syncWithConnection(this@MainActivity) }
    }

    private fun refresh() {
        refreshTick++
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun MainScreen() {
        val profile by app.store.profile.collectAsStateWithLifecycle(initialValue = null)
        val state by app.controller.state.collectAsStateWithLifecycle()
        val scope = rememberCoroutineScope()
        var showPaired by remember { mutableStateOf(false) }

        Scaffold(topBar = { TopAppBar(title = { Text("Split Volume") }) }) { padding ->
            Column(
                Modifier
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Card(Modifier.fillMaxWidth()) {
                    Text(statusText(state, profile), Modifier.padding(16.dp), style = MaterialTheme.typography.bodyLarge)
                }
                profile?.let { p ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Volume", style = MaterialTheme.typography.titleMedium)
                            VolumeControls(
                                profile = p,
                                active = state is SplitController.State.Active,
                                onLevel = { side, v -> scope.launch { app.store.update { it.withLevel(side, v) } } },
                                onSwap = { scope.launch { app.store.update { it.swapped() } } },
                                onPing = { side ->
                                    scope.launch {
                                        try {
                                            app.controller.ping(side)
                                        } catch (e: Exception) {
                                            toast("Ping failed: ${e.message}")
                                        }
                                    }
                                },
                            )
                        }
                    }
                }
                SetupCard(profile, onPickPaired = { showPaired = true })
            }
        }
        if (showPaired) PairedDevicesDialog(onDismiss = { showPaired = false })
    }

    @Composable
    private fun SetupCard(profile: Profile?, onPickPaired: () -> Unit) {
        @Suppress("UNUSED_EXPRESSION") refreshTick
        val bluetoothGranted = granted(Manifest.permission.BLUETOOTH_CONNECT)
        val notificationsGranted = granted(Manifest.permission.POST_NOTIFICATIONS)
        val batteryUnrestricted = getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName)
        val balanceAvailable = app.controller.systemBalanceAvailable
        val monoOn = app.controller.systemMonoOn == true

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Setup", style = MaterialTheme.typography.titleMedium)

                if (!balanceAvailable) {
                    SetupItem(
                        title = "Audio balance",
                        detail = "This phone doesn't let apps change the audio balance, so L/R volume can't work here.",
                        done = false,
                    ) {}
                }

                SetupItem(
                    title = "Mono audio",
                    detail = if (monoOn) "On: both earbuds play the whole song."
                    else "Turn on Accessibility → Hearing → Mono audio, so each earbud plays the whole song instead " +
                        "of half the stereo mix. Android only lets its own Settings switch this; it's harmless to leave on.",
                    done = monoOn,
                ) {
                    if (!monoOn) Button(onClick = { openAccessibility() }) { Text("Open Accessibility") }
                }

                SetupItem(
                    title = "Bluetooth & notifications",
                    detail = if (bluetoothGranted && notificationsGranted) "Allowed"
                    else "Needed to recognise your headphones and to show the L/R controls.",
                    done = bluetoothGranted && notificationsGranted,
                ) {
                    if (!bluetoothGranted || !notificationsGranted) {
                        Button(onClick = {
                            permissionLauncher.launch(
                                arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.POST_NOTIFICATIONS)
                            )
                        }) { Text("Allow") }
                    }
                }

                SetupItem(
                    title = "Headphones",
                    detail = profile?.deviceAddress?.let { "${profile.deviceName ?: "Unnamed"} ($it)" }
                        ?: "Pick the pair you share. The app stays off for every other device.",
                    done = profile?.deviceAddress != null,
                ) {
                    Button(onClick = { chooseHeadphones() }) {
                        Text(if (profile?.deviceAddress == null) "Choose" else "Change")
                    }
                    TextButton(onClick = onPickPaired, enabled = bluetoothGranted) { Text("From paired list") }
                }

                SetupItem(
                    title = "Run in background",
                    detail = "Let the app start by itself when the headphones connect. On ColorOS, also turn on " +
                        "Auto launch and Allow background activity under App info → Battery.",
                    done = batteryUnrestricted,
                ) {
                    if (!batteryUnrestricted) {
                        Button(onClick = { requestBatteryExemption() }) { Text("Allow") }
                    }
                    TextButton(onClick = { openAppInfo() }) { Text("App info") }
                }
            }
        }
    }

    @Composable
    private fun SetupItem(title: String, detail: String, done: Boolean, actions: @Composable RowScope.() -> Unit) {
        Row(verticalAlignment = Alignment.Top) {
            Text(if (done) "✓" else "•", style = MaterialTheme.typography.titleMedium, modifier = Modifier.width(24.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(detail, style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically, content = actions)
            }
        }
    }

    @Composable
    private fun PairedDevicesDialog(onDismiss: () -> Unit) {
        val devices = remember { pairedDevices() }
        AlertDialog(
            onDismissRequest = onDismiss,
            confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
            title = { Text("Paired devices") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "Picking from here skips Android's companion-device link, so starting by itself relies on " +
                            "the battery exemption. Prefer \"Choose\" if it finds your headphones.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    if (devices.isEmpty()) Text("No paired devices found.")
                    devices.forEach { (name, address) ->
                        Text(
                            "$name\n$address",
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    saveDevice(address, name)
                                    onDismiss()
                                }
                                .padding(vertical = 8.dp),
                        )
                    }
                }
            },
        )
    }

    /** Android's companion-device picker; the association it creates is what allows starting from the background. */
    private fun chooseHeadphones() {
        val cdm = getSystemService(CompanionDeviceManager::class.java)
        val request = AssociationRequest.Builder()
            .addDeviceFilter(BluetoothDeviceFilter.Builder().build())
            .build()
        cdm.associate(request, mainExecutor, object : CompanionDeviceManager.Callback() {
            override fun onAssociationPending(intentSender: IntentSender) {
                associationLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
            }

            override fun onAssociationCreated(associationInfo: AssociationInfo) {
                val address = associationInfo.deviceMacAddress?.toString() ?: return
                // Only the current pair matters; drop links to any pair chosen before.
                cdm.myAssociations.filter { it.id != associationInfo.id }.forEach { cdm.disassociate(it.id) }
                saveDevice(address, associationInfo.displayName?.toString())
            }

            override fun onFailure(error: CharSequence?) {
                toast("Couldn't pick headphones: ${error ?: "unknown error"}. Try \"From paired list\".")
            }
        })
    }

    private fun saveDevice(address: String, name: String?) {
        lifecycleScope.launch {
            app.store.setDevice(address, name ?: bondedName(address))
            syncWithConnection(this@MainActivity)
        }
    }

    @SuppressLint("MissingPermission") // Guarded by the SecurityException catch.
    private fun bondedName(address: String): String? = try {
        getSystemService(BluetoothManager::class.java).adapter?.getRemoteDevice(address.uppercase())?.name
    } catch (_: SecurityException) {
        null
    }

    @SuppressLint("MissingPermission") // Guarded by the SecurityException catch.
    private fun pairedDevices(): List<Pair<String, String>> = try {
        getSystemService(BluetoothManager::class.java).adapter?.bondedDevices.orEmpty()
            .map { (it.name ?: it.address) to it.address }
            .sortedBy { it.first.lowercase() }
    } catch (_: SecurityException) {
        emptyList()
    }

    @SuppressLint("BatteryLife") // Personal app; starting on connect is the whole point.
    private fun requestBatteryExemption() {
        startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, "package:$packageName".toUri()))
    }

    private fun openAccessibility() {
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    private fun openAppInfo() {
        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:$packageName".toUri()))
    }

    private fun granted(permission: String) =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_LONG).show()
}
