package com.deep3d.upgrade.grid

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.deep3d.upgrade.bt.IBtSource
import kotlin.math.*

class GridRunner(
    private val store: GridStore,
    private val bt: IBtSource,
    var cellSizeM: Double = 0.30,
    var autoStride: Boolean = true
): SensorEventListener {

    var stepModeEnabled: Boolean = true
    private var heading = 0.0
    private var x = 0.0; private var y = 0.0
    private var strideM = 0.30
    private var lastStepTs = 0L
    private var cadenceHz = 0.0
    var forward = true

    override fun onSensorChanged(e: SensorEvent) {
        when (e.sensor.type) {
            Sensor.TYPE_ROTATION_VECTOR -> {
                val R = FloatArray(9); val ori = FloatArray(3)
                SensorManager.getRotationMatrixFromVector(R, e.values)
                SensorManager.getOrientation(R, ori)
                heading = ori[0].toDouble()
            }
            Sensor.TYPE_STEP_DETECTOR -> if (stepModeEnabled) {
                val now = System.currentTimeMillis()
                val dt = (now - lastStepTs) / 1000.0
                lastStepTs = now

                if (autoStride && dt>0) {
                    val stepHz = 1.0/dt
                    cadenceHz = 0.8*cadenceHz + 0.2*stepHz
                    strideM = (0.37 + 0.28 * (cadenceHz/2.0)).coerceIn(0.55, 0.85)
                }
                x += strideM * cos(heading); y += strideM * sin(heading)

                var col = (x / cellSizeM).roundToInt().coerceIn(0, store.cols-1)
                var row = (y / cellSizeM).roundToInt().coerceIn(0, store.rows-1)
                if (!forward) col = (store.cols - 1) - col

                val c = bt.readCMedian(3)
                store.put(row, col, c, null)
            }
        }
    }
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
