package com.alothmany.wa.feature.sync.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.alothmany.wa.R
import com.alothmany.wa.feature.sync.engine.SmartSyncEngine
import com.alothmany.wa.feature.sync.model.SyncEngineStatus
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class SmartSyncService : Service() {
    companion object {
        const val ACTION_START = "com.alothmany.wa.sync.START"
        const val ACTION_PAUSE = "com.alothmany.wa.sync.PAUSE"
        const val ACTION_RESUME = "com.alothmany.wa.sync.RESUME"
        const val ACTION_STOP = "com.alothmany.wa.sync.STOP"

        private const val CHANNEL_ID = "wa_alothmany_smart_sync"
        private const val NOTIFICATION_ID = 3030

        fun intent(context: Context, action: String) = Intent(context, SmartSyncService::class.java).setAction(action)
    }

    @Inject lateinit var engine: SmartSyncEngine

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        createChannel()
        scope.launch {
            engine.state.collectLatest { state ->
                if (state.status !in setOf(SyncEngineStatus.IDLE, SyncEngineStatus.COMPLETED, SyncEngineStatus.STOPPED, SyncEngineStatus.ERROR)) {
                    val text = state.currentGroupName?.let { "$it • ${state.discoveredCount}" }
                        ?: state.message
                        ?: getString(R.string.sync_engine_active)
                    val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    manager.notify(NOTIFICATION_ID, buildNotification(text))
                }
                if (state.status in setOf(SyncEngineStatus.COMPLETED, SyncEngineStatus.STOPPED, SyncEngineStatus.ERROR)) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PAUSE -> engine.pause()
            ACTION_RESUME -> engine.resume()
            ACTION_STOP -> engine.stop()
            else -> {
                startForeground(NOTIFICATION_ID, buildNotification(getString(R.string.sync_engine_starting)))
                engine.start()
            }
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun createChannel() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.sync_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            )
        )
    }

    private fun buildNotification(text: String) = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(android.R.drawable.stat_notify_sync)
        .setContentTitle(getString(R.string.smart_sync_title))
        .setContentText(text)
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setCategory(NotificationCompat.CATEGORY_SERVICE)
        .build()
}
