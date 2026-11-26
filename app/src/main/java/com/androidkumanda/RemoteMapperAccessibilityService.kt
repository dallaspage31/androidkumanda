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
    }
    private lateinit var store: BindingStore
    private lateinit var catalog: AppCatalog
    private var bindings = emptyList<Binding>()
    private val router = KeyRouter()
    private var learningSlot: Slot? = null
    private var learningUntil = 0L
    private var learned: ((Boolean) -> Unit)? = null
    private val preferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        bindings = store.bindings()
    }

    override fun onServiceConnected() {
        store = BindingStore(this)
        catalog = AppCatalog(this)
        bindings = store.bindings()
        store.preferences.registerOnSharedPreferenceChangeListener(preferenceListener)
        serviceInfo = serviceInfo.apply {
            eventTypes = 0
            flags = flags or AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
        }
        connected = this
    }

    fun beginLearning(slot: Slot, callback: (Boolean) -> Unit) {
        learningSlot = slot
        learningUntil = SystemClock.uptimeMillis() + 15_000
        learned = callback
    }
    fun cancelLearning() {
        learningSlot = null
        learningUntil = 0
        learned = null
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        val key = KeySignature(event.keyCode, event.scanCode)
        if (!key.isLearnable()) return false
        if (SystemClock.uptimeMillis() >= learningUntil) cancelLearning()
        val slot = learningSlot
        val target = if (slot != null) "learning" else bindings.firstOrNull { it.key?.matches(key) == true }?.target
        return router.route(KeyStroke(key, event.action, event.repeatCount, event.deviceId, event.downTime, event.isCanceled), target) {
            if (slot != null) {
                val saved = store.saveKey(slot, key)
                val callback = learned
                cancelLearning()
                callback?.invoke(saved)
                true
            } else {
                val started = target != null && catalog.launch(target)
                if (!started) Toast.makeText(this, "Uygulama açılamadı. Atamayı kontrol edin.", Toast.LENGTH_SHORT).show()
                started
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() { cancelLearning() }
    override fun onUnbind(intent: Intent?): Boolean {
        disconnect()
        return super.onUnbind(intent)
    }
    override fun onDestroy() {
        disconnect()
        super.onDestroy()
    }
    private fun disconnect() {
        cancelLearning()
        if (connected === this) connected = null
        if (::store.isInitialized) store.preferences.unregisterOnSharedPreferenceChangeListener(preferenceListener)
    }
}
