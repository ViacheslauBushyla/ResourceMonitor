package com.vnazarov.resourcemonitor.presentation.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.ui.platform.ComposeView
import androidx.core.app.NotificationCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.vnazarov.resourcemonitor.core.telemetry.fusion.TelemetryFusionEngine
import com.vnazarov.resourcemonitor.presentation.ui.screen.MonitorOverlay
import org.koin.android.ext.android.inject

class MonitorService : Service(), LifecycleOwner, SavedStateRegistryOwner {

    companion object {
        private const val CHANNEL_ID = "resource_monitor_channel"
        private const val NOTIFICATION_ID = 101
    }

    private val fusionEngine: TelemetryFusionEngine by inject()

    override val lifecycle: Lifecycle
        field = LifecycleRegistry(this)

    private val savedStateController = SavedStateRegistryController.create(this)
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry

    private lateinit var windowManager: WindowManager
    private lateinit var overlayView: ComposeView

    override fun onCreate() {
        super.onCreate()

        savedStateController.performRestore(null)
        lifecycle.currentState = Lifecycle.State.CREATED
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                createNotification(),
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, createNotification())
        }

        if (lifecycle.currentState != Lifecycle.State.RESUMED) {
            windowManager = getSystemService(WindowManager::class.java)
            showOverlay()
        }

        return START_STICKY
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Resource Monitor")
            .setContentText("Resource Monitor is active")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Resource Monitor",
            NotificationManager.IMPORTANCE_LOW
        )

        val manager = getSystemService(NotificationManager::class.java)
        manager?.createNotificationChannel(channel)
    }

    private fun showOverlay() {
        overlayView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@MonitorService)
            setViewTreeSavedStateRegistryOwner(this@MonitorService)

            setContent {
                MonitorOverlay()
            }
        }

        lifecycle.currentState = Lifecycle.State.RESUMED

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        )

        params.gravity = Gravity.TOP
        windowManager.addView(overlayView, params)
        fusionEngine.start()
    }

    override fun onDestroy() {
        fusionEngine.stop()
        windowManager.removeView(overlayView)
        lifecycle.currentState = Lifecycle.State.DESTROYED

        super.onDestroy()
    }

    override fun onBind(p0: Intent?) = null
}