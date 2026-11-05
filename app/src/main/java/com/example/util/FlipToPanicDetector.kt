package com.example.util

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.abs

/**
 * Detects when the device is flipped face-down (placed on a table or covered face-down).
 * Triggers Panic Mode immediately if enabled in settings.
 */
class FlipToPanicDetector(
    context: Context,
    private val onFlipTriggered: () -> Unit
) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val proximitySensor = sensorManager?.getDefaultSensor(Sensor.TYPE_PROXIMITY)

    private var isListening = false
    private var faceDownStartTime = 0L
    private var lastTriggerTime = 0L
    private var isProximityNear = false

    companion object {
        private const val FACE_DOWN_HOLD_REQUIRED_MS = 250L // Must be face-down for 250ms to prevent accidental triggers
        private const val TRIGGER_COOLDOWN_MS = 3000L
        private const val Z_FACE_DOWN_THRESHOLD = -7.0f // Gravity on Z axis pointing down through screen
    }

    fun start() {
        if (isListening) return
        accelerometer?.let {
            sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
        proximitySensor?.let {
            sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
        isListening = true
    }

    fun stop() {
        if (!isListening) return
        sensorManager?.unregisterListener(this)
        isListening = false
        faceDownStartTime = 0L
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return

        val now = System.currentTimeMillis()
        if (now - lastTriggerTime < TRIGGER_COOLDOWN_MS) return

        val settings = SettingsManager.settings.value
        if (!settings.flipToPanicEnabled) return

        if (event.sensor.type == Sensor.TYPE_PROXIMITY) {
            val maxRange = event.sensor.maximumRange
            isProximityNear = event.values[0] < maxRange
        } else if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]

            val isFaceDown = z < Z_FACE_DOWN_THRESHOLD && abs(x) < 4.5f && abs(y) < 4.5f

            if (isFaceDown || (isProximityNear && z < -4.0f)) {
                if (faceDownStartTime == 0L) {
                    faceDownStartTime = now
                } else if (now - faceDownStartTime >= FACE_DOWN_HOLD_REQUIRED_MS) {
                    lastTriggerTime = now
                    faceDownStartTime = 0L
                    onFlipTriggered()
                }
            } else {
                faceDownStartTime = 0L
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
