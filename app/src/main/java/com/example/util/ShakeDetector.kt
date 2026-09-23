package com.example.util

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.sqrt

class ShakeDetector(
    context: Context,
    private val onShakeTriggered: () -> Unit
) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private var isListening = false
    private var shakeStartTime: Long = 0L
    private var lastShakeEventTime: Long = 0L
    private var lastTriggerTime: Long = 0L

    companion object {
        private const val SHAKE_THRESHOLD = 11.5f // Acceleration above earth gravity
        private const val EVENT_CONTINUITY_WINDOW_MS = 500L // Time gap allowed between shakes
        private const val TRIGGER_COOLDOWN_MS = 2000L
    }

    fun start() {
        if (isListening || accelerometer == null) return
        sensorManager?.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI)
        isListening = true
    }

    fun stop() {
        if (!isListening) return
        sensorManager?.unregisterListener(this)
        isListening = false
        shakeStartTime = 0L
        lastShakeEventTime = 0L
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || event.sensor.type != Sensor.TYPE_ACCELEROMETER) return

        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]

        val gForce = sqrt((x * x + y * y + z * z).toDouble()).toFloat() - SensorManager.GRAVITY_EARTH

        val now = System.currentTimeMillis()
        if (now - lastTriggerTime < TRIGGER_COOLDOWN_MS) {
            return
        }

        val settings = SettingsManager.settings.value
        if (!settings.shakeToLockEnabled) {
            return
        }

        val requiredDurationMs = (settings.shakeToLockDurationSeconds * 1000L).toLong()

        if (gForce > SHAKE_THRESHOLD) {
            if (shakeStartTime == 0L || (now - lastShakeEventTime > EVENT_CONTINUITY_WINDOW_MS)) {
                shakeStartTime = now
            }
            lastShakeEventTime = now

            val continuousShakeDuration = now - shakeStartTime
            if (continuousShakeDuration >= requiredDurationMs) {
                lastTriggerTime = now
                shakeStartTime = 0L
                lastShakeEventTime = 0L
                onShakeTriggered()
            }
        } else {
            if (now - lastShakeEventTime > EVENT_CONTINUITY_WINDOW_MS) {
                shakeStartTime = 0L
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
