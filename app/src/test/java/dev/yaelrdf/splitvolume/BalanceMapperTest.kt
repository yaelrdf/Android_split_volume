package dev.yaelrdf.splitvolume

import org.junit.Assert.assertEquals
import org.junit.Test

class BalanceMapperTest {
    @Test
    fun equalLevelsMeanNoBalance() {
        assertEquals(BalanceMapper.Output(8, 0f), BalanceMapper.map(50, 50, 0, 15))
    }

    @Test
    fun louderSideDrivesVolumeAndQuieterSideIsAttenuated() {
        val out = BalanceMapper.map(left = 20, right = 80, minIndex = 0, maxIndex = 15)
        assertEquals(12, out.volumeIndex)
        // ratio 0.25 -> gain 0.0625 on L -> balance +0.8085 (towards R) on AudioFlinger's curve
        assertEquals(0.8085f, out.balance, 1e-4f)
    }

    @Test
    fun leftLouderGivesNegativeBalance() {
        val out = BalanceMapper.map(left = 100, right = 50, minIndex = 0, maxIndex = 15)
        assertEquals(15, out.volumeIndex)
        assertEquals(-0.5432f, out.balance, 1e-4f)
    }

    @Test
    fun balanceMatchesMeasuredAudioFlingerCurve() {
        // dumpsys media.audio_flinger reported these quiet-side volumes for these balances.
        assertEquals(0.2f, BalanceMapper.balanceForGain(0.666667f), 1e-4f)
        assertEquals(0.5f, BalanceMapper.balanceForGain(0.291667f), 1e-4f)
        assertEquals(0.8f, BalanceMapper.balanceForGain(0.0666667f), 1e-4f)
        assertEquals(0f, BalanceMapper.balanceForGain(1f), 0f)
    }

    @Test
    fun mutedSideIsFullyOff() {
        assertEquals(1f, BalanceMapper.map(0, 60, 0, 15).balance, 0f)
        assertEquals(-1f, BalanceMapper.map(60, 0, 0, 15).balance, 0f)
    }

    @Test
    fun bothSilent() {
        assertEquals(BalanceMapper.Output(0, 0f), BalanceMapper.map(0, 0, 0, 15))
    }

    @Test
    fun levelAndIndexRoundTripSoVolumeKeyEchoesAreIgnored() {
        for (max in listOf(15, 25, 30, 50)) {
            for (index in 0..max) {
                val level = BalanceMapper.levelFor(index, 0, max)
                assertEquals("max=$max index=$index", index, BalanceMapper.indexFor(level, 0, max))
            }
        }
    }

    @Test
    fun rescaleKeepsRatio() {
        assertEquals(40 to 10, BalanceMapper.rescale(80, 20, 40))
        assertEquals(30 to 30, BalanceMapper.rescale(0, 0, 30))
    }
}
