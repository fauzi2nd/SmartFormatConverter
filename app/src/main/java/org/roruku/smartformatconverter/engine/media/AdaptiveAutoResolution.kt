package org.roruku.smartformatconverter.engine.media

/** Choose largest reported-compatible display size without upscaling.
 * Probes the original and standard 4K–144p tiers, then refines the highest
 * supported bracket with at most eight size probes. Capability reports
 * remain advisory and are not a guarantee of a successful export.
 */
object AdaptiveAutoResolution {
    data class Choice(val size: AdaptiveResolutionPolicy.Size?, val verified: Boolean)

    fun choose(
        source: AdaptiveResolutionPolicy.Size,
        support: (AdaptiveResolutionPolicy.Size) -> Boolean?
    ): Choice {
        if (!AdaptiveResolutionPolicy.valid(source)) return Choice(null, false)
        val tiers = (listOf(source) +
            AdaptiveResolutionPolicy.availableTiers(source, true).map { it.second })
            .distinct().sortedByDescending { it.width.toLong() * it.height }
        val checked = mutableMapOf<AdaptiveResolutionPolicy.Size, Boolean?>()
        fun supported(size: AdaptiveResolutionPolicy.Size): Boolean? {
            if (checked.containsKey(size)) return checked[size]
            return support(size).also { checked[size] = it }
        }
        val best = tiers.firstOrNull { supported(it) == true }
        if (best != null) {
            val originalShort = minOf(source.width, source.height)
            var low = minOf(best.width, best.height)
            var high = originalShort
            repeat(8) {
                if (high - low <= 2) return@repeat
                val middle = ((low + high) / 4) * 2
                if (middle <= low || middle >= high) return@repeat
                val candidate = AdaptiveResolutionPolicy.scaledToShortEdge(source, middle)
                if (candidate != null && !AdaptiveResolutionPolicy.isUpscale(source, candidate) &&
                    supported(candidate) == true) low = middle
                else high = middle
            }
            val refined = AdaptiveResolutionPolicy.scaledToShortEdge(source, low)
            return Choice(refined?.takeIf { supported(it) == true } ?: best, true)
        }
        // Inconclusive API results are not treated as definite failure.
        return Choice(tiers.firstOrNull { supported(it) == null }, false)
    }
}
