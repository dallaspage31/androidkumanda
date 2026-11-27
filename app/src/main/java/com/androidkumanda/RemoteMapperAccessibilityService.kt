package com.androidkumanda

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.content.SharedPreferences
import android.os.SystemClock
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast

class RemoteMapperAccessibilityService : AccessibilityService() {
    companion object {
        var connected: RemoteMapperAccessibilityService? = null
            private set
        var lastVolumeResult: VolumeAdjustmentResult? = null
            private set
        const val LEARNING_TIMEOUT_MS = 15_000L
    }

    private lateinit var store: BindingStore
    private lateinit var catalog: AppCatalog
    private lateinit var volumeController: AndroidVolumeController
    private var bindings = emptyList<KeyBinding>()
    private var volumeBinding = VolumeKeyBinding(false, null, null)
    private var foregroundPackage: String? = null
    private val router = KeyRouter()
    private var learningUntil = 0L
    private var learningAllowsVolumeKeys = false
    private var learned: ((KeySignature?) -> Unit)? = null
    private val preferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        refreshBindings()
    }

    override fun onServiceConnected() {
        store = BindingStore(this)
        catalog = AppCatalog(this)
        volumeController = AndroidVolumeController(this)
        refreshBindings()
        store.preferences.registerOnSharedPreferenceChangeListener(preferenceListener)
        serviceInfo = serviceInfo.apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            flags = flags or AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
        }
        foregroundPackage = null
        connected = this
    }

    fun beginLearning(allowVolumeKeys: Boolean = false, callback: (KeySignature?) -> Unit) {
        cancelLearning()
        learningUntil = SystemClock.uptimeMillis() + LEARNING_TIMEOUT_MS
        learningAllowsVolumeKeys = allowVolumeKeys
        learned = callback
    }

    fun cancelLearning() {
        learningUntil = 0L
        learningAllowsVolumeKeys = false
        learned = null
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        val stroke = KeyStroke(
            KeySignature(event.keyCode, event.scanCode),
            event.action,
            event.repeatCount,
            event.deviceId,
            event.downTime,
            event.isCanceled
        )
        if (SystemClock.uptimeMillis() >= learningUntil) cancelLearning()

        val callback = learned
        if (callback != null) {
            val eligible = if (learningAllowsVolumeKeys) {
                event.keyCode == KeyEvent.KEYCODE_VOLUME_UP ||
                    event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN
            } else {
                stroke.key.isLearnable()
            }
            if (!eligible) return false
            return router.route(
                stroke,
                KeyBinding("learning", stroke.key, "learning"),
                foregroundPackage
            ) {
                val current = learned
                cancelLearning()
                current?.invoke(stroke.key)
                true
            }
        }

        val direction = if (volumeBinding.enabled) {
            when {
                volumeBinding.raiseKey?.matches(stroke.key) == true -> VolumeDirection.RAISE
                volumeBinding.lowerKey?.matches(stroke.key) == true -> VolumeDirection.LOWER
                else -> null
            }
        } else null
        if (event.keyCode == KeyEvent.KEYCODE_VOLUME_UP ||
            event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN
        ) {
            return router.routeVolume(stroke, direction) { requested ->
                volumeController.adjust(requested).also { lastVolumeResult = it }
            }
        }

        if (!stroke.key.isLearnable()) return false
        val binding = bindings.firstOrNull { it.key.matches(stroke.key) }
        return router.route(stroke, binding, foregroundPackage) { target ->
            val started = catalog.launch(target)
            if (!started) {
                Toast.makeText(this, "Uygulama açılamadı. Atamayı kontrol edin.", Toast.LENGTH_SHORT).show()
            }
            started
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            foregroundPackage = event.packageName?.toString()?.takeIf(String::isNotBlank)
        }
    }

    override fun onInterrupt() {
        cancelLearning()
    }

    override fun onUnbind(intent: Intent?): Boolean {
        disconnect()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        disconnect()
        super.onDestroy()
    }

    private fun refreshBindings() {
        if (!::store.isInitialized) return
        bindings = store.bindings()
        volumeBinding = store.volumeKeyBinding()
    }

    private fun disconnect() {
        cancelLearning()
        foregroundPackage = null
        if (connected === this) connected = null
        if (::store.isInitialized) {
            store.preferences.unregisterOnSharedPreferenceChangeListener(preferenceListener)
        }
    }

}
