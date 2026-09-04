package com.vnazarov.resourcemonitor.presentation

import android.Manifest
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.vnazarov.resourcemonitor.presentation.service.MonitorService
import com.vnazarov.resourcemonitor.presentation.ui.screen.StartScreen
import com.vnazarov.resourcemonitor.presentation.ui.theme.ResourceMonitorTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        enableEdgeToEdge()
        setContent {
            ResourceMonitorTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    StartScreen(
                        paddingValues = innerPadding,
                        modifier = Modifier,
                        onRegisterChange = {
                            if (!isServiceRunning(MonitorService::class.java)) {
                                checkPermission()
                            } else {
                                unregisterService()
                            }
                        }
                    )
                }
            }
        }
    }

    private fun checkPermission() {
        if (Settings.canDrawOverlays(this@MainActivity)) {
            checkNotificationPermission()
        } else {
            requestOverlayPermission()
        }
    }

    private fun checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasPermission = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

            if (hasPermission) {
                registerService()
            } else {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    NOTIFICATION_REQUEST_CODE
                )

                registerService()
            }
        } else {
            registerService()
        }
    }

    private fun requestOverlayPermission() {
        startActivity(
            Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                "package:${this@MainActivity.packageName}".toUri()
            )
        )
    }

    private fun isServiceRunning(serviceClass: Class<*>): Boolean {
        val manager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager

        @Suppress("DEPRECATION")
        return manager.getRunningServices(Int.MAX_VALUE)
            .any { serviceInfo ->
                serviceInfo.service.className == serviceClass.name
            }
    }

    private fun registerService() {
        val intent = Intent(this@MainActivity, MonitorService::class.java)
        ContextCompat.startForegroundService(this@MainActivity, intent)
    }

    private fun unregisterService() {
        val intent = Intent(this@MainActivity, MonitorService::class.java)
        stopService(intent)
    }

    companion object {
        private const val NOTIFICATION_REQUEST_CODE = 1001
    }
}