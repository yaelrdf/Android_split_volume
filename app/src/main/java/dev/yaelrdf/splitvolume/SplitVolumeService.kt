package dev.yaelrdf.splitvolume

import android.app.ForegroundServiceStartNotAllowedException
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Handler
import android.os.IBinder
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Lives exactly as long as the chosen headphones are the media output. It applies the saved levels, keeps them
 * in sync with sliders, notification buttons and volume keys, and restores the phone's balance when the
 * headphones go away.
 */
class SplitVolumeService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var session: Job? = null
    private var ending = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        running = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(
            Notifications.SESSION_ID,
            Notifications.status(this, "Waiting for headphones…", "Getting the audio route ready"),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE,
        )
        Notifications.cancel(this, Notifications.PROMPT_ID)
        if (intent?.action == ACTION_STOP) {
            endSession()
        } else if (session?.isActive != true && !ending) {
            session = scope.launch { runSession() }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        running = false
        scope.cancel()
        super.onDestroy()
    }

    private suspend fun runSession() {
        val controller = app.controller
        val store = app.store
        val profile = store.current()
        val address = profile.deviceAddress ?: return finish()
        val name = profile.deviceName ?: "headphones"
        controller.setState(SplitController.State.Waiting(name))

        val routed = withTimeoutOrNull(ROUTE_TIMEOUT_MS) {
            while (!isOutputConnected(this@SplitVolumeService, address)) delay(ROUTE_POLL_MS)
        } != null
        if (!routed) return finish()
        // Give the headphones a moment to become the active media route, so the volume we set lands on them.
        delay(ROUTE_SETTLE_MS)

        try {
            controller.activate(store.current())
        } catch (e: Exception) {
            return fail(e)
        }
        controller.setState(SplitController.State.Active(name))

        val audio = getSystemService(AudioManager::class.java)
        val deviceCallback = object : AudioDeviceCallback() {
            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) {
                if (removedDevices.any { it.isBluetoothOutput(address) }) endSession()
            }
        }
        val volumeReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.getIntExtra(EXTRA_VOLUME_STREAM_TYPE, -1) == AudioManager.STREAM_MUSIC) {
                    scope.launch { controller.onExternalVolumeChange() }
                }
            }
        }
        audio.registerAudioDeviceCallback(deviceCallback, Handler(mainLooper))
        ContextCompat.registerReceiver(
            this, volumeReceiver, IntentFilter(VOLUME_CHANGED_ACTION), ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        try {
            store.profile.collectLatest { p ->
                delay(APPLY_DEBOUNCE_MS)
                try {
                    controller.apply(p)
                    controller.setState(SplitController.State.Active(name))
                    Notifications.show(this, Notifications.SESSION_ID, Notifications.controls(this, p))
                } catch (e: Exception) {
                    Log.w(TAG, "Applying levels failed", e)
                    controller.setState(SplitController.State.Error(e.message ?: "Couldn't apply levels"))
                }
            }
        } finally {
            audio.unregisterAudioDeviceCallback(deviceCallback)
            unregisterReceiver(volumeReceiver)
        }
    }

    private fun endSession() {
        if (ending) return
        ending = true
        val job = session
        scope.launch {
            job?.cancelAndJoin()
            try {
                app.controller.restore()
            } catch (e: Exception) {
                Log.w(TAG, "Restoring balance failed", e)
            }
            finish()
        }
    }

    private fun fail(e: Exception) {
        Log.w(TAG, "Activating failed", e)
        val message = e.message ?: "Couldn't change the audio settings"
        app.controller.setState(SplitController.State.Error(message))
        Notifications.show(this, Notifications.SESSION_ID, Notifications.status(this, "Split volume failed", message))
    }

    private fun finish() {
        if (app.controller.state.value !is SplitController.State.Error) app.controller.setState(SplitController.State.Idle)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    companion object {
        private const val TAG = "SplitVolumeService"
        private const val ACTION_STOP = "dev.yaelrdf.splitvolume.STOP"
        // Not public API, but sent by AudioService on every OEM build we care about.
        private const val VOLUME_CHANGED_ACTION = "android.media.VOLUME_CHANGED_ACTION"
        private const val EXTRA_VOLUME_STREAM_TYPE = "android.media.EXTRA_VOLUME_STREAM_TYPE"
        private const val ROUTE_TIMEOUT_MS = 20_000L
        private const val ROUTE_POLL_MS = 500L
        private const val ROUTE_SETTLE_MS = 1_500L
        private const val APPLY_DEBOUNCE_MS = 60L

        @Volatile var running = false
            private set

        /** Returns false when Android refuses a background start (no companion association / battery exemption). */
        fun start(context: Context): Boolean = try {
            context.startForegroundService(Intent(context, SplitVolumeService::class.java))
            true
        } catch (e: ForegroundServiceStartNotAllowedException) {
            Log.w(TAG, "Not allowed to start from the background", e)
            false
        }

        fun stop(context: Context) {
            context.startService(Intent(context, SplitVolumeService::class.java).setAction(ACTION_STOP))
        }
    }
}
