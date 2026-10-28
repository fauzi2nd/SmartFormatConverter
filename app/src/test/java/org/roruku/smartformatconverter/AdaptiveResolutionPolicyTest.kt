package org.roruku.smartformatconverter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.roruku.smartformatconverter.engine.media.AdaptiveResolutionPolicy

class AdaptiveResolutionPolicyTest {
    private val policy = AdaptiveResolutionPolicy

    @Test fun portrait3by4PreservesOrientationFrom4kTo144p() {
        val source = AdaptiveResolutionPolicy.Size(1320, 1760)
        assertEquals(AdaptiveResolutionPolicy.Size(1080, 1440), policy.scaledToShortEdge(source, 1080))
        assertEquals(AdaptiveResolutionPolicy.Size(720, 960), policy.scaledToShortEdge(source, 720))
        assertEquals(AdaptiveResolutionPolicy.Size(144, 192), policy.scaledToShortEdge(source, 144))
        assertEquals(AdaptiveResolutionPolicy.Size(2160, 2880), policy.scaledToShortEdge(source, 2160))
        assertEquals(listOf("1080", "720", "480", "360", "240", "144"),
            policy.availableTiers(source, preventUpscale = true).map { it.first.id })
    }

    @Test fun landscapeAndRotationMetadataPreserveCorrectDisplayDimensions() {
        assertEquals(AdaptiveResolutionPolicy.Size(1920, 1080),
            policy.scaledToShortEdge(AdaptiveResolutionPolicy.Size(3840, 2160), 1080))
        val portrait = policy.displaySize(1760, 1320, 90)
        assertEquals(AdaptiveResolutionPolicy.Size(1320, 1760), portrait)
        assertEquals(AdaptiveResolutionPolicy.Size(720, 960), policy.scaledToShortEdge(portrait!!, 720))
        assertEquals(AdaptiveResolutionPolicy.Size(3840, 2160),
            policy.scaledToShortEdge(AdaptiveResolutionPolicy.Size(3840, 2160), 2160))
    }

    @Test fun deviceCanOffer4kAndNoUpscaleOptionOnlyHidesLargerSizes() {
        val source = AdaptiveResolutionPolicy.Size(3840, 2160)
        assertEquals("2160", policy.availableTiers(source, true).first().first.id)
        assertTrue(policy.availableTiers(AdaptiveResolutionPolicy.Size(640, 480), false).any {
            it.first.id == "2160"
        })
        assertFalse(policy.availableTiers(AdaptiveResolutionPolicy.Size(640, 480), true).any {
            it.first.id == "2160"
        })
    }

    @Test fun customDimensionsAreEvenAndValidateBounds() {
        val source = AdaptiveResolutionPolicy.Size(1320, 1760)
        assertEquals(AdaptiveResolutionPolicy.Size(990, 1320), policy.scaleToWidth(source, 990))
        assertEquals(AdaptiveResolutionPolicy.Size(960, 1280), policy.scaleToHeight(source, 1280))
        assertFalse(policy.valid(AdaptiveResolutionPolicy.Size(991, 1320)))
        assertNull(policy.scaleToWidth(source, 9000))
        assertTrue(policy.isUpscale(source, AdaptiveResolutionPolicy.Size(2160, 2880)))
    }
}
