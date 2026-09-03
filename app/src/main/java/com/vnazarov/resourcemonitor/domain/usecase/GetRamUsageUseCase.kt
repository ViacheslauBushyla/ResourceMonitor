package com.vnazarov.resourcemonitor.domain.usecase

import com.vnazarov.resourcemonitor.domain.repository.ResourceRepository

class GetRamUsageUseCase(
    private val repository: ResourceRepository
) {

    operator fun invoke() = repository.getRamUsage()
}