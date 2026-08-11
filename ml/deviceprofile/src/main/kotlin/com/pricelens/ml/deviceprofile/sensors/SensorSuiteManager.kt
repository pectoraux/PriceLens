package com.pricelens.ml.deviceprofile.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.pricelens.core.common.logging.Logger
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SensorSuiteManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val logger: Logger
) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    
    private val gyro = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    private val accel = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val rotationVector = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    private val ambientLight = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)

    private val listeners = mutableListOf<SensorDataListener>()

    interface SensorDataListener {
        fun onGyroChanged(x: Float, y: Float, z: Float)
        fun onAccelChanged(x: Float, y: Float, z: Float)
    }

    fun startTracking() {
        gyro?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
        accel?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
        rotationVector?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
        ambientLight?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
    }

    fun stopTracking() {
        sensorManager.unregisterListener(this)
    }

    fun addListener(listener: SensorDataListener) {
        listeners.add(listener)
    }

    fun removeListener(listener: SensorDataListener) {
        listeners.remove(listener)
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_GYROSCOPE -> {
                listeners.forEach { it.onGyroChanged(event.values[0], event.values[1], event.values[2]) }
            }
            Sensor.TYPE_ACCELEROMETER -> {
                listeners.forEach { it.onAccelChanged(event.values[0], event.values[1], event.values[2]) }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Not used for now
    }
}
