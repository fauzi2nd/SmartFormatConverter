package org.roruku.smartformatconverter.engine.media3

import kotlin.math.abs

/** Geometry is compared in display orientation, not raw codec dimensions. */
internal object VideoGeometryPolicy {
    data class Size(val width: Int, val height: Int) {
        override fun toString(): String = "${width}x${height}"
    }

    fun displaySize(width: Int, height: Int, rotation: Int): Size {
        val correctedRotation = ((rotation % 360) + 360) % 360
        return if (correctedRotation == 90 || correctedRotation == 270) {
            Size(height, width)
        } else Size(width, height)
    }

    fun unexpectedResize(
        sourceWidth: Int, sourceHeight: Int, sourceRotation: Int,
        outputWidth: Int, outputHeight: Int, outputRotation: Int
    ): String? {
        if (sourceWidth <= 0 || sourceHeight <= 0 ||
            outputWidth <= 0 || outputHeight <= 0) return null
        val expected = displaySize(sourceWidth, sourceHeight, sourceRotation)
        val actual = displaySize(outputWidth, outputHeight, outputRotation)
        if (abs(expected.width - actual.width) <= 2 &&
            abs(expected.height - actual.height) <= 2) return null
        return "Export encoder changed video resolution from $expected to $actual " +
            "without an explicit resize. Smart Auto will not silently discard detail. " +
            "Choose $actual explicitly under resolution settings, use MP4/H.265, " +
            "or select another device encoder."
    }
}
