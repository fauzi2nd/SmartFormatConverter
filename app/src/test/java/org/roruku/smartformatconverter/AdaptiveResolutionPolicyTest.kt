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
        val source = policy.Size(1320, 1760)
        assertEquals(policy.Size(1080, 1440), policy.scaledToShortEdge(source, 1080))
        assertEquals(policy.Size(720, 960), policy.scaledToShortEdge(source, 720))
        assertEquals(policy.Size(144, 192), policy.scaledToShortEdge(source, 144))
        assertEquals(policy.Size(2160, 2880), policy.scaledToShortEdge(source, 2160))
        assertEquals(listOf("1080", "720", "480", "360", "240", "144"),
            policy.availableTiers(source, preventUpscale = true).map { it.first.id })
    }

    @Test fun landscapeAndRotationMetadataPreserveCorrectDisplayDimensions() {
        assertEquals(policy.Size(1920, 1080),
            policy.scaledToShortEdge(policy.Size(3840, 2160), 1080))
        val portrait = policy.displaySize(1760, 1320, 90)
        assertEquals(policy.Size(1320, 1760), portrait)
        assertEquals(policy.Size(720, 960), policy.scaledToShortEdge(portrait!!, 720))
        assertEquals(policy.Size(3840, 2160),
            policy.scaledToShortEdge(policy.Size(3840, 2160), 2160))
    }

    @Test fun deviceCanOffer4kAndNoUpscaleOptionOnlyHidesLargerSizes() {
        val source = policy.Size(3840, 2160)
        assertEquals("2160", policy.availableTiers(source, true).first().first.id)
        assertTrue(policy.availableTiers(policy.Size(640, 480), false).any {
            it.first.id == "2160"
        })
        assertFalse(policy.availableTiers(policy.Size(640, 480), true).any {
            it.first.id == "2160"
        })
    }

    @Test fun customDimensionsAreEvenAndValidateBounds() {
        val source = policy.Size(1320, 1760)
        assertEquals(policy.Size(990, 1320), policy.scaleToWidth(source, 990))
        assertEquals(policy.Size(960, 1280), policy.scaleToHeight(source, 1280))
        assertFalse(policy.valid(policy.Size(991, 1320)))
        assertNull(policy.scaleToWidth(source, 9000))
        assertTrue(policy.isUpscale(source, policy.Size(2160, 2880)))
    }
}
