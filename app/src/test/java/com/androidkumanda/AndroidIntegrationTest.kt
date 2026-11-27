package com.androidkumanda

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.ResolveInfo
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
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
    @Test fun `arbitrary mappings survive a new store instance and removing one preserves the rest`() {
        val store = BindingStore(app)
        val first = KeyBinding("one", KeySignature(200, 500), "tv.example.one", emptySet())
        val second = KeyBinding("two", KeySignature(201, 501), "tv.example.two", emptySet())
        assertEquals(SaveBindingResult.SAVED, store.save(first))
        assertEquals(SaveBindingResult.SAVED, store.save(second))

        val restored = BindingStore(app)
        assertEquals(listOf(first, second), restored.bindings())
        assertTrue(restored.remove("one"))
        assertFalse(restored.remove("one"))
        assertEquals(listOf(second), store.bindings())
    }

    @Test fun `legacy slot values migrate once without changing key or target`() {
        app.getSharedPreferences("bindings", Context.MODE_PRIVATE).edit()
            .putInt("NETFLIX.code", 200).putInt("NETFLIX.scan", 500)
            .putString("NETFLIX.target", "tv.example.one")
            .putInt("DISNEY.code", 201).putInt("DISNEY.scan", 501)
            .putString("DISNEY.target", "tv.example.two")
            .putInt("PRIME.code", 202).putInt("PRIME.scan", 502)
            .putString("PRIME.target", "tv.example.three")
            .commit()

        val migrated = BindingStore(app).bindings()
        assertEquals(3, migrated.size)
        assertEquals(KeySignature(200, 500), migrated.first { it.targetPackage == "tv.example.one" }.key)
        assertEquals(KeySignature(202, 502), migrated.first { it.targetPackage == "tv.example.three" }.key)
        assertEquals(migrated, BindingStore(app).bindings())
    }

    @Test fun `duplicate mapping keys are rejected without damaging existing mappings`() {
        val store = BindingStore(app)
        val first = KeyBinding("one", KeySignature(200, 500), "tv.example.one", emptySet())
        val duplicate = KeyBinding("two", KeySignature(200, 500), "tv.example.two", emptySet())
        assertEquals(SaveBindingResult.SAVED, store.save(first))
        assertEquals(SaveBindingResult.DUPLICATE_KEY, store.save(duplicate))
        assertEquals(listOf(first), store.bindings())
    }

    @Test fun `excluded package names persist with their mapping`() {
        val store = BindingStore(app)
        val binding = KeyBinding(
            "one",
            KeySignature(200, 500),
            "tv.example.one",
            setOf("tv.example.video", "tv.example.settings")
        )
        assertEquals(SaveBindingResult.SAVED, store.save(binding))
        assertEquals(binding, BindingStore(app).bindings().single())
    }

    @Test fun `reserved keys cannot be saved and corrupt legacy signatures are ignored`() {
        val store = BindingStore(app)
        assertEquals(
            SaveBindingResult.INVALID_KEY,
            store.save(KeyBinding("reserved", KeySignature(23, 10), "tv.example.one", emptySet()))
        )
        store.preferences.edit().putInt("NETFLIX.code", 23).putInt("NETFLIX.scan", 10).commit()
        assertTrue(BindingStore(app).bindings().isEmpty())
    }

    @Test fun `clear all removes app mappings and keeps migration complete`() {
        val store = BindingStore(app)
        assertEquals(
            SaveBindingResult.SAVED,
            store.save(KeyBinding("one", KeySignature(200, 500), "tv.example.one", emptySet()))
        )
        store.clearAll()
        assertTrue(store.bindings().isEmpty())
        assertTrue(BindingStore(app).bindings().isEmpty())
    }

    @Test fun `volume keys are disabled by default and persist only as a distinct pair`() {
        val store = BindingStore(app)
        assertEquals(VolumeKeyBinding(false, null, null), store.volumeKeyBinding())
        val raiseOnly = VolumeKeyBinding(false, KeySignature(24, 240), null)
        assertTrue(store.saveVolumeKeyBinding(raiseOnly))
        assertEquals(raiseOnly, BindingStore(app).volumeKeyBinding())
        assertFalse(store.saveVolumeKeyBinding(raiseOnly.copy(enabled = true)))
        val configured = VolumeKeyBinding(
            true,
            KeySignature(24, 240),
            KeySignature(25, 250)
        )
        assertTrue(store.saveVolumeKeyBinding(configured))
        assertEquals(configured, BindingStore(app).volumeKeyBinding())
        assertFalse(store.saveVolumeKeyBinding(configured.copy(lowerKey = configured.raiseKey)))
    }

    @Test fun `volume keys cannot collide with app mappings`() {
        val store = BindingStore(app)
        val appKey = KeyBinding("one", KeySignature(200, 500), "tv.example.one", emptySet())
        assertEquals(SaveBindingResult.SAVED, store.save(appKey))
        assertTrue(store.saveVolumeKeyBinding(VolumeKeyBinding(true, KeySignature(24, 240), KeySignature(25, 250))))
        assertEquals(SaveBindingResult.INVALID_KEY,
            store.save(KeyBinding("two", KeySignature(24, 240), "tv.example.two", emptySet())))
    }

    @Test fun `volume controller uses explicit media stream and reports fixed policy`() {
        val platform = FakeVolumePlatform(fixed = true)
        assertEquals(
            VolumeAdjustmentResult.FIXED_POLICY,
            AndroidVolumeController(platform).adjust(VolumeDirection.RAISE)
        )
        assertTrue(platform.adjustments.isEmpty())
    }

    @Test fun `volume controller distinguishes an index change from a no-op`() {
        val changing = FakeVolumePlatform()
        assertEquals(
            VolumeAdjustmentResult.INDEX_CHANGED,
            AndroidVolumeController(changing).adjust(VolumeDirection.RAISE)
        )
        assertEquals(listOf(VolumeDirection.RAISE), changing.adjustments)

        val unchanged = FakeVolumePlatform(changeIndex = false)
        assertEquals(
            VolumeAdjustmentResult.NO_INDEX_CHANGE,
            AndroidVolumeController(unchanged).adjust(VolumeDirection.LOWER)
        )
        assertEquals(listOf(VolumeDirection.LOWER), unchanged.adjustments)
    }

    @Test fun `connected output inventory is available on API 23 and newer`() {
        assertNotNull(AndroidVolumeController(app).connectedOutputTypes())
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
    @Test fun `settings screen exposes arbitrary mappings and Android media volume controls`() {
        Robolectric.buildActivity(MainActivity::class.java).use { controller ->
            val activity = controller.setup().get()
            val buttons = descend(activity.window.decorView).filterIsInstance<Button>()
            val labels = buttons.map { it.text.toString() }
            assertTrue(labels.contains("Yeni tuş eşlemesi"))
            assertTrue(labels.contains("Android medya sesini aç"))
            assertTrue(labels.contains("Ses açma tuşunu öğret"))
            assertTrue(labels.contains("Ses kısma tuşunu öğret"))
            assertTrue(buttons.all { it.isFocusable && it.isEnabled })
            val hero = descend(activity.window.decorView).filterIsInstance<ImageView>()
                .single { it.tag == "decorative-brand-hero" }
            assertFalse(hero.isFocusable)
            assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_NO, hero.importantForAccessibility)
            val copy = descend(activity.window.decorView).filterIsInstance<TextView>()
                .joinToString(" ") { it.text.toString() }
            assertTrue(copy.contains("Android medya sesi"))
            assertTrue(copy.contains("aktif rota olduğu anlamına gelmez"))
        }
    }
    private fun descend(view: View): List<View> = listOf(view) +
        if (view is ViewGroup) (0 until view.childCount).flatMap { descend(view.getChildAt(it)) } else emptyList()
}

private class FakeVolumePlatform(
    private val fixed: Boolean = false,
    private val changeIndex: Boolean = true
) : VolumePlatform {
    var index = 8
    val adjustments = mutableListOf<VolumeDirection>()

    override fun isVolumeFixed() = fixed
    override fun musicVolumeIndex() = index
    override fun adjustMusic(direction: VolumeDirection) {
        adjustments += direction
        if (changeIndex) index += if (direction == VolumeDirection.RAISE) 1 else -1
    }
}
