package dev.yaelrdf.splitvolume

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.audiofx.DynamicsProcessing
import android.util.Log
import org.lsposed.hiddenapibypass.HiddenApiBypass

/**
 * adb shell am broadcast -n dev.yaelrdf.splitvolume/.DiagReceiver --es cmd <cmd> [--es arg <value>]
 * cmds: balance <f>, mono <0|1>, get, effect <dbL,dbR>, effect_off, levels <L,R> (same path as the sliders)
 */
@SuppressLint("PrivateApi") // Debug tooling for the same hidden API SystemBalance uses.
class DiagReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val cmd = intent.getStringExtra("cmd") ?: return
        val arg = intent.getStringExtra("arg")
        val result = runCatching {
            HiddenApiBypass.addHiddenApiExemptions("Landroid/media/AudioSystem;")
            val audioSystem = Class.forName("android.media.AudioSystem")
            when (cmd) {
                "balance" -> audioSystem.getMethod("setMasterBalance", Float::class.javaPrimitiveType).invoke(null, arg!!.toFloat())
                "mono" -> audioSystem.getMethod("setMasterMono", Boolean::class.javaPrimitiveType).invoke(null, arg == "1")
                "vol" -> context.getSystemService(android.media.AudioManager::class.java).let { am ->
                    val s = android.media.AudioManager.STREAM_MUSIC
                    if (arg != null) am.setStreamVolume(s, arg.toInt(), 0)
                    "music ${am.getStreamVolume(s)} of ${am.getStreamMinVolume(s)}..${am.getStreamMaxVolume(s)}"
                }
                "get" -> "mono=" + audioSystem.getMethod("getMasterMono").invoke(null) +
                    " balance=" + audioSystem.getMethod("getMasterBalance").invoke(null)
                "effect" -> {
                    val (l, r) = arg!!.split(",").map { it.toFloat() }
                    val dp = effect ?: DynamicsProcessing(
                        Int.MAX_VALUE, 0,
                        DynamicsProcessing.Config.Builder(
                            DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION, 2, false, 0, false, 0, false, 0, false,
                        ).build(),
                    ).also { effect = it }
                    dp.setInputGainbyChannel(0, l)
                    dp.setInputGainbyChannel(1, r)
                    dp.enabled = true
                    "enabled=${dp.enabled} hasControl=${dp.hasControl()} id=${dp.id} L=${dp.getInputGainByChannelIndex(0)} R=${dp.getInputGainByChannelIndex(1)}"
                }
                "levels" -> {
                    val (l, r) = arg!!.split(",").map { it.toInt() }
                    kotlinx.coroutines.runBlocking {
                        context.app.store.update { it.withLevel(Side.L, l).withLevel(Side.R, r) }
                    }
                    "stored L=$l R=$r"
                }
                "effect_off" -> effect?.release().also { effect = null }
                else -> "unknown cmd"
            }
        }
        Log.i(TAG, "$cmd($arg) -> ${result.getOrElse { "FAILED: $it / ${it.cause}" }}")
        resultData = result.getOrElse { "FAILED: $it / ${it.cause}" }.toString()
    }

    companion object {
        private const val TAG = "SplitDiag"
        private var effect: DynamicsProcessing? = null
    }
}
