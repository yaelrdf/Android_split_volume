package dev.yaelrdf.splitvolume

import android.annotation.SuppressLint
import android.util.Log
import org.lsposed.hiddenapibypass.HiddenApiBypass
import java.lang.reflect.Method

/**
 * Android's master balance, the stage behind the accessibility "Audio balance" slider. AudioFlinger applies it
 * to every app after effects and after the system "Mono audio" downmix, which is exactly what sharing earbuds
 * needs. The hidden AudioSystem call only requires MODIFY_AUDIO_SETTINGS, so no Shizuku or adb.
 *
 * Mono itself can't be set from here: AudioPolicyService only accepts setMasterMono from system processes
 * (verified on ColorOS 15), so the user turns on the system "Mono audio" switch and we only read it.
 *
 * Values set this way aren't persisted; a reboot falls back to the phone's own setting.
 */
class SystemBalance private constructor(
    private val setMasterBalance: Method,
    private val getMasterBalance: Method,
    private val getMasterMono: Method,
) {
    var balance: Float
        get() = getMasterBalance.invoke(null) as Float
        set(value) {
            val status = setMasterBalance.invoke(null, value) as Int
            check(status == SUCCESS) { "Android refused setMasterBalance (status $status)" }
        }

    val monoOn: Boolean get() = getMasterMono.invoke(null) as Boolean

    companion object {
        private const val TAG = "SystemBalance"
        private const val SUCCESS = 0

        /** Returns null when this phone doesn't let an app reach the hidden calls. */
        @SuppressLint("PrivateApi") // The hidden API is the whole point; failure is handled by returning null.
        fun create(): SystemBalance? = try {
            HiddenApiBypass.addHiddenApiExemptions("Landroid/media/AudioSystem;")
            val audioSystem = Class.forName("android.media.AudioSystem")
            SystemBalance(
                setMasterBalance = audioSystem.getMethod("setMasterBalance", Float::class.javaPrimitiveType),
                getMasterBalance = audioSystem.getMethod("getMasterBalance"),
                getMasterMono = audioSystem.getMethod("getMasterMono"),
            ).also {
                // Writing back the current value proves AudioFlinger accepts our calls, without changing anything.
                it.balance = it.balance
            }
        } catch (e: Exception) {
            Log.w(TAG, "System balance unavailable", e)
            null
        }
    }
}
