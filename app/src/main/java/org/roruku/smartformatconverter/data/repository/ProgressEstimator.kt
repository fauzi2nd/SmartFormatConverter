package org.roruku.smartformatconverter.data.repository

import java.util.Locale
import kotlin.math.ceil

data class ProgressReading(val speed: String, val eta: String)

/**
 * Elapsed-time estimator based on actual work completion instead of arbitrary
 * 1x/2x multipliers. Speed is expressed as percentage points per second and
 * has the same meaning for image, audio, video and SAF output copying.
 *
 * A short warm-up intentionally reports "Measuring…" rather than fake values.
 */
class ProgressEstimator(private val monotonicNanos: () -> Long = System::nanoTime) {
    private val startedNanos: Long = monotonicNanos()

    fun measure(progress: Float): ProgressReading {
        val seconds = (monotonicNanos() - startedNanos).coerceAtLeast(0L) / 1_000_000_000.0
        val fraction = progress.coerceIn(0f, 0.999f).toDouble()
        if (seconds < 0.5 || fraction < 0.015) {
            return ProgressReading("Measuring…", "Measuring…")
        }
        val percentagePerSec = (100 * fraction / seconds).coerceAtLeast(0.0)
        val remaining = ceil(((1 - fraction) / fraction) * seconds).toLong().coerceAtLeast(0L)
        val etaText = if (remaining >= 3600) {
            String.format(Locale.US, "%02d:%02d:%02d",
                remaining / 3600, (remaining / 60) % 60, remaining % 60)
        } else {
            String.format(Locale.US, "%02d:%02d", remaining / 60, remaining % 60)
        }
        return ProgressReading(
            String.format(Locale.US, "%.1f%%/s", percentagePerSec),
            etaText
        )
    }
}
