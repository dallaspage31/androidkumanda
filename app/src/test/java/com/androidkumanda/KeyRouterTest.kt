package com.androidkumanda

import org.junit.Assert.*
import org.junit.Test

class KeyRouterTest {
    private val key = KeySignature(200, 500)
    private val binding = KeyBinding("one", key, "target", emptySet())
    private fun press(action: Int, repeat: Int = 0, device: Int = 1, time: Long = 100) =
        KeyStroke(key, action, repeat, device, time, false)

    @Test fun `mapped press consumes both edges and launches once`() {
        val router = KeyRouter()
        var launches = 0
        val launch: (String) -> Boolean = { launches++; true }
        assertTrue(router.route(press(0), binding, "home", launch))
        assertTrue(router.route(press(0, 1), binding, "home", launch))
        assertTrue(router.route(press(1), binding, "home", launch))
        assertEquals(1, launches)
    }
    @Test fun `failed launch passes the whole stream through`() {
        val router = KeyRouter()
        assertFalse(router.route(press(0), binding, "home") { false })
        assertFalse(router.route(press(0, 1), binding, "home") { true })
        assertFalse(router.route(press(1), binding, "home") { true })
    }
    @Test fun `unmapped keys pass through`() {
        val router = KeyRouter()
        assertFalse(router.route(press(0), null, "home") { error("must not launch") })
        assertFalse(router.route(press(1), null, "home") { error("must not launch") })
    }
    @Test fun `mapping change while pressed still consumes matching up`() {
        val router = KeyRouter()
        assertTrue(router.route(press(0), binding, "home") { true })
        assertTrue(router.route(press(1), null, "home") { error("up must not launch") })
    }
    @Test fun `other device up cannot finish a held key`() {
        val router = KeyRouter()
        router.route(press(0), binding, "home") { true }
        assertFalse(router.route(press(1, device = 2), binding, "home") { true })
        assertTrue(router.route(press(1), binding, "home") { true })
    }
    @Test fun `new press works even if previous up was lost`() {
        val router = KeyRouter()
        var count = 0
        router.route(press(0), binding, "home") { count++; true }
        router.route(press(0, time = 200), binding, "home") { count++; true }
        assertEquals(2, count)
    }
    @Test fun `scan code prevents two unknown keys colliding`() {
        assertFalse(KeySignature(0, 500).matches(KeySignature(0, 501)))
        assertTrue(KeySignature(0, 500).matches(KeySignature(0, 500)))
        assertFalse(KeySignature(0, 0).isLearnable())
    }
    @Test fun `missing scan code falls back only for known key codes`() {
        assertTrue(KeySignature(200, 0).matches(KeySignature(200, 500)))
        assertFalse(KeySignature(0, 0).matches(KeySignature(0, 500)))
        assertFalse(KeySignature(200, 500).matches(KeySignature(200, 501)))
    }
    @Test fun `navigation power volume and keyboard typing cannot be captured`() {
        for (code in listOf(3,4,19,20,21,22,23,24,25,26,29,66,67,82,111,164,177,178,224,225,257,280)) {
            assertFalse("reserved $code", KeySignature(code, 500).isLearnable())
        }
        assertTrue(KeySignature(200, 500).isLearnable())
        assertTrue(KeySignature(0, 500).isLearnable())
    }

    @Test fun `excluded and unknown foreground apps pass through`() {
        val router = KeyRouter()
        val excluded = binding.copy(excludedPackages = setOf("tv.example.video"))
        var launches = 0

        assertFalse(router.route(press(0), excluded, "tv.example.video") { launches++; true })
        assertFalse(router.route(press(0, time = 200), excluded, null) { launches++; true })
        assertTrue(router.route(press(0, time = 300), excluded, "tv.example.home") { launches++; true })
        assertEquals(1, launches)
    }

    @Test fun `volume repeats adjust once each while matching up never adjusts`() {
        val router = KeyRouter()
        var adjustments = 0
        val adjust: (VolumeDirection) -> VolumeAdjustmentResult = {
            adjustments++
            VolumeAdjustmentResult.INDEX_CHANGED
        }

        assertTrue(router.routeVolume(press(0), VolumeDirection.RAISE, adjust))
        assertTrue(router.routeVolume(press(0, repeat = 1), VolumeDirection.RAISE, adjust))
        assertTrue(router.routeVolume(press(1), VolumeDirection.RAISE, adjust))
        assertEquals(2, adjustments)
    }

    @Test fun `disabled or unmatched volume keys pass through without adjusting`() {
        val router = KeyRouter()
        var adjustments = 0
        val adjust: (VolumeDirection) -> VolumeAdjustmentResult = {
            adjustments++
            VolumeAdjustmentResult.INDEX_CHANGED
        }

        assertFalse(router.routeVolume(press(0), null, adjust))
        assertFalse(router.routeVolume(press(1), null, adjust))
        assertFalse(router.routeVolume(press(1, device = 2), VolumeDirection.RAISE, adjust))
        assertEquals(0, adjustments)
    }

    @Test fun `fixed or unchanged volume passes the complete event stream through`() {
        val router = KeyRouter()
        var adjustments = 0
        val adjust: (VolumeDirection) -> VolumeAdjustmentResult = {
            adjustments++
            VolumeAdjustmentResult.NO_INDEX_CHANGE
        }

        assertFalse(router.routeVolume(press(0), VolumeDirection.LOWER, adjust))
        assertFalse(router.routeVolume(press(0, repeat = 1), VolumeDirection.LOWER, adjust))
        assertFalse(router.routeVolume(press(1), VolumeDirection.LOWER, adjust))
        assertEquals(1, adjustments)
    }

    @Test fun `cancelled and unsupported events are never newly consumed`() {
        val router = KeyRouter()
        val launch: (String) -> Boolean = { true }
        assertFalse(router.route(press(0).copy(canceled = true), binding, "home", launch))
        assertFalse(router.route(press(2), binding, "home", launch))
    }

    @Test fun `held press state stays bounded`() {
        val router = KeyRouter()
        var first: KeyStroke? = null
        var last: KeyStroke? = null
        repeat(33) { index ->
            val key = KeySignature(200 + index, 500 + index)
            val stroke = KeyStroke(key, 0, 0, index, 100, false)
            val entry = KeyBinding("binding-$index", key, "target")
            assertTrue(router.route(stroke, entry, "home") { true })
            if (index == 0) first = stroke
            last = stroke
        }

        assertFalse(router.route(first!!.copy(action = 1), binding, "home") { true })
        assertTrue(router.route(last!!.copy(action = 1), binding, "home") { true })
    }
}
