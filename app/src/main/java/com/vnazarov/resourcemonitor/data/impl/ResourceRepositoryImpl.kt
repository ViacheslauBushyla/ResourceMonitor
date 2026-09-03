package com.vnazarov.resourcemonitor.data.impl

import com.vnazarov.resourcemonitor.data.managers.CpuManager
import com.vnazarov.resourcemonitor.data.managers.RamManager
import com.vnazarov.resourcemonitor.domain.repository.ResourceRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

class ResourceRepositoryImpl(
    cpuManager: CpuManager,
    ramManager: RamManager,
    resScope: CoroutineScope
): ResourceRepository {

    private val cpuLoadPercFlow = MutableStateFlow(0)
    private val cpuFreqMHzFlow = MutableStateFlow(0)
    private val ramUsageFlow = MutableStateFlow(0)

    init {
        resScope.launch {
            while (true) {
                cpuLoadPercFlow.emit(cpuManager.getLoad())
                cpuFreqMHzFlow.emit(cpuManager.getFreq())
                ramUsageFlow.emit(ramManager.getMemoryUsage())

                delay(1.seconds)
            }
        }
    }

    override fun getCpuLoadPerc(): Flow<Int> {
        return cpuLoadPercFlow.asStateFlow()
    }

    override fun getCpuFreqMHz(): Flow<Int> {
        return cpuFreqMHzFlow.asStateFlow()
    }

    override fun getRamUsage(): Flow<Int> {
        return ramUsageFlow.asStateFlow()
    }
}