package com.vnazarov.resourcemonitor.domain.usecase

import com.vnazarov.resourcemonitor.domain.repository.ResourceRepository

class GetCpuFreqMHzUseCase(
    private val repository: ResourceRepository
) {

    operator fun invoke() = repository.getCpuFreqMHz()
}