package online.k73.bmwlauncher.music.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameMillis
import android.media.audiofx.Visualizer
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.pow

/**
 * Audio-reactive equalizer source.
 *
 * [fftToBands] is a pure function (unit-tested) that turns one Visualizer FFT frame into
 * [nBands] perceptual magnitudes in 0..1, bass→treble, log-spaced across the spectrum.
 * [rememberAudioSpectrum] wraps [Visualizer] on the *global output mix* (session 0) so it
 * reacts to whatever is playing (Yandex Music runs in its own process). If the Visualizer
 * can't be created — no RECORD_AUDIO / ROM blocks session-0 capture — it returns a state
 * that stays null and the caller falls back to the synthetic animation.
 */

/** Android FFT layout (captureSize = n bytes):
 *  fft[0]=Re[0] (DC), fft[1]=Re[n/2] (Nyquist), then fft[2k],fft[2k+1] = Re[k],Im[k] for k in 1..n/2-1.
 *  Returns [nBands] values in 0..1, index 0 = lowest frequency band. */
fun fftToBands(fft: ByteArray, nBands: Int): FloatArray {
    val out = FloatArray(nBands)
    if (nBands <= 0 || fft.size < 4) return out
    val half = fft.size / 2                       // number of complex bins (incl. DC)
    val usable = half - 1                          // skip DC (bin 0); bins 1..half-1 carry the spectrum
    if (usable < 1) return out
    // Log-spaced band edges over bins [1, half) so low frequencies get their own bars
    // instead of being crushed into one — matches how the ear hears octaves.
    val minBin = 1.0
    val maxBin = half.toDouble()
    for (b in 0 until nBands) {
        val lo = (minBin * (maxBin / minBin).pow(b.toDouble() / nBands)).toInt().coerceIn(1, half - 1)
        val hi = (minBin * (maxBin / minBin).pow((b + 1).toDouble() / nBands)).toInt().coerceIn(lo + 1, half)
        var peak = 0f
        for (k in lo until hi) {
            val re = fft[2 * k].toInt().toFloat()
            val im = fft[2 * k + 1].toInt().toFloat()
            val mag = hypot(re, im)
            if (mag > peak) peak = mag
        }
        // Perceptual (log) compression: raw byte magnitudes span ~0..180. ln maps the wide
        // dynamic range into a pleasant 0..1 with a small noise floor cut.
        val norm = (ln(1f + peak) / LN_SCALE).coerceIn(0f, 1f)
        out[b] = norm
    }
    return out
}

private const val LN_SCALE = 5.1f   // ln(1+~165) ≈ 5.1 → loud bins reach ~1.0

/** Holds the smoothed band state; caller reads [State.value] (null ⇒ no live audio, use fallback). */
@Composable
fun rememberAudioSpectrum(active: Boolean, nBands: Int): State<FloatArray?> {
    val out = remember { mutableStateOf<FloatArray?>(null) }
    val latest = remember { AtomicReference<ByteArray?>(null) }
    val smoothed = remember { FloatArray(nBands) }

    DisposableEffect(active, nBands) {
        var vis: Visualizer? = null
        if (active) {
            vis = try {
                Visualizer(0).apply {
                    enabled = false
                    captureSize = Visualizer.getCaptureSizeRange()[1].coerceAtMost(1024)
                    setDataCaptureListener(
                        object : Visualizer.OnDataCaptureListener {
                            override fun onWaveFormDataCapture(v: Visualizer?, wave: ByteArray?, rate: Int) {}
                            override fun onFftDataCapture(v: Visualizer?, fft: ByteArray?, rate: Int) {
                                if (fft != null) latest.set(fft.copyOf())
                            }
                        },
                        Visualizer.getMaxCaptureRate(), false, true, // fft only
                    )
                    enabled = true
                }
            } catch (t: Throwable) {
                null
            }
        }
        if (vis == null) out.value = null
        onDispose {
            latest.set(null)
            smoothed.fill(0f)
            out.value = null
            try { vis?.enabled = false; vis?.release() } catch (_: Throwable) {}
        }
    }

    // Frame-synced smoothing on the main thread: fast attack, slow peak-decay = classic
    // analyzer bounce. Only publishes once real FFT frames arrive (else stays null → fallback).
    LaunchedEffect(active, nBands) {
        if (!active) return@LaunchedEffect
        while (true) {
            withFrameMillis {
                val fft = latest.get()
                if (fft != null) {
                    val bands = fftToBands(fft, nBands)
                    for (i in 0 until nBands) {
                        val decayed = smoothed[i] * DECAY
                        smoothed[i] = if (bands[i] > decayed) bands[i] else decayed
                    }
                    out.value = smoothed.copyOf()
                }
            }
        }
    }
    return out
}

private const val DECAY = 0.86f
