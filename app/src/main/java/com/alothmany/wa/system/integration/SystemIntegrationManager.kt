package com.alothmany.wa.system.integration

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import com.alothmany.wa.core.logging.AppLogger
import com.alothmany.wa.data.local.dao.SourceDao
import com.alothmany.wa.data.local.entity.WhatsAppSourceEntity
import com.alothmany.wa.system.accessibility.AccessibilityController
import com.alothmany.wa.system.accessibility.AccessibilityRuntime
import com.alothmany.wa.system.overlay.OverlayController
import com.alothmany.wa.system.overlay.OverlayRuntime
import com.alothmany.wa.system.shizuku.ShizukuController
import com.alothmany.wa.system.whatsapp.WhatsAppSourceDetector
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SystemIntegrationManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val shizuku: ShizukuController,
    private val accessibility: AccessibilityController,
    private val overlay: OverlayController,
    private val sourceDetector: WhatsAppSourceDetector,
    private val sourceDao: SourceDao,
    private val logger: AppLogger,
) {
    companion object {
        private const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _state = MutableStateFlow(SystemIntegrationState())
    val state: StateFlow<SystemIntegrationState> = _state.asStateFlow()

    private val initialized = AtomicBoolean(false)
    private var probeJob: Job? = null
    private var lastShizuku: CapabilityStatus? = null
    private var lastAccessibilityEnabled: Boolean? = null
    private var lastOverlayGranted: Boolean? = null
    private var lastSourceSignature: String? = null

    fun initialize() {
        if (!initialized.compareAndSet(false, true)) return

        scope.launch {
            shizuku.state.collectLatest { snapshot ->
                _state.update { it.copy(shizuku = snapshot) }
                if (snapshot.status != lastShizuku) {
                    logger.info("CAPABILITY", "Shizuku status: ${snapshot.status}")
                    lastShizuku = snapshot.status
                }
                if (snapshot.privilegedServiceConnected) probeSources()
            }
        }

        scope.launch {
            AccessibilityRuntime.state.collectLatest { runtime ->
                _state.update {
                    it.copy(accessibility = runtime.copy(enabled = accessibility.isEnabled()))
                }
            }
        }

        scope.launch {
            OverlayRuntime.running.collectLatest { running ->
                _state.update {
                    it.copy(
                        overlayPermissionGranted = overlay.permissionGranted(),
                        overlayRunning = running,
                    )
                }
            }
        }

        refresh()
    }

    fun refresh() {
        shizuku.refresh()
        val accessibilityEnabled = accessibility.isEnabled()
        val overlayGranted = overlay.permissionGranted()
        val accessibilityRuntime = AccessibilityRuntime.state.value

        _state.update {
            it.copy(
                shizuku = shizuku.state.value,
                accessibility = accessibilityRuntime.copy(enabled = accessibilityEnabled),
                overlayPermissionGranted = overlayGranted,
                overlayRunning = OverlayRuntime.running.value,
            )
        }

        if (lastAccessibilityEnabled != accessibilityEnabled) {
            logger.info("CAPABILITY", "Accessibility enabled: $accessibilityEnabled")
            lastAccessibilityEnabled = accessibilityEnabled
        }
        if (lastOverlayGranted != overlayGranted) {
            logger.info("CAPABILITY", "Overlay permission: $overlayGranted")
            lastOverlayGranted = overlayGranted
        }

        probeSources()
    }

    fun configureShizuku() {
        if (shizuku.state.value.binderAlive) {
            shizuku.requestPermission()
        } else {
            val launch = context.packageManager.getLaunchIntentForPackage(SHIZUKU_PACKAGE)
            if (launch != null) {
                context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                logger.info("SHIZUKU", "Opened Shizuku manager")
            } else {
                logger.warning("SHIZUKU", "Shizuku manager is not installed")
                runCatching {
                    context.startActivity(
                        Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.parse("package:$SHIZUKU_PACKAGE"),
                        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
            }
        }
    }

    fun configureAccessibility() {
        accessibility.openSettings()
        logger.info("ACCESSIBILITY", "Opened Accessibility settings")
    }

    fun toggleOverlay() {
        if (!overlay.permissionGranted()) {
            overlay.openPermissionSettings()
            logger.info("OVERLAY", "Opened overlay permission settings")
            return
        }
        if (OverlayRuntime.running.value) {
            overlay.stop()
            logger.info("OVERLAY", "Floating controller stopped")
        } else {
            overlay.start()
            logger.info("OVERLAY", "Floating controller started")
        }
    }

    fun probeSources() {
        probeJob?.cancel()
        probeJob = scope.launch {
            _state.update { it.copy(probing = true) }
            try {
                val detected = sourceDetector.detect()
                val entities = detected.map { source ->
                    WhatsAppSourceEntity(
                        id = source.id,
                        packageName = source.packageName,
                        userId = source.userId,
                        profileType = source.profileType,
                        displayName = source.displayName,
                        status = "AVAILABLE",
                        lastCheckedAt = System.currentTimeMillis(),
                    )
                }
                sourceDao.replaceAll(entities)
                val signature = detected.joinToString("|") { it.id }
                if (signature != lastSourceSignature) {
                    logger.success("SOURCE", "Detected ${detected.size} WhatsApp source(s)")
                    lastSourceSignature = signature
                }
                _state.update {
                    it.copy(
                        sources = detected,
                        lastProbeAt = System.currentTimeMillis(),
                        probing = false,
                    )
                }
            } catch (t: Throwable) {
                logger.error("SOURCE", "Source probe failed: ${t.message.orEmpty()}")
                _state.update { it.copy(probing = false) }
            }
        }
    }
}
