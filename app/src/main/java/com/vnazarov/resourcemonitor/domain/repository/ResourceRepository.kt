package com.vnazarov.resourcemonitor.domain.repository

import kotlinx.coroutines.flow.Flow

interface ResourceRepository {

    fun getCpuLoadPerc(): Flow<Int>

    fun getCpuFreqMHz(): Flow<Int>

    fun getRamUsage(): Flow<Int>
}