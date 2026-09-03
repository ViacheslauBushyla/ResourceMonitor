package com.vnazarov.resourcemonitor.app.di

import com.vnazarov.resourcemonitor.domain.usecase.GetCpuFreqMHzUseCase
import com.vnazarov.resourcemonitor.domain.usecase.GetCpuLoadPercUseCase
import com.vnazarov.resourcemonitor.domain.usecase.GetRamUsageUseCase
import org.koin.dsl.module

val domainModule = module {
    single { GetCpuLoadPercUseCase(get()) }
    single { GetCpuFreqMHzUseCase(get()) }
    single { GetRamUsageUseCase(get()) }
}