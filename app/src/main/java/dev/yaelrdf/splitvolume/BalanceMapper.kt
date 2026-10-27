package dev.yaelrdf.splitvolume

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Turns two independent side levels (0..100) into what Android can actually do:
 * one STREAM_MUSIC volume (driven by the louder side) plus a master balance that attenuates the quieter side.
 */
object BalanceMapper {
    data class Output(val volumeIndex: Int, val balance: Float)

    fun map(left: Int, right: Int, minIndex: Int, maxIndex: Int): Output {
        val l = left.coerceIn(0, 100)
        val r = right.coerceIn(0, 100)
        val loud = max(l, r)
        val index = indexFor(loud, minIndex, maxIndex)
        if (loud == 0 || l == r) return Output(index, 0f)

        // Squaring the ratio makes half the slider sound roughly half as loud (about -12 dB)
        // instead of barely quieter (-6 dB).
        val ratio = min(l, r).toFloat() / loud
        val attenuation = balanceForGain(ratio * ratio)
        // Positive balance keeps R and attenuates L.
        return Output(index, if (r > l) attenuation else -attenuation)
    }

    /**
     * AudioFlinger doesn't scale the quieter side linearly: with x = 1 - |balance| it applies
     * gain = (x + 5x²) / 6 (measured on ColorOS 15 via dumpsys media.audio_flinger). This inverts that.
     */
    fun balanceForGain(gain: Float): Float {
        val x = (-1f + sqrt(1f + 120f * gain.coerceIn(0f, 1f))) / 10f
        return (1f - x).coerceIn(0f, 1f)
    }

    fun indexFor(level: Int, minIndex: Int, maxIndex: Int): Int =
        (minIndex + level.coerceIn(0, 100) / 100f * (maxIndex - minIndex)).roundToInt()

    fun levelFor(index: Int, minIndex: Int, maxIndex: Int): Int {
        if (maxIndex <= minIndex) return 0
        return ((index - minIndex) * 100f / (maxIndex - minIndex)).roundToInt().coerceIn(0, 100)
    }

    /** Moves the louder side to [newLoud] and scales the quieter side by the same factor. */
    fun rescale(left: Int, right: Int, newLoud: Int): Pair<Int, Int> {
        val loud = max(left, right)
        if (loud == 0) return newLoud to newLoud
        val factor = newLoud.toFloat() / loud
        return (left * factor).roundToInt().coerceIn(0, 100) to (right * factor).roundToInt().coerceIn(0, 100)
    }
}
