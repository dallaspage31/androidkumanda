package com.androidkumanda

import org.junit.Assert.*
import org.junit.Test

class KeyRouterTest {
    private val key = KeySignature(200, 500)
    private fun press(action: Int, repeat: Int = 0, device: Int = 1, time: Long = 100) =
        KeyStroke(key, action, repeat, device, time, false)

    @Test fun `mapped press consumes both edges and launches once`() {
        val router = KeyRouter()
        var launches = 0
        val launch = { launches++; true }
        assertTrue(router.route(press(0), "target", launch))
        assertTrue(router.route(press(0, 1), "target", launch))
        assertTrue(router.route(press(1), "target", launch))
        assertEquals(1, launches)
    }
    @Test fun `failed launch passes the whole stream through`() {
        val router = KeyRouter()
        assertFalse(router.route(press(0), "missing") { false })
        assertFalse(router.route(press(0, 1), "missing") { true })
        assertFalse(router.route(press(1), "missing") { true })
    }
    @Test fun `unmapped keys pass through`() {
        val router = KeyRouter()
        assertFalse(router.route(press(0), null) { error("must not launch") })
        assertFalse(router.route(press(1), null) { error("must not launch") })
    }
    @Test fun `mapping change while pressed still consumes matching up`() {
        val router = KeyRouter()
        assertTrue(router.route(press(0), "target") { true })
        assertTrue(router.route(press(1), null) { error("up must not launch") })
    }
    @Test fun `other device up cannot finish a held key`() {
        val router = KeyRouter()
        router.route(press(0), "target") { true }
        assertFalse(router.route(press(1, device = 2), "target") { true })
        assertTrue(router.route(press(1), "target") { true })
    }
    @Test fun `new press works even if previous up was lost`() {
        val router = KeyRouter()
        var count = 0
        router.route(press(0), "target") { count++; true }
        router.route(press(0, time = 200), "target") { count++; true }
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
}
