package com.alothmany.wa.system.shizuku

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import com.alothmany.wa.BuildConfig
import com.alothmany.wa.core.logging.AppLogger
import com.alothmany.wa.system.integration.CapabilityStatus
import com.alothmany.wa.system.integration.ShizukuSnapshot
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ShizukuController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val logger: AppLogger,
) {
    companion object {
        private const val REQUEST_CODE = 2333
        private const val USER_SERVICE_VERSION = 2
        private const val USER_SERVICE_TAG = "wa_alothmany_turbo_core"
    }

    private val _state = MutableStateFlow(ShizukuSnapshot())
    val state: StateFlow<ShizukuSnapshot> = _state.asStateFlow()

    @Volatile private var remote: ITurboUserService? = null
    @Volatile private var binding = false

    private val userServiceArgs by lazy {
        Shizuku.UserServiceArgs(ComponentName(context, TurboUserService::class.java))
            .processNameSuffix("turbo_core")
            .tag(USER_SERVICE_TAG)
            .version(USER_SERVICE_VERSION)
            .daemon(false)
            .debuggable(BuildConfig.DEBUG)
    }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            remote = ITurboUserService.Stub.asInterface(service)
            binding = false
            val privilegedUid = runCatching { remote?.uid() }.getOrNull()
            _state.value = _state.value.copy(
                status = CapabilityStatus.READY,
                privilegedServiceConnected = true,
                privilegedUid = privilegedUid,
                error = null,
            )
            logger.success("SHIZUKU", "Privileged UserService connected (uid=$privilegedUid)")
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            remote = null
            binding = false
            _state.value = _state.value.copy(privilegedServiceConnected = false, privilegedUid = null)
            logger.warning("SHIZUKU", "Privileged UserService disconnected")
        }
    }

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        logger.success("SHIZUKU", "Binder received")
        refresh()
    }

    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        remote = null
        binding = false
        _state.value = ShizukuSnapshot(status = CapabilityStatus.OFFLINE)
        logger.warning("SHIZUKU", "Binder disconnected")
    }

    private val permissionListener = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
        if (requestCode == REQUEST_CODE) {
            if (grantResult == PackageManager.PERMISSION_GRANTED) {
                logger.success("SHIZUKU", "Permission granted")
            } else {
                logger.warning("SHIZUKU", "Permission denied")
            }
            refresh()
        }
    }

    init {
        Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
        Shizuku.addBinderDeadListener(binderDeadListener)
        Shizuku.addRequestPermissionResultListener(permissionListener)
        refresh()
    }

    fun refresh() {
        val alive = runCatching { Shizuku.pingBinder() }.getOrDefault(false)
        if (!alive) {
            remote = null
            binding = false
            _state.value = ShizukuSnapshot(status = CapabilityStatus.OFFLINE)
            return
        }

        val permission = runCatching {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        }.getOrDefault(false)

        val version = runCatching { Shizuku.getVersion() }.getOrNull()
        val uid = runCatching { Shizuku.getUid() }.getOrNull()

        val nextStatus = when {
            !permission -> CapabilityStatus.NEEDS_PERMISSION
            remote != null -> CapabilityStatus.READY
            else -> CapabilityStatus.LIMITED
        }

        _state.value = _state.value.copy(
            status = nextStatus,
            binderAlive = true,
            permissionGranted = permission,
            serverVersion = version,
            serverUid = uid,
            error = null,
        )

        if (permission) bindPrivilegedServiceIfNeeded()
    }

    fun requestPermission() {
        if (!runCatching { Shizuku.pingBinder() }.getOrDefault(false)) {
            logger.warning("SHIZUKU", "Cannot request permission: service is offline")
            refresh()
            return
        }
        if (runCatching { Shizuku.checkSelfPermission() }.getOrNull() == PackageManager.PERMISSION_GRANTED) {
            refresh()
            return
        }
        runCatching { Shizuku.requestPermission(REQUEST_CODE) }
            .onFailure {
                logger.error("SHIZUKU", "Permission request failed: ${it.message.orEmpty()}")
                _state.value = _state.value.copy(status = CapabilityStatus.ERROR, error = it.message)
            }
    }

    private fun bindPrivilegedServiceIfNeeded() {
        if (remote != null || binding) return
        if (runCatching { Shizuku.isPreV11() }.getOrDefault(true)) {
            _state.value = _state.value.copy(
                status = CapabilityStatus.UNAVAILABLE,
                error = "Shizuku API v11+ is required",
            )
            return
        }
        binding = true
        runCatching { Shizuku.bindUserService(userServiceArgs, serviceConnection) }
            .onFailure {
                binding = false
                logger.error("SHIZUKU", "UserService bind failed: ${it.message.orEmpty()}")
                _state.value = _state.value.copy(
                    status = CapabilityStatus.ERROR,
                    privilegedServiceConnected = false,
                    error = it.message,
                )
            }
    }

    suspend fun exec(command: String): String? = withContext(Dispatchers.IO) {
        val service = remote ?: return@withContext null
        runCatching { service.exec(command) }
            .onFailure {
                logger.error("SHIZUKU", "Privileged command failed: ${it.message.orEmpty()}")
            }
            .getOrNull()
    }
}
