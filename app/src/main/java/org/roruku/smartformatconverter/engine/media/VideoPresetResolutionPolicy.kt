package org.roruku.smartformatconverter.engine.media

/**
 * Independent resolution strategy for each quick preset. All sizes use
 * display orientation and preserve source aspect ratio without upscaling.
 * Standard quality tiers are exact presets; only Smart Auto refines between
 * tiers to use the highest device-reported compatible dimensions.
 */
object VideoPresetResolutionPolicy {
    data class Choice(
        val size: AdaptiveResolutionPolicy.Size?,
        val verified: Boolean,
        val targetMinShortEdge: Int,
        val targetMaxShortEdge: Int,
        val belowTarget: Boolean,
    )

    data class Range(val min: Int, val max: Int)

    fun range(preset: String): Range? = when (preset) {
        "High Quality" -> Range(720, 2160)
        "Balanced" -> Range(360, 480)
        "Small File" -> Range(144, 240)
        else -> null
    }

    fun choose(
        preset: String,
        source: AdaptiveResolutionPolicy.Size,
        support: (AdaptiveResolutionPolicy.Size) -> Boolean?,
    ): Choice {
        if (!AdaptiveResolutionPolicy.valid(source))
            return Choice(null, false, 0, 0, false)
        val range = range(preset)
        if (range == null) {
            val best = AdaptiveAutoResolution.choose(source, support)
            return Choice(best.size, best.verified, 0, 0, false)
        }

        val sourceShort = minOf(source.width, source.height)
        val lower = range.min
        val upper = range.max
        val tiers = AdaptiveResolutionPolicy.availableTiers(source, true)
            .filter { (_, size) -> minOf(size.width, size.height) <= upper }

        // High Quality prioritizes source if it is BELOW its own minimum.
        // It never enlarges a 480p source to 720p.
        val sourceCandidate = if (sourceShort < lower) listOf(source) else emptyList()
        val primary = sourceCandidate + tiers
            .filter { (tier, _) -> tier.shortEdge in lower..upper }
            .map { it.second }
        val fallback = tiers.filter { (tier, _) -> tier.shortEdge < lower }
            .map { it.second }
        val candidates = (primary + fallback).distinct()
        var firstUnknown: AdaptiveResolutionPolicy.Size? = null
        for (candidate in candidates) {
            when (support(candidate)) {
                true -> return Choice(
                    candidate, true, lower, upper,
                    sourceShort >= lower && minOf(candidate.width, candidate.height) < lower
                )
                null -> if (firstUnknown == null) firstUnknown = candidate
                false -> Unit
            }
        }
        // Device APIs sometimes return inconclusive reports. Keep an unknown
        // candidate only if nothing was positively supported, with no guarantee.
        return Choice(firstUnknown, false, lower, upper, false)
    }
}
