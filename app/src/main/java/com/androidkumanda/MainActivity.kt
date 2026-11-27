package com.androidkumanda

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.view.accessibility.AccessibilityManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.util.UUID
import java.util.concurrent.Executors

class MainActivity : Activity() {
    private data class MappingDraft(
        val id: String,
        val key: KeySignature? = null,
        val targetPackage: String? = null,
        val tested: Boolean = false
    )

    private lateinit var store: BindingStore
    private lateinit var catalog: AppCatalog
    private lateinit var status: TextView
    private lateinit var mappingRows: LinearLayout
    private lateinit var draftRows: LinearLayout
    private lateinit var volumeStatus: TextView
    private lateinit var volumeToggle: Button
    private val handler = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private var learningDialog: AlertDialog? = null
    private var learningCompletion: ((KeySignature?) -> Unit)? = null
    private var pickerDialog: AlertDialog? = null
    private var pickerPending = false
    private var mappingDraft: MappingDraft? = null

    private val expireLearning = Runnable {
        val dialog = learningDialog ?: return@Runnable
        val completion = learningCompletion
        learningCompletion = null
        dialog.dismiss()
        completion?.invoke(null)
        message("15 saniye içinde uygun bir tuş alınamadı. Cihaz bu tuşu Android'e iletmiyor olabilir.")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = BindingStore(this)
        catalog = AppCatalog(this)
        createScreen()
    }

