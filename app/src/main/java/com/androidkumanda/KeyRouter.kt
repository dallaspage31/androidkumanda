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

data class KeyStroke(
    val key: KeySignature,
    val action: Int,
    val repeat: Int,
    val device: Int,
    val downTime: Long,
    val canceled: Boolean
)

enum class VolumeDirection { RAISE, LOWER }

enum class VolumeAdjustmentResult { INDEX_CHANGED, FIXED_POLICY, NO_INDEX_CHANGE }

class KeyRouter {
    private enum class ActionKind { APP, VOLUME }
    private data class Press(val device: Int, val key: KeySignature)
    private data class Held(val time: Long, val consumed: Boolean, val kind: ActionKind)

    private val held = linkedMapOf<Press, Held>()

    fun route(
        event: KeyStroke,
        binding: KeyBinding?,
        foregroundPackage: String?,
        launch: (String) -> Boolean
    ): Boolean {
        val id = Press(event.device, event.key)
        if (event.action == ACTION_UP) return finish(id, event.downTime)
        if (event.action != ACTION_DOWN) return false

        val previous = held[id]
        if (previous?.time == event.downTime) return previous.consumed
        if (event.repeat > 0 || event.canceled) return false

        val eligibleBinding = binding?.takeIf {
            it.excludedPackages.isEmpty() ||
                foregroundPackage != null && foregroundPackage !in it.excludedPackages
        }
        val consumed = eligibleBinding?.let { launch(it.targetPackage) } ?: false
        remember(id, Held(event.downTime, consumed, ActionKind.APP))
        return consumed
    }

    fun routeVolume(
        event: KeyStroke,
        direction: VolumeDirection?,
        adjust: (VolumeDirection) -> VolumeAdjustmentResult
    ): Boolean {
        val id = Press(event.device, event.key)
        if (event.action == ACTION_UP) return finish(id, event.downTime)
        if (event.action != ACTION_DOWN) return false

        val previous = held[id]
        if (previous?.time == event.downTime) {
            if (previous.kind == ActionKind.VOLUME && previous.consumed &&
                direction != null && event.repeat > 0 && !event.canceled
            ) {
                adjust(direction)
            }
            return previous.consumed
        }
        if (event.repeat > 0 || event.canceled || direction == null) return false

        val consumed = adjust(direction) == VolumeAdjustmentResult.INDEX_CHANGED
        remember(id, Held(event.downTime, consumed, ActionKind.VOLUME))
        return consumed
    }

    private fun finish(id: Press, downTime: Long): Boolean {
        val previous = held[id] ?: return false
        if (previous.time != downTime) return false
        held.remove(id)
        return previous.consumed
    }

    private fun remember(id: Press, press: Held) {
        if (held.size >= MAX_HELD_PRESSES) held.remove(held.keys.first())
        held[id] = press
    }

    private companion object {
        const val ACTION_DOWN = 0
        const val ACTION_UP = 1
        const val MAX_HELD_PRESSES = 32
    }
}
