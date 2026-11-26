package com.androidkumanda

import android.content.Context

enum class Slot(val label: String) { NETFLIX("Netflix"), DISNEY("Disney"), PRIME("Prime") }
data class Binding(val slot: Slot, val key: KeySignature?, val target: String?)

class BindingStore(context: Context) {
    val preferences = context.getSharedPreferences("bindings", Context.MODE_PRIVATE)
    fun read(slot: Slot): Binding {
        val prefix = slot.name
        val key = if (preferences.contains("$prefix.code")) {
            KeySignature(preferences.getInt("$prefix.code", 0), preferences.getInt("$prefix.scan", 0))
                .takeIf { it.isLearnable() }
        } else null
        return Binding(slot, key, preferences.getString("$prefix.target", null))
    }
    fun bindings(): List<Binding> = Slot.entries.map(::read)
    fun saveKey(slot: Slot, key: KeySignature): Boolean {
        if (!key.isLearnable() || bindings().any { it.slot != slot && it.key?.matches(key) == true }) return false
        preferences.edit().putInt("${slot.name}.code", key.code)
            .putInt("${slot.name}.scan", key.scan).apply()
        return true
    }
    fun saveTarget(slot: Slot, target: String) {
        preferences.edit().putString("${slot.name}.target", target).apply()
    }
    fun clear(slot: Slot) {
        preferences.edit().remove("${slot.name}.code").remove("${slot.name}.scan")
            .remove("${slot.name}.target").apply()
    }
}
