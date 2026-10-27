package dev.yaelrdf.splitvolume

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Notification buttons only edit the stored levels; the running service applies them. */
class NotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val store = context.app.store
                when (intent.action) {
                    ACTION_ADJUST -> {
                        val side = Side.valueOf(intent.getStringExtra(EXTRA_SIDE) ?: return@launch)
                        val delta = intent.getIntExtra(EXTRA_DELTA, 0)
                        store.update { it.withLevel(side, it.level(side) + delta) }
                    }
                    ACTION_SWAP -> store.update { it.swapped() }
                }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_ADJUST = "dev.yaelrdf.splitvolume.ADJUST"
        const val ACTION_SWAP = "dev.yaelrdf.splitvolume.SWAP"
        const val EXTRA_SIDE = "side"
        const val EXTRA_DELTA = "delta"
    }
}
