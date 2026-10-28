package org.roruku.smartformatconverter.engine.media

/**
 * Canonical DISPLAY-space video dimensions. MediaExtractor gives encoded
 * track width/height; MediaMetadataRetriever dimensions vary by Android
 * implementation and can already include the rotation metadata. Never apply
 * retriever rotation to its width/height a second time.
 */
object VideoSourceGeometry {
    data class Result(
        val display: AdaptiveResolutionPolicy.Size?,
        val fromEncodedTrack: Boolean,
    )

    fun resolve(
        encodedWidth: Int, encodedHeight: Int,
        retrieverWidth: Int, retrieverHeight: Int,
        rotationDegrees: Int,
    ): Result {
        if (encodedWidth > 0 && encodedHeight > 0) {
            return Result(
                AdaptiveResolutionPolicy.displaySize(
                    encodedWidth, encodedHeight, rotationDegrees
                ), true
            )
        }
        // Retriever often exposes presentation-space dimensions already.
        // Without actual encoded track dimensions we cannot safely rotate
        // retriever dimensions again, so preserve their reported orientation.
        return Result(AdaptiveResolutionPolicy.displaySize(
            retrieverWidth, retrieverHeight, 0
        ), false)
    }
}
