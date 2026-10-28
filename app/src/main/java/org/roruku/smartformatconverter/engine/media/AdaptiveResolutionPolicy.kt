package org.roruku.smartformatconverter.engine.media

import kotlin.math.roundToInt

/**
 * Device-independent, orientation-aware resolution planning.
 *
 * A quality tier refers to the SHORT display edge, so 1080p is 1920x1080
 * for 16:9 landscape and 1080x1440 for 3:4 portrait. Output dimensions are
 * rounded to even pixels for common YUV420 encoders.
 */
object AdaptiveResolutionPolicy {
    data class Size(val width: Int, val height: Int) {
        override fun toString(): String = "${width}×${height}"
    }

    data class Tier(val id: String, val label: String, val shortEdge: Int)

    val tiers: List<Tier> = listOf(
        Tier("2160", "4K (2160p)", 2160),
        Tier("1440", "1440p", 1440),
        Tier("1080", "1080p", 1080),
        Tier("720", "720p", 720),
        Tier("480", "480p", 480),
        Tier("360", "360p", 360),
        Tier("240", "240p", 240),
        Tier("144", "144p", 144),
    )

    fun displaySize(width: Int, height: Int, rotation: Int): Size? {
        if (width <= 0 || height <= 0) return null
        val r = ((rotation % 360) + 360) % 360
        return if (r == 90 || r == 270) Size(height, width) else Size(width, height)
    }

    private fun even(value: Double): Int =
        ((value / 2.0).roundToInt() * 2).coerceAtLeast(2)

    fun scaledToShortEdge(source: Size, shortEdge: Int): Size? {
        if (source.width <= 0 || source.height <= 0 || shortEdge <= 0) return null
        val inputShort = minOf(source.width, source.height)
        val targetLong = even(maxOf(source.width, source.height).toDouble() * shortEdge / inputShort)
        val output = if (source.width <= source.height) Size(shortEdge, targetLong)
            else Size(targetLong, shortEdge)
        return output.takeIf { valid(it) }
    }

    fun scaleToWidth(source: Size, width: Int): Size? {
        if (source.width <= 0 || source.height <= 0 || width <= 0) return null
        return Size(width, even(source.height.toDouble() * width / source.width))
            .takeIf { valid(it) }
    }

    fun scaleToHeight(source: Size, height: Int): Size? {
        if (source.width <= 0 || source.height <= 0 || height <= 0) return null
        return Size(even(source.width.toDouble() * height / source.height), height)
            .takeIf { valid(it) }
    }

    fun valid(size: Size): Boolean =
        size.width in 16..8192 && size.height in 16..8192 &&
            size.width % 2 == 0 && size.height % 2 == 0

    fun isUpscale(source: Size, output: Size): Boolean =
        output.width > source.width || output.height > source.height

    /** Invalid, out-of-range, or forbidden-upscale tiers are not selectable. */
    fun availableTiers(source: Size, preventUpscale: Boolean): List<Pair<Tier, Size>> =
        tiers.mapNotNull { tier ->
            val size = scaledToShortEdge(source, tier.shortEdge) ?: return@mapNotNull null
            if (preventUpscale && isUpscale(source, size)) null else tier to size
        }
}
