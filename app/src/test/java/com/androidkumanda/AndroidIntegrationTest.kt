package com.androidkumanda

import android.app.Application
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.ResolveInfo
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class AndroidIntegrationTest {
    private lateinit var app: Application
    @Before fun clearPreferences() {
        app = RuntimeEnvironment.getApplication()
        BindingStore(app).preferences.edit().clear().commit()
    }
    @Test fun `bindings survive a new store instance and clear affects one slot only`() {
        val store = BindingStore(app)
        assertTrue(store.saveKey(Slot.NETFLIX, KeySignature(200, 500)))
        store.saveTarget(Slot.NETFLIX, "tv.example.one")
        assertTrue(store.saveKey(Slot.DISNEY, KeySignature(201, 501)))
        store.saveTarget(Slot.DISNEY, "tv.example.two")
        val restored = BindingStore(app)
        assertEquals("tv.example.one", restored.read(Slot.NETFLIX).target)
        assertEquals(KeySignature(200, 500), restored.read(Slot.NETFLIX).key)
        restored.clear(Slot.NETFLIX)
        assertNull(store.read(Slot.NETFLIX).key)
        assertNull(store.read(Slot.NETFLIX).target)
        assertEquals("tv.example.two", store.read(Slot.DISNEY).target)
    }
    @Test fun `duplicate assignment is rejected without damaging existing slots`() {
        val store = BindingStore(app)
        store.saveKey(Slot.NETFLIX, KeySignature(200, 500))
        store.saveKey(Slot.DISNEY, KeySignature(201, 501))
        assertFalse(store.saveKey(Slot.DISNEY, KeySignature(200, 500)))
        assertFalse(store.saveKey(Slot.PRIME, KeySignature(200, 0)))
        assertEquals(KeySignature(201, 501), store.read(Slot.DISNEY).key)
        assertNull(store.read(Slot.PRIME).key)
    }
    @Test fun `reserved keys cannot be saved and corrupt persisted keys are ignored`() {
        val store = BindingStore(app)
        assertFalse(store.saveKey(Slot.NETFLIX, KeySignature(23, 10)))
        store.preferences.edit().putInt("NETFLIX.code", 23).putInt("NETFLIX.scan", 10).commit()
        assertNull(store.read(Slot.NETFLIX).key)
    }
    @Test fun `missing target returns failure without sending an intent`() {
        assertFalse(AppCatalog(app).launch("nonexistent.app"))
        assertNull(shadowOf(app).nextStartedActivity)
    }
    @Test fun `TV entry point takes priority over phone entry point and catalog deduplicates`() {
        registerApp("tv.example.player", "Player", true)
        val catalog = AppCatalog(app)
        assertTrue(catalog.launch("tv.example.player"))
        val launched = shadowOf(app).nextStartedActivity
        assertEquals("tv.example.player.TvActivity", launched.component?.className)
        assertTrue(launched.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
        assertEquals(1, catalog.list().count { it.packageName == "tv.example.player" })
    }
    @Test fun `normal launcher works when no TV entry point exists`() {
        registerApp("tv.example.regular", "Regular", false)
        assertTrue(AppCatalog(app).launch("tv.example.regular"))
        assertEquals("tv.example.regular.MainActivity", shadowOf(app).nextStartedActivity.component?.className)
    }
    @Suppress("DEPRECATION")
    private fun registerApp(pkg: String, label: String, tv: Boolean) {
        val application = ApplicationInfo().apply { packageName = pkg; enabled = true; nonLocalizedLabel = label }
        val normal = ActivityInfo().apply {
            packageName = pkg; name = "$pkg.MainActivity"; applicationInfo = application; enabled = true; exported = true
        }
        val television = ActivityInfo(normal).apply { name = "$pkg.TvActivity" }
        val pm = shadowOf(app.packageManager)
        pm.installPackage(PackageInfo().apply {
            packageName = pkg; applicationInfo = application
            activities = if (tv) arrayOf(normal, television) else arrayOf(normal)
        })
        for (filterPackage in listOf(null, pkg)) {
            pm.addResolveInfoForIntent(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(filterPackage),
                ResolveInfo().apply { activityInfo = normal })
            if (tv) pm.addResolveInfoForIntent(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER).setPackage(filterPackage),
                ResolveInfo().apply { activityInfo = television })
        }
    }
    @Test fun `settings screen builds and all three rows expose usable buttons`() {
        Robolectric.buildActivity(MainActivity::class.java).use { controller ->
            val activity = controller.setup().get()
            val buttons = descend(activity.window.decorView).filterIsInstance<Button>()
            assertEquals(3, buttons.count { it.text.toString() == "Tuşu öğret" })
            assertEquals(3, buttons.count { it.text.toString() == "Uygulama seç" })
            assertEquals(3, buttons.count { it.text.toString() == "Dene" })
            assertTrue(buttons.all { it.isFocusable && it.isEnabled })
            buttons.first { it.text.toString() == "Dene" }.performClick()
            assertNull(shadowOf(activity).nextStartedActivity)
        }
    }
    private fun descend(view: View): List<View> = listOf(view) +
        if (view is ViewGroup) (0 until view.childCount).flatMap { descend(view.getChildAt(it)) } else emptyList()
}
