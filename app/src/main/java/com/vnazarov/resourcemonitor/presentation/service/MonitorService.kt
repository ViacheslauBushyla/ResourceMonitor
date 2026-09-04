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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
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
import com.vnazarov.resourcemonitor.core.config.HudSettings
import com.vnazarov.resourcemonitor.core.config.HudSettingsRepository
import com.vnazarov.resourcemonitor.core.designsystem.cutout.CutoutGeometry
import com.vnazarov.resourcemonitor.core.designsystem.cutout.CutoutGeometryResolver
import com.vnazarov.resourcemonitor.core.telemetry.fusion.TelemetryFusionEngine
import com.vnazarov.resourcemonitor.presentation.ui.screen.MonitorOverlay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

class MonitorService : Service(), LifecycleOwner, SavedStateRegistryOwner {

    companion object {
        private const val CHANNEL_ID = "resource_monitor_channel"
        private const val NOTIFICATION_ID = 101
    }

    private val fusionEngine: TelemetryFusionEngine by inject()
    private val settingsRepository: HudSettingsRepository by inject()
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override val lifecycle: Lifecycle
        field = LifecycleRegistry(this)

    private val savedStateController = SavedStateRegistryController.create(this)
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry

    private lateinit var windowManager: WindowManager
    private lateinit var overlayView: ComposeView
    private var isOverlayAttached = false
    private var layoutParams: WindowManager.LayoutParams? = null
    private val geometryState = mutableStateOf<CutoutGeometry?>(null)
    private val haloRadiusState = mutableStateOf(52.dp)
    private val settingsState = mutableStateOf(HudSettings())

    override fun onCreate() {
        super.onCreate()

        savedStateController.performRestore(null)
        lifecycle.currentState = Lifecycle.State.CREATED
        createNotificationChannel()

        serviceScope.launch {
            settingsRepository.settingsFlow.collect { settings ->
                settingsState.value = settings
                haloRadiusState.value = (settings.haloDiameterDp / 2f).dp
                fusionEngine.switchSource(settings.telemetrySourceMode)
                fusionEngine.updatePollingIntervals(settings.activePollingMs, settings.idlePollingMs)

                if (settings.isOverlayEnabled) {
                    if (!isOverlayAttached && ::windowManager.isInitialized) {
                        showOverlay()
                    } else if (isOverlayAttached) {
                        updateOverlayHeight()
                    }
                } else {
                    if (isOverlayAttached) {
                        hideOverlay()
                    }
                }
            }
        }
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

        windowManager = getSystemService(WindowManager::class.java)

        serviceScope.launch {
            val settings = settingsRepository.getSettings()
            if (!settings.isOverlayEnabled) {
                settingsRepository.updateOverlayEnabled(true)
            } else if (!isOverlayAttached) {
                showOverlay()
            }
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
        if (isOverlayAttached) return
        if (!::windowManager.isInitialized) {
            windowManager = getSystemService(WindowManager::class.java)
        }

        val windowMetrics = windowManager.currentWindowMetrics
        val insets = windowMetrics.windowInsets
        val density = resources.displayMetrics.density
        val geometry = CutoutGeometryResolver.resolve(
            windowInsets = insets,
            displayWidthPx = windowMetrics.bounds.width(),
            density = density
        )

        geometryState.value = geometry

        val haloRadius = haloRadiusState.value.value
        val haloRadiusPx = haloRadius * density
        val bloomPaddingPx = 16f * density
        val requiredHeight = (geometry.centerYPx + haloRadiusPx + bloomPaddingPx).toInt()
        val overlayHeightPx = maxOf(geometry.statusBarHeightPx, requiredHeight)

        overlayView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@MonitorService)
            setViewTreeSavedStateRegistryOwner(this@MonitorService)

            setContent {
                MonitorOverlay(
                    cutoutGeometry = geometryState.value,
                    haloRadiusDp = haloRadiusState.value,
                    hudSettings = settingsState.value
                )
            }
        }

        lifecycle.currentState = Lifecycle.State.RESUMED

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            overlayHeightPx,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        }
        layoutParams = params

        overlayView.setOnApplyWindowInsetsListener { _, windowInsets ->
            val updatedDensity = resources.displayMetrics.density
            val updatedGeometry = CutoutGeometryResolver.resolve(
                windowInsets = windowInsets,
                displayWidthPx = windowManager.currentWindowMetrics.bounds.width(),
                density = updatedDensity
            )
            geometryState.value = updatedGeometry

            val currentHalo = haloRadiusState.value.value
            val updatedHaloRadiusPx = currentHalo * updatedDensity
            val updatedBloomPaddingPx = 16f * updatedDensity
            val updatedRequiredHeight = (updatedGeometry.centerYPx + updatedHaloRadiusPx + updatedBloomPaddingPx).toInt()
            val newHeight = maxOf(updatedGeometry.statusBarHeightPx, updatedRequiredHeight)

            if (params.height != newHeight) {
                params.height = newHeight
                try {
                    windowManager.updateViewLayout(overlayView, params)
                } catch (_: Exception) {}
            }
            windowInsets
        }

        try {
            windowManager.addView(overlayView, params)
            isOverlayAttached = true
            fusionEngine.start()
        } catch (_: Exception) {}
    }

    private fun hideOverlay() {
        if (!isOverlayAttached) return
        if (::overlayView.isInitialized && ::windowManager.isInitialized) {
            try {
                windowManager.removeView(overlayView)
            } catch (_: Exception) {}
        }
        isOverlayAttached = false
        fusionEngine.stop()
    }

    private fun updateOverlayHeight() {
        if (!isOverlayAttached || layoutParams == null) return
        val geometry = geometryState.value ?: return
        val density = resources.displayMetrics.density
        val currentHalo = haloRadiusState.value.value
        val haloRadiusPx = currentHalo * density
        val bloomPaddingPx = 16f * density
        val requiredHeight = (geometry.centerYPx + haloRadiusPx + bloomPaddingPx).toInt()
        val newHeight = maxOf(geometry.statusBarHeightPx, requiredHeight)

        val params = layoutParams ?: return
        if (params.height != newHeight) {
            params.height = newHeight
            try {
                windowManager.updateViewLayout(overlayView, params)
            } catch (_: Exception) {}
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        hideOverlay()
        lifecycle.currentState = Lifecycle.State.DESTROYED

        super.onDestroy()
    }

    override fun onBind(p0: Intent?) = null
}