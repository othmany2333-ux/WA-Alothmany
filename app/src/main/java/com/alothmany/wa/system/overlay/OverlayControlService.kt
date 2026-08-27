package com.alothmany.wa.system.overlay

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.alothmany.wa.MainActivity
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
import kotlin.math.abs

@AndroidEntryPoint
class OverlayControlService : Service() {
    companion object {
        private const val CHANNEL_ID = "wa_alothmany_overlay"
        private const val NOTIFICATION_ID = 2202
    }

    @Inject lateinit var smartSyncEngine: SmartSyncEngine

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mainHandler = Handler(Looper.getMainLooper())
    private lateinit var windowManager: WindowManager
    private var overlayView: View? = null
    private var bubbleView: TextView? = null
    private var pauseView: TextView? = null
    private lateinit var params: WindowManager.LayoutParams

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        showOverlay()
        OverlayRuntime.setRunning(true)

        scope.launch {
            smartSyncEngine.state.collectLatest { state ->
                mainHandler.post {
                    val active = state.running || state.status == SyncEngineStatus.PAUSED
                    bubbleView?.text = if (active) "⚡ SYNC ${state.discoveredCount}" else "⚡ WA"
                    pauseView?.text = if (state.status == SyncEngineStatus.PAUSED) "▶" else "⏸"
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scope.cancel()
        overlayView?.let { runCatching { windowManager.removeView(it) } }
        overlayView = null
        bubbleView = null
        pauseView = null
        OverlayRuntime.setRunning(false)
        super.onDestroy()
    }

    private fun showOverlay() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(12)
            y = dp(140)
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.START
        }

        val bubble = TextView(this).apply {
            text = "⚡ WA"
            textSize = 15f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(dp(14), dp(10), dp(14), dp(10))
            background = roundedBackground("#13232B", "#00D9E6", 18f)
        }
        bubbleView = bubble

        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            visibility = View.GONE
            setPadding(dp(6), dp(6), dp(6), dp(6))
            background = roundedBackground("#101820", "#314653", 16f)
        }

        val pause = actionButton("⏸") {
            val status = smartSyncEngine.state.value.status
            if (status == SyncEngineStatus.PAUSED) {
                smartSyncEngine.resume()
            } else if (smartSyncEngine.state.value.running) {
                smartSyncEngine.pause()
            } else {
                AutomationControlBus.togglePause()
            }
        }
        pauseView = pause

        val stop = actionButton("⏹") {
            val state = smartSyncEngine.state.value
            if (state.running || state.status == SyncEngineStatus.PAUSED) {
                smartSyncEngine.stop()
            } else {
                AutomationControlBus.requestStop()
            }
        }
        val close = actionButton("×") { stopSelf() }
        panel.addView(pause)
        panel.addView(stop)
        panel.addView(close)

        root.addView(bubble)
        root.addView(panel)

        var startX = 0
        var startY = 0
        var touchX = 0f
        var touchY = 0f
        var moved = false
        bubble.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = params.x
                    startY = params.y
                    touchX = event.rawX
                    touchY = event.rawY
                    moved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - touchX).toInt()
                    val dy = (event.rawY - touchY).toInt()
                    if (abs(dx) > dp(4) || abs(dy) > dp(4)) moved = true
                    params.x = startX + dx
                    params.y = startY + dy
                    runCatching { windowManager.updateViewLayout(root, params) }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) panel.visibility = if (panel.visibility == View.VISIBLE) View.GONE else View.VISIBLE
                    true
                }
                else -> false
            }
        }

        overlayView = root
        windowManager.addView(root, params)
    }

    private fun actionButton(label: String, action: (TextView) -> Unit): TextView =
        TextView(this).apply {
            text = label
            textSize = 18f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(dp(11), dp(7), dp(11), dp(7))
            setOnClickListener { action(this) }
        }

    private fun roundedBackground(fill: String, stroke: String, radiusDp: Float) =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(radiusDp.toInt()).toFloat()
            setColor(Color.parseColor(fill))
            setStroke(dp(1), Color.parseColor(stroke))
        }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun createNotificationChannel() {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.overlay_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            )
        )
    }

    private fun buildNotification() = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(R.mipmap.ic_launcher)
        .setContentTitle(getString(R.string.app_name))
        .setContentText(getString(R.string.overlay_notification_text))
        .setOngoing(true)
        .setContentIntent(
            PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        )
        .build()
}
