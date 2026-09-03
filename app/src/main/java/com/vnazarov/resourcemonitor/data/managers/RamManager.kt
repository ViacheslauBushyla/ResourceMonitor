package com.vnazarov.resourcemonitor.data.managers

import android.content.Context

class RamManager(
    context: Context
) {

    private val applicationContext = context.applicationContext

    fun getMemoryUsage(): Int {
        return 0
    }
}