package dev.yaelrdf.splitvolume

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

object TonePlayer {
    private const val SAMPLE_RATE = 48_000
    private const val FREQUENCY = 880.0
    private const val DURATION_MS = 400
    private const val FADE_SAMPLES = 1_200

    suspend fun beep(side: Side) = withContext(Dispatchers.Default) {
        val frames = SAMPLE_RATE * DURATION_MS / 1000
        // Interleaved stereo with the other channel left silent.
        val pcm = ShortArray(frames * 2)
        val channel = if (side == Side.L) 0 else 1
        for (i in 0 until frames) {
            val fade = min(1.0, min(i, frames - i).toDouble() / FADE_SAMPLES)
            val sample = sin(2 * PI * FREQUENCY * i / SAMPLE_RATE) * 0.5 * fade
            pcm[i * 2 + channel] = (sample * Short.MAX_VALUE).toInt().toShort()
        }
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                    .build()
            )
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(pcm.size * 2)
            .build()
        try {
            track.write(pcm, 0, pcm.size)
            track.play()
            delay(DURATION_MS + 100L)
        } finally {
            track.release()
        }
    }
}
