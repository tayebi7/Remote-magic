package com.remotemagic.app.remote

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager

/** Converts gyroscope rates (rad/s) to pointer pixels, keeping sub-pixel remainders. */
class MotionMapper(var speed: Float = 1f) {
    private var biasX = 0f
    private var biasZ = 0f
    private var restX = 0f
    private var restY = 0f
    private var samples = 0
    private var sumX = 0f
    private var sumZ = 0f

    fun startCalibration() {
        samples = CALIBRATION_SAMPLES
        sumX = 0f
        sumZ = 0f
    }

    val calibrating: Boolean get() = samples > 0

    /** [wx] is pitch rate, [wz] yaw rate, [dt] seconds. */
    fun map(wx: Float, wz: Float, dt: Float): Pair<Int, Int> {
        if (samples > 0) {
            sumX += wx
            sumZ += wz
            if (--samples == 0) {
                biasX = sumX / CALIBRATION_SAMPLES
                biasZ = sumZ / CALIBRATION_SAMPLES
            }
            return 0 to 0
        }
        fun filtered(v: Float) = if (kotlin.math.abs(v) < DEAD_ZONE) 0f else v
        restX -= filtered(wz - biasZ) * GAIN * speed * dt
        restY -= filtered(wx - biasX) * GAIN * speed * dt
        val x = restX.toInt()
        val y = restY.toInt()
        restX -= x
        restY -= y
        return x to y
    }

    companion object {
        const val CALIBRATION_SAMPLES = 60
        private const val DEAD_ZONE = 0.02f
        private const val GAIN = 900f
    }
}

class AirMouseSensor(context: Context, private val mapper: MotionMapper, private val onMove: (Int, Int) -> Unit) :
    SensorEventListener {
    private val manager = context.getSystemService(SensorManager::class.java)
    private val gyroscope = manager?.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    private var last = 0L

    val available: Boolean get() = gyroscope != null

    fun start() {
        last = 0
        gyroscope?.let { manager?.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
    }

    fun stop() = manager?.unregisterListener(this) ?: Unit

    override fun onSensorChanged(event: SensorEvent) {
        val dt = if (last == 0L) 0f else ((event.timestamp - last) / 1e9f).coerceAtMost(0.1f)
        last = event.timestamp
        val (dx, dy) = mapper.map(event.values[0], event.values[2], dt)
        if (dx != 0 || dy != 0) onMove(dx, dy)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
