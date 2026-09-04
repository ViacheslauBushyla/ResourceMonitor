package com.vnazarov.resourcemonitor.app.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.vnazarov.resourcemonitor.animations.hologram.HolographicRingsPlugin
import com.vnazarov.resourcemonitor.core.config.DataStoreHudSettingsRepository
import com.vnazarov.resourcemonitor.core.config.HudSettingsRepository
import com.vnazarov.resourcemonitor.core.telemetry.api.TelemetrySource
import com.vnazarov.resourcemonitor.core.telemetry.fusion.TelemetryFusionEngine
import com.vnazarov.resourcemonitor.core.telemetry.mock.FakeTelemetrySource
import com.vnazarov.resourcemonitor.core.telemetry.system.RealTelemetrySource
import com.vnazarov.resourcemonitor.core.telemetry.system.collector.CpuFreqCollector
import com.vnazarov.resourcemonitor.core.telemetry.system.collector.NetworkTrafficCollector
import com.vnazarov.resourcemonitor.core.telemetry.system.collector.RamMeminfoCollector
import com.vnazarov.resourcemonitor.core.telemetry.system.collector.StorageIoCollector
import com.vnazarov.resourcemonitor.core.telemetry.system.collector.ThermalSystemCollector
import com.vnazarov.resourcemonitor.data.impl.ResourceRepositoryImpl
import com.vnazarov.resourcemonitor.data.managers.CpuManager
import com.vnazarov.resourcemonitor.data.managers.RamManager
import com.vnazarov.resourcemonitor.domain.repository.ResourceRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.bind
import org.koin.dsl.module

private val Context.hudDataStore: DataStore<Preferences> by preferencesDataStore(name = "hud_settings")

val dataModule = module {
    single<CoroutineScope> {
        CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }

    single { CpuManager(get()) }
    single { RamManager(get()) }

    single<ResourceRepository> { ResourceRepositoryImpl(get(), get(), get()) }

    single<HudSettingsRepository> {
        DataStoreHudSettingsRepository(androidContext().hudDataStore)
    }

    single { CpuFreqCollector(androidContext()) }
    single { RamMeminfoCollector(androidContext()) }
    single { NetworkTrafficCollector(androidContext()) }
    single { StorageIoCollector(androidContext()) }
    single { ThermalSystemCollector(androidContext()) }

    single {
        RealTelemetrySource(
            cpuCollector = get<CpuFreqCollector>(),
            ramCollector = get<RamMeminfoCollector>(),
            networkCollector = get<NetworkTrafficCollector>(),
            storageCollector = get<StorageIoCollector>(),
            thermalCollector = get<ThermalSystemCollector>()
        )
    }

    single { FakeTelemetrySource() }

    single<TelemetrySource> { get<RealTelemetrySource>() }

    single {
        TelemetryFusionEngine(
            telemetrySource = get<RealTelemetrySource>(),
            coroutineScope = get<CoroutineScope>(),
            realSource = get<RealTelemetrySource>(),
            fakeSource = get<FakeTelemetrySource>()
        )
    }

    single { HolographicRingsPlugin() }
}