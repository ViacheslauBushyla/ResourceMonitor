package com.vnazarov.resourcemonitor.domain.usecase

import com.vnazarov.resourcemonitor.domain.repository.ResourceRepository

class GetCpuLoadPercUseCase(
    private val repository: ResourceRepository
) {

    operator fun invoke() = repository.getCpuLoadPerc()
}