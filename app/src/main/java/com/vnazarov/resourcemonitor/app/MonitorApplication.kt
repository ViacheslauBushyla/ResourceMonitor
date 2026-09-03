package com.vnazarov.resourcemonitor.app

import android.app.Application
import com.vnazarov.resourcemonitor.app.di.dataModule
import com.vnazarov.resourcemonitor.app.di.domainModule
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.dsl.koinApplication

class MonitorApplication: Application() {

    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@MonitorApplication)
            androidLogger()
            modules(dataModule, domainModule)
        }
    }
}