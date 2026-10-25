package org.roruku.smartformatconverter.engine.media

/**
 * Bitrate choices stored as Int in existing version-1 queue snapshots:
 * 0 = Auto, -1 = Source track bitrate, >0 = requested kbps.
 * Existing positive bitrate snapshots remain backward-compatible.
 */
object BitrateSelection {
    const val AUTO = 0
    const val SOURCE = -1

    val videoStandardsKbps = listOf(500, 1000, 1500, 2500, 4000, 6000, 8000,
        12000, 16000, 20000, 35000, 50000)
    val audioStandardsKbps = listOf(64, 96, 128, 160, 192, 256, 320)

    /** null means let the video encoder choose its own target bitrate. */
    fun videoTargetKbps(selection: Int, sourceTrackKbps: Int?): Int? = when {
        selection > 0 -> selection
        selection == SOURCE -> sourceTrackKbps?.takeIf { it in 100..200000 }
        else -> null
    }

    /**
     * Audio codecs generally require KEY_BIT_RATE even when the user picks
     * Auto. Use a codec/channel-aware recommendation, not an arbitrary 0.
     * Source falls back to that recommendation if metadata is unavailable.
     */
    fun audioTargetKbps(selection: Int, sourceTrackKbps: Int?, autoKbps: Int): Int {
        require(autoKbps in 32..512)
        return when {
            selection > 0 -> selection
            selection == SOURCE -> sourceTrackKbps?.takeIf { it in 32..512 } ?: autoKbps
            else -> autoKbps
        }
    }

    fun audioRecommendedKbps(opus: Boolean, channels: Int): Int =
        if (opus) {
            if (channels == 1) 96 else 160
        } else {
            if (channels == 1) 128 else 192
        }
}
