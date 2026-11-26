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
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.util.concurrent.Executors

class MainActivity : Activity() {
    private lateinit var store: BindingStore
    private lateinit var catalog: AppCatalog
    private lateinit var status: TextView
    private val details = mutableMapOf<Slot, TextView>()
    private val handler = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private var learningDialog: AlertDialog? = null
    private var pickerDialog: AlertDialog? = null
    private var pickerPending = false
    private val expireLearning = Runnable {
        RemoteMapperAccessibilityService.connected?.cancelLearning()
        learningDialog?.dismiss()
        if (!isFinishing && !isDestroyed) {
            AlertDialog.Builder(this).setTitle("Tuş algılanamadı")
                .setMessage("15 saniye içinde uygun bir tuş alınamadı. Hizmetin açık olduğunu kontrol edin. TV box bu tuşu Android'e iletmiyor olabilir. Yön, OK, geri, ses ve güç tuşları korunur.")
                .setPositiveButton("Tamam", null).show()
        }
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
            if (resources.configuration.screenWidthDp > 900) dp(900) else -1, -2, Gravity.TOP or Gravity.CENTER_HORIZONTAL))
        scroll.addView(frame)
        setContentView(scroll)
        scroll.setOnApplyWindowInsetsListener { view, insets ->
            @Suppress("DEPRECATION")
            val bars = if (android.os.Build.VERSION.SDK_INT >= 30) {
                val bounds = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                intArrayOf(bounds.left, bounds.top, bounds.right, bounds.bottom)
            } else intArrayOf(insets.systemWindowInsetLeft, insets.systemWindowInsetTop,
                insets.systemWindowInsetRight, insets.systemWindowInsetBottom)
            view.setPadding(bars[0], bars[1], bars[2], bars[3])
            insets
        }
        content.addView(text("TV Kumanda", 30f, true))
        content.addView(text("Üç tuş. Senin uygulamaların.", 17f).apply { setTextColor(Color.parseColor("#ACEDB9")) })
        status = text("Hizmet kontrol ediliyor…", 15f).apply { setPadding(0, dp(12), 0, dp(6)) }
        content.addView(status)
        content.addView(button("1. Kumanda hizmetini aç") { showServiceHelp() })
        content.addView(text("2. Tuşu öğret   →   3. Uygulama seç\nDeğişiklikler otomatik kaydedilir.", 14f).apply {
            setPadding(0, dp(14), 0, dp(6))
        })
        for (slot in Slot.entries) {
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(14), dp(10), dp(14), dp(10))
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#1C2820")); cornerRadius = dp(14).toFloat()
                }
            }
            content.addView(card, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
            card.addView(text(slot.label, 21f, true))
            details[slot] = text("", 14f).also {
                it.setPadding(0, dp(3), 0, dp(8)); card.addView(it)
            }
            val buttons = listOf(
                button("Tuşu öğret") { learn(slot) },
                button("Uygulama seç") { chooseApp(slot) },
                button("Dene") { testTarget(slot) },
                button("Sıfırla") { confirmClear(slot) }
            )
            val perRow = if (resources.configuration.screenWidthDp >= 600) 4 else 2
            buttons.chunked(perRow).forEach { group ->
                val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
                group.forEachIndexed { index, item ->
                    row.addView(item, LinearLayout.LayoutParams(0, dp(48), 1f).apply {
                        if (index > 0) marginStart = dp(8)
                        topMargin = dp(5)
                    })
                }
                card.addView(row)
            }
        }
        content.addView(button("Kurulum ve uyumluluk yardımı") { showHelp() }.apply {
            layoutParams = LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(16) }
        })
        content.addView(text("Çevrimdışı • Reklamsız • Root gerektirmez", 13f).apply { setPadding(0, dp(12), 0, 0) })
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
        for (slot in Slot.entries) {
            val binding = store.read(slot)
            val keyText = if (binding.key != null) "Tuş öğrenildi" else "Tuş henüz öğretilmedi"
            val appText = binding.target?.let { catalog.label(it) ?: "Uygulama kurulu değil / açılamıyor" } ?: "Uygulama seçilmedi"
            details[slot]?.text = "$keyText  →  $appText"
        }
    }

    private fun showServiceHelp() {
        AlertDialog.Builder(this).setTitle("Kumanda hizmetini etkinleştir")
            .setMessage("Açılacak Android ayarlarında TV Kumanda hizmetini bulup açın, sonra buraya dönün.\n\nBu hizmet, seçtiğiniz tuşları yakalayıp uygulama açmak için gereklidir. Ekran metinlerini okumaz ve internete veri göndermez.\n\nAndroid izin vermiyorsa yardım ekranındaki ‘Kısıtlanmış ayarlar’ açıklamasına bakın.")
            .setPositiveButton("Ayarlara git") { _, _ ->
                try { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
                catch (_: ActivityNotFoundException) {
                    try { startActivity(Intent(Settings.ACTION_SETTINGS)) }
                    catch (_: ActivityNotFoundException) { message("Android ayarları açılamadı. Cihaz ayarlarını elle açın.") }
                }
            }.setNegativeButton("Vazgeç", null).show()
    }

    private fun learn(slot: Slot) {
        val service = RemoteMapperAccessibilityService.connected
        if (service == null) { showServiceHelp(); return }
        learningDialog?.dismiss()
        val dialog = AlertDialog.Builder(this).setTitle("${slot.label} tuşunu öğret")
            .setMessage("Şimdi kumandadaki fiziksel ${slot.label} tuşuna bir kez basın.\n\n15 saniye içinde algılanan tuş kaydedilir. OK, geri, yön, ses ve güç tuşları atanmaz.")
            .setNegativeButton("Vazgeç", null).create()
        learningDialog = dialog
        dialog.setOnDismissListener {
            service.cancelLearning()
            handler.removeCallbacks(expireLearning)
            if (learningDialog === dialog) learningDialog = null
        }
        dialog.show()
        service.beginLearning(slot) { saved ->
            handler.post {
                if (!isDestroyed && !isFinishing && learningDialog === dialog) {
                    dialog.dismiss()
                    refresh()
                    message(if (saved) "${slot.label} tuşu öğrenildi." else "Bu tuş başka bir satıra atanmış. Önce o atamayı sıfırlayın.")
                }
            }
        }
        handler.postDelayed(expireLearning, 15_000)
    }

    private fun chooseApp(slot: Slot) {
        if (pickerPending) return
        pickerPending = true
        worker.execute {
            val apps = try { catalog.list() } catch (_: RuntimeException) { emptyList() }
            handler.post {
                pickerPending = false
                if (isDestroyed || isFinishing || !hasWindowFocus()) return@post
                if (apps.isEmpty()) { message("Başlatılabilir uygulama bulunamadı."); return@post }
                val labels = apps.map { "${it.label}\n${it.packageName}" }.toTypedArray()
                val selected = apps.indexOfFirst { it.packageName == store.read(slot).target }
                pickerDialog?.dismiss()
                pickerDialog = AlertDialog.Builder(this).setTitle("${slot.label} hangi uygulamayı açsın?")
                    .setSingleChoiceItems(labels, selected) { dialog, index ->
                        store.saveTarget(slot, apps[index].packageName)
                        refresh()
                        dialog.dismiss()
                    }.setNegativeButton("Vazgeç", null).show()
            }
        }
    }

    private fun testTarget(slot: Slot) {
        val target = store.read(slot).target
        if (target == null) message("Önce bir uygulama seçin.")
        else if (!catalog.launch(target)) message("Uygulama açılamadı. Başka bir uygulama seçin.")
    }
    private fun confirmClear(slot: Slot) {
        AlertDialog.Builder(this).setTitle("${slot.label} ataması sıfırlansın mı?")
            .setMessage("Bu satırdaki tuş ve uygulama seçimi silinir. Tuşun yönlendirilmesi durur.")
            .setPositiveButton("Sıfırla") { _, _ -> store.clear(slot); refresh() }
            .setNegativeButton("Vazgeç", null).show()
    }
    private fun showHelp() {
        AlertDialog.Builder(this).setTitle("Kurulum ve uyumluluk")
            .setMessage("1. Erişilebilirlik ayarlarında TV Kumanda hizmetini açın.\n2. Her satırda ‘Tuşu öğret’ deyip fiziksel tuşa basın.\n3. Açılacak uygulamayı seçin. ‘Dene’ ile açılışı kontrol edin.\n4. Ana ekrana dönün ve kumanda tuşunu deneyin.\n\nKısıtlanmış ayarlar: Bazı Android sürümlerinde APK ile kurulan uygulamanın erişilebilirliği engellenir. Uygulama bilgisi ekranındaki menüde varsa ‘Kısıtlanmış ayarlara izin ver’ seçeneğini açın. Menü adı cihaza göre değişebilir.\n\nTuş algılanmıyorsa: Firmware tuşu Android'e iletmiyor olabilir. Bazı cihazlar tuşu yakalasak bile kendi uygulamasını açabilir. Böyle cihazlarda tek başına APK yeterli olmayabilir.\n\nBaşka bir tuş eşleme hizmeti açıksa hizmetler çakışabilir. Onu geçici olarak kapatıp deneyin.\n\nYeniden başlatma sonrası: Hizmet durumunu kontrol edin. Üretici hizmeti durduruyorsa tekrar etkinleştirmek gerekebilir.\n\nSıfırla yalnızca ilgili atamayı kaldırır. Tüm yönlendirmeyi durdurmak için Android ayarlarında hizmeti kapatın.\n\nBu sürümün fiziksel kumanda ve TV box uyumluluğu cihazınızda doğrulanmalıdır.")
            .setPositiveButton("Tamam", null).show()
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
    private fun text(value: String, size: Float, bold: Boolean = false) = TextView(this).apply {
        text = value; textSize = size; setTextColor(Color.parseColor("#E8F0EA"))
        if (bold) setTypeface(typeface, Typeface.BOLD)
    }
    private fun button(label: String, action: () -> Unit) = Button(this).apply {
        text = label; textSize = 14f; isAllCaps = false
        setPadding(dp(8), 0, dp(8), 0)
        minHeight = dp(48); minimumHeight = dp(48)
        background = getDrawable(R.drawable.button_background)
        @Suppress("DEPRECATION")
        setTextColor(resources.getColorStateList(R.color.button_text))
        isFocusable = true
        setOnClickListener { action() }
        layoutParams = LinearLayout.LayoutParams(-1, dp(48))
    }
    private fun message(value: String) { Toast.makeText(this, value, Toast.LENGTH_LONG).show() }
    private fun dp(value: Int) = (value * resources.displayMetrics.density + 0.5f).toInt()
}
