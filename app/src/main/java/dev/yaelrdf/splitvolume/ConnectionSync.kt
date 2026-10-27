package dev.yaelrdf.splitvolume

import android.content.Context
import android.util.Log

/**
 * Brings things in line with reality when the UI opens: start the session if the headphones are already
 * connected, or put back a balance left behind by a session the OS killed.
 */
suspend fun syncWithConnection(context: Context) {
    val address = context.app.store.current().deviceAddress ?: return
    if (isOutputConnected(context, address)) {
        if (!SplitVolumeService.running) SplitVolumeService.start(context)
    } else if (!SplitVolumeService.running && context.app.controller.hasPendingRestore()) {
        try {
            context.app.controller.restore()
        } catch (e: Exception) {
            Log.w("ConnectionSync", "Restoring balance failed", e)
        }
    }
}
