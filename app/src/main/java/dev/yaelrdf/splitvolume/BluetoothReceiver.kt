package dev.yaelrdf.splitvolume

import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Starts the session when the chosen headphones connect, and makes sure balance is restored when they leave. */
class BluetoothReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java) ?: return
        val connected = when (intent.action) {
            BluetoothDevice.ACTION_ACL_CONNECTED -> true
            BluetoothDevice.ACTION_ACL_DISCONNECTED -> false
            BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED -> when (intent.getIntExtra(BluetoothProfile.EXTRA_STATE, -1)) {
                BluetoothProfile.STATE_CONNECTED -> true
                BluetoothProfile.STATE_DISCONNECTED -> false
                else -> return
            }
            else -> return
        }
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val target = context.app.store.current().deviceAddress
                if (!device.address.equals(target, ignoreCase = true)) return@launch
                if (connected) {
                    if (!SplitVolumeService.running && !SplitVolumeService.start(context)) {
                        Notifications.show(context, Notifications.PROMPT_ID, Notifications.activatePrompt(context))
                    }
                } else if (SplitVolumeService.running) {
                    SplitVolumeService.stop(context)
                } else if (context.app.controller.hasPendingRestore()) {
                    // The service was killed while active; it can't restore, so we do.
                    context.app.controller.restore()
                }
            } catch (e: Exception) {
                Log.w("BluetoothReceiver", "Handling ${intent.action} failed", e)
            } finally {
                pending.finish()
            }
        }
    }
}
