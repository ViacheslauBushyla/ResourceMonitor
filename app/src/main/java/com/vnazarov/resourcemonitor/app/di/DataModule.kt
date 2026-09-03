package com.vnazarov.resourcemonitor.app.di

import com.vnazarov.resourcemonitor.data.impl.ResourceRepositoryImpl
import com.vnazarov.resourcemonitor.data.managers.CpuManager
import com.vnazarov.resourcemonitor.data.managers.RamManager
import com.vnazarov.resourcemonitor.domain.repository.ResourceRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.dsl.bind
import org.koin.dsl.module
import org.koin.plugin.module.dsl.single

val dataModule = module {
    single<CoroutineScope> {
        CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }

    single { CpuManager(get()) }
    single { RamManager(get()) }

    single<ResourceRepositoryImpl>() bind ResourceRepository::class
}