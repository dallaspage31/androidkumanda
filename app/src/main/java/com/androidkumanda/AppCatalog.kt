package com.androidkumanda

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import java.text.Collator

data class LaunchableApp(val packageName: String, val label: String)

class AppCatalog(private val context: Context) {
    private val pm = context.packageManager
    @Suppress("DEPRECATION")
    fun list(): List<LaunchableApp> {
        val apps = linkedMapOf<String, LaunchableApp>()
        for (category in listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)) {
            val intent = Intent(Intent.ACTION_MAIN).addCategory(category)
            for (result in pm.queryIntentActivities(intent, 0)) {
                val activity = result.activityInfo ?: continue
                if (!activity.exported || !activity.enabled || !activity.applicationInfo.enabled) continue
                if (activity.packageName == context.packageName) continue
                if (!apps.containsKey(activity.packageName)) {
                    apps[activity.packageName] = LaunchableApp(activity.packageName, result.loadLabel(pm).toString())
                }
            }
        }
        val collator = Collator.getInstance()
        return apps.values.sortedWith { a, b -> collator.compare(a.label, b.label) }
    }
    fun intentFor(target: String): Intent? =
        (pm.getLeanbackLaunchIntentForPackage(target) ?: pm.getLaunchIntentForPackage(target))
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)

    fun launch(target: String): Boolean {
        return try {
            val intent = intentFor(target) ?: return false
            context.startActivity(intent)
            true
        } catch (_: ActivityNotFoundException) { false }
          catch (_: SecurityException) { false }
    }
    @Suppress("DEPRECATION")
    fun label(target: String): String? = try {
        if (intentFor(target) == null) null
        else pm.getApplicationLabel(pm.getApplicationInfo(target, 0)).toString()
    } catch (_: android.content.pm.PackageManager.NameNotFoundException) { null }
}
