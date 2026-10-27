package dev.yaelrdf.splitvolume

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager

private val BLUETOOTH_OUTPUT_TYPES = setOf(
    AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
    AudioDeviceInfo.TYPE_BLE_HEADSET,
)

fun AudioDeviceInfo.isBluetoothOutput(address: String) =
    type in BLUETOOTH_OUTPUT_TYPES && this.address.equals(address, ignoreCase = true)

fun isOutputConnected(context: Context, address: String): Boolean =
    context.getSystemService(AudioManager::class.java)
        .getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        .any { it.isBluetoothOutput(address) }
