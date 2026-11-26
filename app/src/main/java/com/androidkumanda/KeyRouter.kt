package com.androidkumanda

data class KeySignature(val code: Int, val scan: Int) {
    fun matches(other: KeySignature): Boolean {
        if (code != other.code) return false
        if (scan > 0 && other.scan > 0) return scan == other.scan
        return code != 0
    }
    fun isLearnable(): Boolean {
        if (code == 0) return scan > 0
        // Keep navigation, typing, volume, power and input selection usable.
        return code > 0 && code !in 1..84 && code !in 91..95 && code !in 111..123 &&
            code !in 144..163 && code !in setOf(164, 177, 178, 187, 219, 224, 225, 257, 280)
    }
}

data class KeyStroke(val key: KeySignature, val action: Int, val repeat: Int,
                     val device: Int, val downTime: Long, val canceled: Boolean)

class KeyRouter {
    private data class Press(val device: Int, val key: KeySignature)
    private data class Held(val time: Long, val consumed: Boolean)
    private val held = linkedMapOf<Press, Held>()

    fun route(event: KeyStroke, target: String?, launch: () -> Boolean): Boolean {
        val id = Press(event.device, event.key)
        if (event.action == 1) {
            val previous = held[id] ?: return false
            if (previous.time != event.downTime) return false
            held.remove(id)
            return previous.consumed
        }
        if (event.action != 0) return false
        val previous = held[id]
        if (previous?.time == event.downTime) return previous.consumed
        if (event.repeat > 0 || event.canceled) return false
        val consume = target != null && launch()
        // Bound state if faulty hardware never sends an UP event.
        if (held.size >= 32) held.remove(held.keys.first())
        held[id] = Held(event.downTime, consume)
        return consume
    }
}
