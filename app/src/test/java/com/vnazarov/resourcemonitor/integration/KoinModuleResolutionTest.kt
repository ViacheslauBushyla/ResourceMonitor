package com.vnazarov.resourcemonitor.integration

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.vnazarov.resourcemonitor.animations.hologram.HolographicRingsPlugin
import com.vnazarov.resourcemonitor.app.di.dataModule
import com.vnazarov.resourcemonitor.app.di.domainModule
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
import com.vnazarov.resourcemonitor.domain.usecase.GetCpuFreqMHzUseCase
import com.vnazarov.resourcemonitor.domain.usecase.GetCpuLoadPercUseCase
import com.vnazarov.resourcemonitor.domain.usecase.GetRamUsageUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.koin.dsl.koinApplication
import org.koin.dsl.module

@OptIn(ExperimentalCoroutinesApi::class)
class KoinModuleResolutionTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    @Test
    fun testKoinModules_resolveAllExpectedDependenciesWithoutCrashing() {
        val testFile = tempFolder.newFile("koin_test_settings.preferences_pb")
        val testDataStore = PreferenceDataStoreFactory.create(
            scope = testScope,
            produceFile = { testFile }
        )

        val testIntegrationModule = module {
            single<CoroutineScope> {
                CoroutineScope(SupervisorJob() + Dispatchers.IO)
            }
            single<HudSettingsRepository> {
                DataStoreHudSettingsRepository(testDataStore)
            }
            single { CpuFreqCollector(context = null, cpuBaseDir = tempFolder.root) }
            single { RamMeminfoCollector(context = null) }
            single { NetworkTrafficCollector(context = null, rxBytesProvider = { 0L }, txBytesProvider = { 0L }) }
            single { StorageIoCollector(context = null, probeDirectory = tempFolder.root, storageDirectory = tempFolder.root) }
            single { ThermalSystemCollector(context = null) }
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

            val fakeContext = object : android.content.ContextWrapper(null) {
                override fun getApplicationContext(): android.content.Context = this
                override fun getSystemService(name: String): Any? = null
            }
            single { CpuManager(fakeContext) }
            single { RamManager(fakeContext) }
            single<ResourceRepository> { ResourceRepositoryImpl(get(), get(), get()) }
        }

        val koinApp = koinApplication {
            modules(testIntegrationModule, domainModule)
        }

        val koin = koinApp.koin

        // 1. Verify Core DI singletons
        val repo = koin.get<HudSettingsRepository>()
        assertNotNull("HudSettingsRepository must be resolved", repo)

        val realSource = koin.get<RealTelemetrySource>()
        assertNotNull("RealTelemetrySource must be resolved", realSource)

        val fakeSource = koin.get<FakeTelemetrySource>()
        assertNotNull("FakeTelemetrySource must be resolved", fakeSource)

        val telemetrySource = koin.get<TelemetrySource>()
        assertNotNull("TelemetrySource must be resolved", telemetrySource)

        val fusionEngine = koin.get<TelemetryFusionEngine>()
        assertNotNull("TelemetryFusionEngine must be resolved", fusionEngine)

        val plugin = koin.get<HolographicRingsPlugin>()
        assertNotNull("HolographicRingsPlugin must be resolved", plugin)

        val resourceRepo = koin.get<ResourceRepository>()
        assertNotNull("ResourceRepository must be resolved", resourceRepo)

        // 2. Verify Domain usecases
        val getCpuLoad = koin.get<GetCpuLoadPercUseCase>()
        assertNotNull("GetCpuLoadPercUseCase must be resolved", getCpuLoad)

        val getCpuFreq = koin.get<GetCpuFreqMHzUseCase>()
        assertNotNull("GetCpuFreqMHzUseCase must be resolved", getCpuFreq)

        val getRamUsage = koin.get<GetRamUsageUseCase>()
        assertNotNull("GetRamUsageUseCase must be resolved", getRamUsage)

        // 3. Verify TelemetryFusionEngine can dynamically switch sources
        assertEquals("REAL", fusionEngine.sourceMode)
        fusionEngine.switchSource("MOCK")
        assertEquals("MOCK", fusionEngine.sourceMode)
        assertTrue(fusionEngine.getTelemetrySource() is FakeTelemetrySource)

        fusionEngine.switchSource("REAL")
        assertEquals("REAL", fusionEngine.sourceMode)
        assertTrue(fusionEngine.getTelemetrySource() is RealTelemetrySource)

        koinApp.close()
    }
}
