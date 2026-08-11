package com.pricelens.ml.runtime.thermal

import android.content.Context
import android.os.PowerManager
import com.pricelens.core.common.logging.Logger
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ThermalPolicyManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val logger: Logger
) {
    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager

    interface ThermalStatusListener {
        fun onThermalStatusChanged(status: Int)
    }

    private val listeners = mutableListOf<ThermalStatusListener>()

    fun startMonitoring() {
        powerManager.addThermalStatusListener { status ->
            logger.i("ThermalPolicyManager", "Thermal status changed: $status")
            listeners.forEach { it.onThermalStatusChanged(status) }
        }
    }

    fun addListener(listener: ThermalStatusListener) {
        listeners.add(listener)
    }

    fun removeListener(listener: ThermalStatusListener) {
        listeners.remove(listener)
    }

    fun getCurrentStatus(): Int {
        return powerManager.currentThermalStatus
    }
}
