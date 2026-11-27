package com.androidkumanda

import android.content.Context
import android.view.KeyEvent

data class KeyBinding(
    val id: String,
    val key: KeySignature,
    val targetPackage: String,
    val excludedPackages: Set<String> = emptySet()
)

enum class SaveBindingResult {
    SAVED,
    DUPLICATE_KEY,
    INVALID_KEY,
    INVALID_TARGET,
    INVALID_ID,
    INVALID_EXCLUSIONS
}

data class VolumeKeyBinding(
    val enabled: Boolean,
    val raiseKey: KeySignature?,
    val lowerKey: KeySignature?
)

class BindingStore(context: Context) {
    val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    init {
        migrateLegacyBindings()
    }

    fun bindings(): List<KeyBinding> = preferences.all.keys.asSequence()
        .filter { it.startsWith("$MAPPING_PREFIX.") && it.endsWith(".code") }
        .map { it.removePrefix("$MAPPING_PREFIX.").removeSuffix(".code") }
        .distinct()
        .mapNotNull(::readBinding)
        .sortedBy(KeyBinding::id)
        .toList()

    fun save(binding: KeyBinding): SaveBindingResult {
        if (!binding.id.matches(ID_PATTERN)) return SaveBindingResult.INVALID_ID
        if (!binding.key.isLearnable()) return SaveBindingResult.INVALID_KEY
        if (binding.targetPackage.isBlank()) return SaveBindingResult.INVALID_TARGET
        if (binding.excludedPackages.any(String::isBlank)) return SaveBindingResult.INVALID_EXCLUSIONS
        if (bindings().any { it.id != binding.id && it.key.matches(binding.key) } ||
            volumeKeyBinding().let { config ->
                config.raiseKey?.matches(binding.key) == true ||
                    config.lowerKey?.matches(binding.key) == true
            }
        ) return SaveBindingResult.DUPLICATE_KEY

        val prefix = mappingPrefix(binding.id)
        preferences.edit()
            .putInt("$prefix.code", binding.key.code)
            .putInt("$prefix.scan", binding.key.scan)
            .putString("$prefix.target", binding.targetPackage)
            .putStringSet("$prefix.excluded", binding.excludedPackages.toSet())
            .apply()
        return SaveBindingResult.SAVED
    }

    fun remove(id: String): Boolean {
        if (!id.matches(ID_PATTERN) || bindings().none { it.id == id }) return false
        val prefix = "${mappingPrefix(id)}."
        preferences.edit().also { editor ->
            preferences.all.keys.filter { it.startsWith(prefix) }.forEach(editor::remove)
        }.apply()
        return true
    }

    fun clearAll() {
        preferences.edit().also { editor ->
            preferences.all.keys.filter {
                it.startsWith("$MAPPING_PREFIX.") || it in LEGACY_KEYS
            }.forEach(editor::remove)
            editor.putInt(SCHEMA_VERSION_KEY, CURRENT_SCHEMA_VERSION)
        }.apply()
    }

    fun volumeKeyBinding(): VolumeKeyBinding = VolumeKeyBinding(
        enabled = preferences.getBoolean(VOLUME_ENABLED_KEY, false),
        raiseKey = readVolumeKey(VOLUME_RAISE_PREFIX),
        lowerKey = readVolumeKey(VOLUME_LOWER_PREFIX)
    )

    fun saveVolumeKeyBinding(binding: VolumeKeyBinding): Boolean {
        val raiseKey = binding.raiseKey
        val lowerKey = binding.lowerKey
        if (binding.enabled && (raiseKey == null || lowerKey == null)) return false
        if (raiseKey != null && raiseKey.code != KeyEvent.KEYCODE_VOLUME_UP) return false
        if (lowerKey != null && lowerKey.code != KeyEvent.KEYCODE_VOLUME_DOWN) return false
        if (raiseKey != null && lowerKey != null && raiseKey.matches(lowerKey)) return false
        if (bindings().any { mapping ->
                raiseKey?.let(mapping.key::matches) == true ||
                    lowerKey?.let(mapping.key::matches) == true
            }
        ) return false

        preferences.edit().also { editor ->
            editor.putBoolean(VOLUME_ENABLED_KEY, binding.enabled)
            writeVolumeKey(editor, VOLUME_RAISE_PREFIX, raiseKey)
            writeVolumeKey(editor, VOLUME_LOWER_PREFIX, lowerKey)
        }.apply()
        return true
    }

