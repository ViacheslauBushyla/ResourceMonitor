package com.vnazarov.resourcemonitor.data.managers

import android.content.Context

class CpuManager(
    context: Context
) {

    private val applicationContext = context.applicationContext

    fun getLoad(): Int {
        return 0
    }

    fun getFreq(): Int {
        return 0
    }
}