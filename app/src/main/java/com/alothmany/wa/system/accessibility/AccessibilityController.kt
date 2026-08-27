package com.alothmany.wa.system.accessibility

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AccessibilityController @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun isEnabled(): Boolean {
        val manager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val viaManager = manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .any { info ->
                val serviceInfo = info.resolveInfo?.serviceInfo ?: return@any false
                serviceInfo.packageName == context.packageName &&
                    serviceInfo.name == WAAccessibilityService::class.java.name
            }
        if (viaManager) return true

        // Samsung/One UI can briefly return a stale AccessibilityManager list after
        // the user toggles a service. The secure setting is used only as a local
        // capability check and never to enable the service programmatically.
        val accessibilityEnabled = runCatching {
            Settings.Secure.getInt(
                context.contentResolver,
                Settings.Secure.ACCESSIBILITY_ENABLED,
                0,
            ) == 1
        }.getOrDefault(false)
        if (!accessibilityEnabled) return false

        val expected = ComponentName(context, WAAccessibilityService::class.java)
        val raw = runCatching {
            Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            )
        }.getOrNull().orEmpty()

        return raw.split(':')
            .mapNotNull(ComponentName::unflattenFromString)
            .any { component ->
                component.packageName == expected.packageName &&
                    component.className == expected.className
            }
    }

    fun openSettings() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}
