package online.k73.bmwlauncher.music

import online.k73.bmwlauncher.music.ui.fftToBands
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioSpectrumTest {

    /** Build a 128-byte FFT frame (64 complex bins) with a peak magnitude at [bin]. */
    private fun fftWithPeakAt(bin: Int, value: Byte = 120): ByteArray {
        val fft = ByteArray(128)
        fft[2 * bin] = value      // real part
        fft[2 * bin + 1] = 0
        return fft
    }

    @Test
    fun silence_returns_all_zero() {
        val bands = fftToBands(ByteArray(128), 8)
        assertEquals(8, bands.size)
        bands.forEach { assertEquals(0f, it, 0.001f) }
    }

    @Test
    fun tiny_or_empty_input_is_safe() {
        assertTrue(fftToBands(ByteArray(0), 8).all { it == 0f })
        assertTrue(fftToBands(ByteArray(2), 8).all { it == 0f })
        assertEquals(0, fftToBands(ByteArray(128), 0).size)
    }

    @Test
    fun bass_energy_lights_low_bands_not_high() {
        val bands = fftToBands(fftWithPeakAt(1), 8)
        assertTrue("low band should be loud", bands.first() > 0.5f)
        assertTrue("high band should stay quiet", bands.last() < 0.1f)
    }

    @Test
    fun treble_energy_lights_high_bands_not_low() {
        val bands = fftToBands(fftWithPeakAt(50), 8)
        assertTrue("high band should be loud", bands.last() > 0.5f)
        assertTrue("low band should stay quiet", bands.first() < 0.1f)
    }

    @Test
    fun output_is_normalized_0_to_1() {
        // Max out every bin; nothing should exceed 1.0.
        val fft = ByteArray(128) { if (it % 2 == 0) 127 else 0 }
        fftToBands(fft, 16).forEach { assertTrue(it in 0f..1f) }
    }

    @Test
    fun louder_bin_gives_higher_band_than_quieter_bin() {
        val loud = fftToBands(fftWithPeakAt(1, 120), 8).first()
        val quiet = fftToBands(fftWithPeakAt(1, 10), 8).first()
        assertTrue(loud > quiet)
    }
}
