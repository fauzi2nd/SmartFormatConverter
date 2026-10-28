package org.roruku.smartformatconverter.engine.media

/**
 * Device reports are advisory, but obviously unavailable encoder modes or
 * rejected size/fps combinations must never be added to the conversion queue.
 */
object VideoEncoderEligibility {
    data class Candidate(val name: String, val hardware: Boolean, val sizeAndRateSupported: Boolean?)

    fun rejection(
        mime: String,
        encoderChoice: String,
        width: Int,
        height: Int,
        fps: Float,
        candidates: List<Candidate>
    ): String? {
        val matching = candidates.filter {
            VideoEncoderSelection.matches(encoderChoice, it.name, it.hardware)
        }
        val label = VideoEncoderSelection.displayName(encoderChoice)
        if (matching.isEmpty()) {
            return "No $label encoder is installed for $mime. Change Video encoder to Auto " +
                "or Software, or choose a different output codec."
        }
        if (width > 0 && height > 0 && matching.all { it.sizeAndRateSupported == false }) {
            val rate = fps.takeIf { it > 0 && it.isFinite() }?.let { " at %.2f fps".format(java.util.Locale.US, it) } ?: ""
            return "$label $mime encoder does not report support for ${width}×${height}$rate. " +
                "Select a supported resolution or codec. Smart Auto will not resize silently."
        }
        // Unknown/null means capability probing was inconclusive, not unsupported.
        // Runtime initialization can still fail even after a positive report.
        return null
    }
}