    private fun readBinding(id: String): KeyBinding? {
        if (!id.matches(ID_PATTERN)) return null
        val prefix = mappingPrefix(id)
        if (!preferences.contains("$prefix.code")) return null
        val key = KeySignature(
            preferences.getInt("$prefix.code", 0),
            preferences.getInt("$prefix.scan", 0)
        ).takeIf(KeySignature::isLearnable) ?: return null
        val target = preferences.getString("$prefix.target", null)?.takeIf(String::isNotBlank)
            ?: return null
        val exclusions = preferences.getStringSet("$prefix.excluded", emptySet()).orEmpty().toSet()
        return KeyBinding(id, key, target, exclusions)
    }

    private fun readVolumeKey(prefix: String): KeySignature? {
        if (!preferences.contains("$prefix.code")) return null
        val key = KeySignature(
            preferences.getInt("$prefix.code", 0),
            preferences.getInt("$prefix.scan", 0)
        )
        return key.takeIf {
            it.code == KeyEvent.KEYCODE_VOLUME_UP || it.code == KeyEvent.KEYCODE_VOLUME_DOWN
        }
    }

    private fun migrateLegacyBindings() {
        if (preferences.getInt(SCHEMA_VERSION_KEY, 0) >= CURRENT_SCHEMA_VERSION) return

        val editor = preferences.edit()
        val migratedKeys = mutableListOf<KeySignature>()
        LEGACY_SLOTS.forEach { slot ->
            val codeKey = "$slot.code"
            if (!preferences.contains(codeKey)) return@forEach
            val key = KeySignature(
                preferences.getInt(codeKey, 0),
                preferences.getInt("$slot.scan", 0)
            ).takeIf(KeySignature::isLearnable) ?: return@forEach
            val target = preferences.getString("$slot.target", null)?.takeIf(String::isNotBlank)
                ?: return@forEach
            if (migratedKeys.any(key::matches)) return@forEach

            val id = "legacy-${slot.lowercase()}"
            val prefix = mappingPrefix(id)
            editor.putInt("$prefix.code", key.code)
                .putInt("$prefix.scan", key.scan)
                .putString("$prefix.target", target)
                .putStringSet("$prefix.excluded", emptySet())
            migratedKeys += key
        }
        editor.putInt(SCHEMA_VERSION_KEY, CURRENT_SCHEMA_VERSION).apply()
    }

    private fun writeVolumeKey(
        editor: android.content.SharedPreferences.Editor,
        prefix: String,
        key: KeySignature?
    ) {
        if (key == null) {
            editor.remove("$prefix.code").remove("$prefix.scan")
        } else {
            editor.putInt("$prefix.code", key.code)
                .putInt("$prefix.scan", key.scan)
        }
    }

    private fun mappingPrefix(id: String) = "$MAPPING_PREFIX.$id"

    private companion object {
        const val PREFERENCES_NAME = "bindings"
        const val MAPPING_PREFIX = "v2"
        const val SCHEMA_VERSION_KEY = "schema.version"
        const val CURRENT_SCHEMA_VERSION = 2
        const val VOLUME_ENABLED_KEY = "volume.enabled"
        const val VOLUME_RAISE_PREFIX = "volume.raise"
        const val VOLUME_LOWER_PREFIX = "volume.lower"
        val ID_PATTERN = Regex("[A-Za-z0-9_-]+")
        val LEGACY_SLOTS = listOf("NETFLIX", "DISNEY", "PRIME")
        val LEGACY_KEYS = LEGACY_SLOTS.flatMap { slot ->
            listOf("$slot.code", "$slot.scan", "$slot.target")
        }.toSet()
    }
}
