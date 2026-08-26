package com.alothmany.wa.system.whatsapp

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import com.alothmany.wa.core.model.WhatsAppSourceType
import com.alothmany.wa.system.integration.DetectedWhatsAppSource
import com.alothmany.wa.system.shizuku.ShizukuController
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WhatsAppSourceDetector @Inject constructor(
    @ApplicationContext private val context: Context,
    private val shizuku: ShizukuController,
) {
    companion object {
        const val WHATSAPP = "com.whatsapp"
        const val BUSINESS = "com.whatsapp.w4b"
        private const val PER_USER_RANGE = 100000
    }

    suspend fun detect(): List<DetectedWhatsAppSource> {
        val result = linkedMapOf<String, DetectedWhatsAppSource>()
        val currentUserId = Process.myUid() / PER_USER_RANGE

        detectLocal(WHATSAPP, currentUserId, WhatsAppSourceType.MAIN)?.let { result[it.id] = it }
        detectLocal(BUSINESS, currentUserId, WhatsAppSourceType.BUSINESS)?.let { result[it.id] = it }

        val shizukuState = shizuku.state.value
        if (shizukuState.privilegedServiceConnected) {
            val usersOutput = shizuku.exec("pm list users")
            parseUsers(usersOutput).forEach { user ->
                if (user.id == currentUserId) return@forEach
                val packagesOutput = shizuku.exec("pm list packages --user ${user.id} com.whatsapp").orEmpty()
                val packages = packagesOutput.lineSequence()
                    .map { it.removePrefix("package:").trim() }
                    .filter { it == WHATSAPP || it == BUSINESS }
                    .toSet()

                packages.forEach { packageName ->
                    val type = sourceTypeForProfile(user.name, packageName)
                    val source = DetectedWhatsAppSource(
                        id = "$packageName:${user.id}",
                        packageName = packageName,
                        userId = user.id,
                        profileType = profileLabel(type),
                        displayName = buildDisplayName(packageName, type),
                        sourceType = type,
                        versionName = null,
                        launchable = false,
                        privilegedDetection = true,
                    )
                    result[source.id] = source
                }
            }
        }

        return result.values.sortedWith(compareBy({ it.userId }, { it.packageName }))
    }

    private fun detectLocal(
        packageName: String,
        userId: Int,
        type: WhatsAppSourceType,
    ): DetectedWhatsAppSource? {
        val info = packageInfo(packageName) ?: return null
        return DetectedWhatsAppSource(
            id = "$packageName:$userId",
            packageName = packageName,
            userId = userId,
            profileType = profileLabel(type),
            displayName = buildDisplayName(packageName, type),
            sourceType = type,
            versionName = info.versionName,
            launchable = context.packageManager.getLaunchIntentForPackage(packageName) != null,
            privilegedDetection = false,
        )
    }

    private fun packageInfo(packageName: String): PackageInfo? = runCatching {
        if (Build.VERSION.SDK_INT >= 33) {
            context.packageManager.getPackageInfo(
                packageName,
                PackageManager.PackageInfoFlags.of(0),
            )
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(packageName, 0)
        }
    }.getOrNull()

    private data class AndroidUser(val id: Int, val name: String)

    private fun parseUsers(output: String?): List<AndroidUser> {
        if (output.isNullOrBlank()) return emptyList()
        val regex = Regex("UserInfo\\{(\\d+):([^:}]*)")
        return regex.findAll(output)
            .mapNotNull { match ->
                val id = match.groupValues[1].toIntOrNull() ?: return@mapNotNull null
                AndroidUser(id, match.groupValues[2].trim())
            }
            .distinctBy { it.id }
            .toList()
    }

    private fun sourceTypeForProfile(name: String, packageName: String): WhatsAppSourceType {
        val lower = name.lowercase()
        return when {
            "secure" in lower || "knox" in lower || "آمن" in name || "امن" in name -> WhatsAppSourceType.SECURE
            "work" in lower || "managed" in lower || "عمل" in name -> WhatsAppSourceType.WORK
            else -> WhatsAppSourceType.DUAL
        }
    }

    private fun profileLabel(type: WhatsAppSourceType): String = when (type) {
        WhatsAppSourceType.MAIN -> "MAIN"
        WhatsAppSourceType.BUSINESS -> "BUSINESS"
        WhatsAppSourceType.DUAL -> "DUAL"
        WhatsAppSourceType.WORK -> "WORK"
        WhatsAppSourceType.SECURE -> "SECURE"
    }

    private fun buildDisplayName(packageName: String, type: WhatsAppSourceType): String {
        val base = if (packageName == BUSINESS) "WhatsApp Business" else "WhatsApp"
        val suffix = when (type) {
            WhatsAppSourceType.MAIN -> "Main"
            WhatsAppSourceType.BUSINESS -> "Business"
            WhatsAppSourceType.DUAL -> "Dual"
            WhatsAppSourceType.WORK -> "Work Profile"
            WhatsAppSourceType.SECURE -> "Secure Folder"
        }
        return "$base • $suffix"
    }
}