    private fun createScreen() {
        val scroll = ScrollView(this).apply { isFillViewport = true; clipToPadding = false }
        val frame = FrameLayout(this)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(28), dp(22), dp(28), dp(28))
        }
        frame.addView(content, FrameLayout.LayoutParams(
            if (resources.configuration.screenWidthDp > 900) dp(900) else -1,
            -2,
            Gravity.TOP or Gravity.CENTER_HORIZONTAL
        ))
        scroll.addView(frame)
        setContentView(scroll)
        scroll.setOnApplyWindowInsetsListener { view, insets ->
            @Suppress("DEPRECATION")
            val bars = if (android.os.Build.VERSION.SDK_INT >= 30) {
                val bounds = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                intArrayOf(bounds.left, bounds.top, bounds.right, bounds.bottom)
            } else {
                intArrayOf(
                    insets.systemWindowInsetLeft,
                    insets.systemWindowInsetTop,
                    insets.systemWindowInsetRight,
                    insets.systemWindowInsetBottom
                )
            }
            view.setPadding(bars[0], bars[1], bars[2], bars[3])
            insets
        }

        content.addView(text("TV Kumanda", 30f, true))
        content.addView(text("Kumanda tuşları ve Android medya sesi", 17f).apply {
            setTextColor(Color.parseColor("#ACEDB9"))
        })
        content.addView(ImageView(this).apply {
            setImageResource(R.drawable.home_hero)
            contentDescription = null
            tag = "decorative-brand-hero"
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            isFocusable = false
            isFocusableInTouchMode = false
            adjustViewBounds = true
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply {
                topMargin = dp(12)
                bottomMargin = dp(4)
            }
        })
        status = text("Hizmet kontrol ediliyor…", 15f).apply {
            setPadding(0, dp(12), 0, dp(6))
        }
        content.addView(status)
        content.addView(button("Kumanda hizmetini aç") { showServiceHelp() })
        content.addView(text(
            "Bu uygulama yalnızca Android'in hizmete ilettiği tuşları yönlendirebilir. Firmware veya sistem tarafından tüketilen tuşlar değiştirilemeyebilir.",
            14f
        ).apply { setPadding(0, dp(8), 0, dp(4)) })

        content.addView(sectionTitle("Uygulama tuş eşlemeleri"))
        content.addView(text(
            "Yeni bir eşleme ekleyin, fiziksel tuşu öğretin, açılacak uygulamayı seçip test edin.",
            14f
        ))
        mappingRows = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(mappingRows)
        draftRows = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(draftRows)
        content.addView(button("Yeni tuş eşlemesi") { createMapping() }.apply {
            layoutParams = LinearLayout.LayoutParams(-1, dp(50)).apply { topMargin = dp(8) }
        })
        content.addView(button("Tüm eşlemeleri sıfırla") { confirmClearAll() }.apply {
            layoutParams = LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(6) }
        })

        content.addView(sectionTitle("Android medya sesi"))
        content.addView(text(
            "Bu özellik Android'in STREAM_MUSIC sesini ayarlar; TV veya soundbar kazancını değiştirme garantisi vermez. AUX, optik/SPDIF ve HDMI davranışı cihaza göre değişir. Bağlı çıkış türleri aktif rota olduğu anlamına gelmez.",
            14f
        ))
        volumeStatus = text("", 14f).apply { setPadding(0, dp(7), 0, dp(7)) }
        content.addView(volumeStatus)
        volumeToggle = button("Android medya sesini aç") { toggleVolumeMode() }
        content.addView(volumeToggle)
        val volumeButtons = listOf(
            button("Ses açma tuşunu öğret") { learnVolumeKey(VolumeDirection.RAISE) },
            button("Ses kısma tuşunu öğret") { learnVolumeKey(VolumeDirection.LOWER) }
        )
        addButtonRows(content, volumeButtons)
        content.addView(button("Bağlı çıkış türlerini göster") { showOutputDiagnostics() }.apply {
            layoutParams = LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(4) }
        })
        content.addView(text(
            "Başarıyı doğrulamak için oynatma sırasında soundbar kazancını sabit tutun; Android ses indeksi ve duyulan çıkışı ayrı ayrı test edin.",
            13f
        ).apply { setPadding(0, dp(8), 0, dp(4)) })

        content.addView(button("Kurulum ve uyumluluk yardımı") { showHelp() }.apply {
            layoutParams = LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(16) }
        })
        content.addView(text("Çevrimdışı • Reklamsız • Root gerektirmez", 13f)
            .apply { setPadding(0, dp(12), 0, 0) })
        refresh()
    }

    override fun onResume() {
        super.onResume()
        refresh()
        handler.postDelayed({ if (!isDestroyed) refresh() }, 600)
        handler.postDelayed({ if (!isDestroyed) refresh() }, 1800)
    }

    private fun refresh() {
        val manager = getSystemService(ACCESSIBILITY_SERVICE) as AccessibilityManager
        val enabled = manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .any { it.resolveInfo.serviceInfo.packageName == packageName }
        status.text = when {
            RemoteMapperAccessibilityService.connected != null -> "● Hizmet açık • Tuş atamaya hazır"
            enabled -> "● Hizmet etkin, bağlantı bekleniyor. Gerekirse kapatıp açın."
            else -> "○ Hizmet kapalı • Önce hizmeti açın"
        }
        renderMappings()
        renderDraft()
        refreshVolumeStatus(enabled)
    }

    private fun renderMappings() {
        mappingRows.removeAllViews()
        val bindings = store.bindings()
        if (bindings.isEmpty()) {
            mappingRows.addView(text("Henüz uygulama tuş eşlemesi yok.", 14f).apply {
                setPadding(0, dp(8), 0, dp(4))
            })
        }
        bindings.forEach { binding ->
            val card = card()
            val targetLabel = catalog.label(binding.targetPackage) ?: "Uygulama kurulu değil veya açılamıyor"
            card.addView(text(targetLabel, 19f, true))
            card.addView(text(
                if (binding.excludedPackages.isEmpty()) "Fiziksel tuş eşlemesi kaydedildi."
                else "${binding.excludedPackages.size} uygulamada yönlendirme devre dışı.",
                13f
            ).apply { setPadding(0, dp(3), 0, dp(5)) })
            addButtonRows(card, listOf(
                button("Uygulamayı değiştir") { chooseTarget(binding) },
                button("Dene") { testTarget(binding.targetPackage) },
                button("Uygulamaları hariç tut") { chooseExclusions(binding) },
                button("Sıfırla") { confirmRemove(binding) }
            ))
            mappingRows.addView(card, LinearLayout.LayoutParams(-1, -2).apply {
                topMargin = dp(8)
            })
        }
    }

    private fun renderDraft() {
        draftRows.removeAllViews()
        val draft = mappingDraft ?: return
        val card = card()
        card.addView(text("Yeni eşleme", 18f, true))
        card.addView(text(
            when {
                draft.key == null -> "Tuş bekleniyor."
                draft.targetPackage == null -> "Tuş öğrenildi. Şimdi bir uygulama seçin."
                draft.tested -> "Uygulama testi başarılı. Eşlemeyi kaydedin."
                else -> "Tuş ve uygulama seçildi. Kaydetmeden önce uygulamayı test edin."
            },
            14f
        ).apply { setPadding(0, dp(4), 0, dp(6)) })
        val actions = mutableListOf<Button>()
        if (draft.key == null) {
            actions += button("Tuşu öğret") { learnDraftKey() }
        }
        if (draft.key != null && draft.targetPackage == null) {
            actions += button("Uygulama seç") { chooseDraftTarget() }
        }
        if (draft.targetPackage != null) {
            actions += button("Uygulamayı değiştir") { chooseDraftTarget() }
            actions += button("Dene") { testDraftTarget() }
            actions += button("Kaydet") { saveDraft() }.apply { isEnabled = draft.tested }
        }
        actions += button("Vazgeç") { mappingDraft = null; refresh() }
        addButtonRows(card, actions)
        draftRows.addView(card, LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(8)
        })
    }

    private fun createMapping() {
        if (mappingDraft != null) {
            message("Önce açık eşlemeyi kaydedin veya vazgeçin.")
            return
        }
        mappingDraft = MappingDraft(UUID.randomUUID().toString())
        refresh()
        learnDraftKey()
    }

    private fun learnDraftKey() {
        startLearning(
            "Uygulama tuşunu öğret",
            "Kumandadaki eşlemek istediğiniz fiziksel tuşa basın. Android'e ulaşmayan veya sistem için ayrılmış tuşlar öğrenilemez."
        ) { key ->
            val draft = mappingDraft ?: return@startLearning
            if (key == null) {
                mappingDraft = null
                refresh()
                return@startLearning
            }
            val volume = store.volumeKeyBinding()
            val duplicate = store.bindings().any { it.key.matches(key) } ||
                volume.raiseKey?.matches(key) == true || volume.lowerKey?.matches(key) == true
            if (duplicate) {
                mappingDraft = null
                message("Bu tuş zaten başka bir işleve atanmış. Önce mevcut atamayı kaldırın.")
            } else {
                mappingDraft = draft.copy(key = key)
                chooseDraftTarget()
            }
            refresh()
        }
    }

    private fun chooseDraftTarget() {
        showAppPicker("Hangi uygulama açılsın?", null) { app ->
            mappingDraft = mappingDraft?.copy(targetPackage = app.packageName, tested = false)
            refresh()
        }
    }

    private fun testDraftTarget() {
        val target = mappingDraft?.targetPackage ?: return
        if (catalog.launch(target)) {
            mappingDraft = mappingDraft?.copy(tested = true)
            message("Uygulama açıldı. Geri dönüp eşlemeyi kaydedin; bu test tuşun Android'e ulaştığını kanıtlamaz.")
        } else {
            message("Uygulama açılamadı. Başka bir uygulama seçin.")
        }
        refresh()
    }

    private fun saveDraft() {
        val draft = mappingDraft ?: return
        val key = draft.key ?: return
        val target = draft.targetPackage ?: return
        if (!draft.tested) {
            message("Önce seçilen uygulamayı test edin.")
            return
        }
        when (store.save(KeyBinding(draft.id, key, target))) {
            SaveBindingResult.SAVED -> {
                mappingDraft = null
                refresh()
                message("Tuş eşlemesi kaydedildi.")
            }
            SaveBindingResult.DUPLICATE_KEY ->
                message("Bu tuş başka bir işleve atanmış. Önce mevcut atamayı kaldırın.")
            SaveBindingResult.INVALID_KEY ->
                message("Bu tuş güvenli eşleme listesinde değil.")
            SaveBindingResult.INVALID_TARGET ->
                message("Önce geçerli bir uygulama seçin.")
            SaveBindingResult.INVALID_ID, SaveBindingResult.INVALID_EXCLUSIONS ->
                message("Eşleme kaydedilemedi. Bilgileri yeniden seçin.")
        }
    }

    private fun chooseTarget(binding: KeyBinding) {
        showAppPicker("Hangi uygulama açılsın?", binding.targetPackage) { app ->
            val result = store.save(binding.copy(targetPackage = app.packageName))
            if (result == SaveBindingResult.SAVED) refresh()
            else message("Uygulama değişikliği kaydedilemedi: ${result.name}.")
        }
    }

    private fun testTarget(target: String) {
        if (!catalog.launch(target)) message("Uygulama açılamadı. Başka bir uygulama seçin.")
    }

    private fun chooseExclusions(binding: KeyBinding) {
        loadApps { apps ->
            val labels = apps.map { "${it.label}\n${it.packageName}" }.toTypedArray()
            val selected = apps.map { it.packageName in binding.excludedPackages }.toBooleanArray()
            val updated = binding.excludedPackages.toMutableSet()
            AlertDialog.Builder(this)
                .setTitle("Hangi uygulamalarda tuş yönlendirilmesin?")
                .setMultiChoiceItems(labels, selected) { _, index, checked ->
                    if (checked) updated += apps[index].packageName
                    else updated -= apps[index].packageName
                }
                .setPositiveButton("Kaydet") { _, _ ->
                    val result = store.save(binding.copy(excludedPackages = updated))
                    if (result == SaveBindingResult.SAVED) refresh()
                    else message("Hariç tutulan uygulamalar kaydedilemedi: ${result.name}.")
                }
                .setNegativeButton("Vazgeç", null)
                .show()
        }
    }

    private fun showAppPicker(
        title: String,
        selectedPackage: String?,
        onSelected: (LaunchableApp) -> Unit
    ) {
        loadApps { apps ->
            val labels = apps.map { "${it.label}\n${it.packageName}" }.toTypedArray()
            val selected = apps.indexOfFirst { it.packageName == selectedPackage }
            pickerDialog?.dismiss()
            pickerDialog = AlertDialog.Builder(this).setTitle(title)
                .setSingleChoiceItems(labels, selected) { dialog, index ->
                    dialog.dismiss()
                    onSelected(apps[index])
                }
                .setNegativeButton("Vazgeç", null)
                .show()
        }
    }

    private fun loadApps(onLoaded: (List<LaunchableApp>) -> Unit) {
        if (pickerPending) return
        pickerPending = true
        worker.execute {
            val apps = try {
                catalog.list()
            } catch (error: RuntimeException) {
                handler.post {
                    pickerPending = false
                    if (!isFinishing && !isDestroyed) {
                        message("Uygulama listesi alınamadı: ${error.message ?: error.javaClass.simpleName}")
                    }
                }
                return@execute
            }
            handler.post {
                pickerPending = false
                if (isDestroyed || isFinishing) return@post
                if (apps.isEmpty()) {
                    message("Başlatılabilir uygulama bulunamadı.")
                    return@post
                }
                onLoaded(apps)
            }
        }
    }

    private fun confirmRemove(binding: KeyBinding) {
        AlertDialog.Builder(this).setTitle("Bu eşleme sıfırlansın mı?")
            .setMessage("Tuş yönlendirmesi durdurulur; diğer eşlemeler etkilenmez.")
            .setPositiveButton("Sıfırla") { _, _ ->
                store.remove(binding.id)
                refresh()
            }
            .setNegativeButton("Vazgeç", null)
            .show()
    }

    private fun confirmClearAll() {
        if (store.bindings().isEmpty()) {
            message("Sıfırlanacak uygulama tuş eşlemesi yok.")
            return
        }
        AlertDialog.Builder(this).setTitle("Tüm eşlemeler sıfırlansın mı?")
            .setMessage("Uygulama tuş eşlemeleri silinir. Android ses ayarı ve erişilebilirlik hizmeti değişmez.")
            .setPositiveButton("Tümünü sıfırla") { _, _ -> store.clearAll(); refresh() }
            .setNegativeButton("Vazgeç", null)
            .show()
    }

    private fun learnVolumeKey(direction: VolumeDirection) {
        val expectedCode = if (direction == VolumeDirection.RAISE) 24 else 25
        val label = if (direction == VolumeDirection.RAISE) "ses açma" else "ses kısma"
        startLearning(
            "$label tuşunu öğret",
            "Şimdi fiziksel kumandadaki $label tuşuna basın. Yalnızca Android'in standart volume-up/down olayı kabul edilir.",
            allowVolumeKeys = true
        ) { key ->
            if (key == null) return@startLearning
            if (key.code != expectedCode) {
                message("Yanlış tuş algılandı. $label tuşunu yeniden öğretin.")
                return@startLearning
            }
            val current = store.volumeKeyBinding()
            val updated = if (direction == VolumeDirection.RAISE) {
                current.copy(raiseKey = key)
            } else {
                current.copy(lowerKey = key)
            }
            if (store.saveVolumeKeyBinding(updated)) {
                refresh()
                message("$label tuşu kaydedildi.")
            } else {
                message("Bu tuş başka bir işleve atanmış veya iki ses tuşu aynı.")
            }
        }
    }

    private fun toggleVolumeMode() {
        val current = store.volumeKeyBinding()
        val updated = current.copy(enabled = !current.enabled)
        if (!store.saveVolumeKeyBinding(updated)) {
            message("Önce ayrı ses açma ve ses kısma tuşlarını öğretin; eşlemeler çakışmamalı.")
            return
        }
        refresh()
    }

    private fun refreshVolumeStatus(serviceEnabled: Boolean) {
        val config = store.volumeKeyBinding()
        volumeToggle.text = if (config.enabled) {
            "Android medya sesini kapat"
        } else {
            "Android medya sesini aç"
        }
        val connected = RemoteMapperAccessibilityService.connected != null
        val controller = AndroidVolumeController(this)
        volumeStatus.text = when {
            !config.enabled -> "Kapalı. Etkinleştirmek için iki ses tuşunu öğretin."
            !serviceEnabled || !connected -> "Hizmet kullanılamıyor; ses tuşları Android'e bırakılır."
            config.raiseKey == null || config.lowerKey == null -> "Ses açma ve ses kısma tuşlarını ayrı ayrı öğretin."
            controller.isVolumeFixed() -> "Android bu cihazda sabit ses politikası bildiriyor; tuşlar Android'e bırakılır."
            RemoteMapperAccessibilityService.lastVolumeResult == VolumeAdjustmentResult.FIXED_POLICY ->
                "Android ses politikası sabit; son tuş olayı yönlendirilmedi."
            RemoteMapperAccessibilityService.lastVolumeResult == VolumeAdjustmentResult.NO_INDEX_CHANGE ->
                "Android medya ses indeksi değişmedi; duyulan çıkış doğrulanmadı."
            RemoteMapperAccessibilityService.lastVolumeResult == VolumeAdjustmentResult.INDEX_CHANGED ->
                "Son basışta Android medya ses indeksi değişti. Bu, soundbar sesinin değiştiğini kanıtlamaz."
            else -> "Hazır. Ses değişikliği yalnızca Android'e ulaşan tuşlarda denenebilir."
        }
    }

    private fun showOutputDiagnostics() {
        val types = AndroidVolumeController(this).connectedOutputTypes()
        val description = when {
            types == null -> "Bu Android sürümünde bağlı çıkış türleri kullanılamıyor."
            types.isEmpty() -> "Android bağlı ses çıkışı bildirmedi."
            else -> types.map(AndroidVolumeController::outputTypeName).sorted().joinToString("\n")
        }
        AlertDialog.Builder(this).setTitle("Bağlı çıkış türleri")
            .setMessage("$description\n\nBu liste bağlı cihaz türlerini gösterir; oynatılan sesin aktif rota olduğu anlamına gelmez. AUX, optik/SPDIF ve HDMI başarısı gerçek cihazda sabit soundbar kazancıyla test edilmelidir.")
            .setPositiveButton("Tamam", null)
            .show()
    }

    private fun startLearning(
        title: String,
        instructions: String,
        allowVolumeKeys: Boolean = false,
        onLearned: (KeySignature?) -> Unit
    ) {
        val service = RemoteMapperAccessibilityService.connected
        if (service == null) {
            showServiceHelp()
            return
        }
        learningDialog?.dismiss()
        learningCompletion = onLearned
        val dialog = AlertDialog.Builder(this).setTitle(title)
            .setMessage("$instructions\n\nÖğrenme 15 saniye sürer.")
            .setNegativeButton("Vazgeç", null)
            .create()
        learningDialog = dialog
        dialog.setOnDismissListener {
            service.cancelLearning()
            handler.removeCallbacks(expireLearning)
            if (learningDialog === dialog) {
                learningDialog = null
                learningCompletion = null
            }
        }
        dialog.show()
        service.beginLearning(allowVolumeKeys) { key ->
            handler.post {
                if (isDestroyed || isFinishing || learningDialog !== dialog) return@post
                val completion = learningCompletion
                learningCompletion = null
                dialog.dismiss()
                completion?.invoke(key)
            }
        }
        handler.postDelayed(expireLearning, 15_000)
    }

    private fun showServiceHelp() {
        AlertDialog.Builder(this).setTitle("Kumanda hizmetini etkinleştir")
            .setMessage("Android erişilebilirlik ayarlarında TV Kumanda hizmetini açın. Hizmet yalnızca Android'in ilettiği fiziksel tuşları işler; ekran metnini okumaz ve tuşları her cihazda yakalayacağı garanti edilemez. İstediğiniz zaman aynı ayarlardan kapatabilirsiniz.")
            .setPositiveButton("Ayarlara git") { _, _ ->
                try {
                    startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                } catch (_: ActivityNotFoundException) {
                    try {
                        startActivity(Intent(Settings.ACTION_SETTINGS))
                    } catch (_: ActivityNotFoundException) {
                        message("Android ayarları açılamadı. Cihaz ayarlarını elle açın.")
                    }
                }
            }
            .setNegativeButton("Vazgeç", null)
            .show()
    }

    private fun showHelp() {
        AlertDialog.Builder(this).setTitle("Kurulum ve uyumluluk")
            .setMessage("Uygulama tuşu için: hizmeti açın, eşleme ekleyin, tuşu öğretin, uygulamayı seçip test ederek kaydedin. Firmware'in veya sistemin Android'e iletmediği tuşlar eşlenemez.\n\nAndroid medya sesi için: ses açma/kısma tuşlarını ayrı ayrı öğretip özelliği açın. Özellik Android STREAM_MUSIC değerini hedefler; TV, soundbar gain'i veya HDMI-CEC kontrolü değildir. Sabit gain'de gerçek ses çıkışı değişmiyorsa bu cihaz/rota desteklenmiş sayılmaz.\n\nBazı cihazlarda Android, dışarıdan yüklenen uygulamalar için erişilebilirlik hizmetini kısıtlayabilir. Android'in uygulama bilgisi menüsündeki kısıtlanmış ayar seçeneği cihazdan cihaza değişir. Hizmet durmuşsa Android ayarlarından durumunu kontrol edin.\n\nBağlı çıkış türleri aktif medya rotasını kanıtlamaz. Bu sürümün gerçek kumanda, TV box ve soundbar uyumluluğu cihazınızda doğrulanmalıdır.")
            .setPositiveButton("Tamam", null)
            .show()
    }

    override fun onPause() {
        learningDialog?.dismiss()
        RemoteMapperAccessibilityService.connected?.cancelLearning()
        handler.removeCallbacksAndMessages(null)
        super.onPause()
    }

    override fun onDestroy() {
        pickerDialog?.dismiss()
        worker.shutdownNow()
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private fun sectionTitle(value: String) = text(value, 21f, true).apply {
        setPadding(0, dp(18), 0, dp(6))
    }

    private fun card() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(14), dp(10), dp(14), dp(10))
        background = GradientDrawable().apply {
            setColor(Color.parseColor("#1C2820"))
            cornerRadius = dp(14).toFloat()
        }
    }

    private fun addButtonRows(parent: LinearLayout, buttons: List<Button>) {
        val perRow = if (resources.configuration.screenWidthDp >= 720) 4 else 2
        buttons.chunked(perRow).forEach { group ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            group.forEachIndexed { index, item ->
                row.addView(item, LinearLayout.LayoutParams(0, dp(48), 1f).apply {
                    if (index > 0) marginStart = dp(8)
                    topMargin = dp(5)
                })
            }
            parent.addView(row)
        }
    }

    private fun text(value: String, size: Float, bold: Boolean = false) = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(Color.parseColor("#E8F0EA"))
        if (bold) setTypeface(typeface, Typeface.BOLD)
    }

    private fun button(label: String, action: () -> Unit) = Button(this).apply {
        text = label
        textSize = 14f
        isAllCaps = false
        setPadding(dp(8), 0, dp(8), 0)
        minHeight = dp(48)
        minimumHeight = dp(48)
        background = getDrawable(R.drawable.button_background)
        @Suppress("DEPRECATION")
        setTextColor(resources.getColorStateList(R.color.button_text))
        isFocusable = true
        setOnClickListener { action() }
        layoutParams = LinearLayout.LayoutParams(-1, dp(48))
    }

    private fun message(value: String) {
        Toast.makeText(this, value, Toast.LENGTH_LONG).show()
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density + 0.5f).toInt()
}
