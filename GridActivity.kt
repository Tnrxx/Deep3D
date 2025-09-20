package com.deep3d.app.grid

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.Bundle
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.deep3d.app.R
import com.deep3d.app.bt.RfcommBtSource
import com.deep3d.upgrade.grid.GridRunner
import com.deep3d.upgrade.grid.GridStore

class GridActivity : AppCompatActivity() {

    private var runner: GridRunner? = null
    private var sensorManager: SensorManager? = null
    private var btSource: RfcommBtSource? = null
    private var store: GridStore? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_grid)

        val etCols = findViewById<EditText>(R.id.etCols)
        val etRows = findViewById<EditText>(R.id.etRows)
        val etCell = findViewById<EditText>(R.id.etCellM)
        val cbAuto = findViewById<CheckBox>(R.id.cbAutoStride)
        val cbStep = findViewById<CheckBox>(R.id.cbStepMode)
        val tvInfo = findViewById<TextView>(R.id.tvInfo)
        val tvProg = findViewById<TextView>(R.id.tvProgress)

        val deviceName = intent.getStringExtra("deviceName") ?: ""

        val adapter = BluetoothAdapter.getDefaultAdapter()
        val device: BluetoothDevice? = adapter?.bondedDevices?.firstOrNull { it.name == deviceName }
        btSource = device?.let { com.deep3d.app.bt.RfcommBtSource(it) }

        findViewById<Button>(R.id.btnStartGrid).setOnClickListener {
            val cols = etCols.text.toString().toIntOrNull() ?: 10
            val rows = etRows.text.toString().toIntOrNull() ?: 10
            val cell = etCell.text.toString().toDoubleOrNull() ?: 0.30

            store = GridStore(cols, rows)
            runner = GridRunner(requireNotNull(store), requireNotNull(btSource)).apply {
                cellSizeM = cell
                autoStride = cbAuto.isChecked
                stepModeEnabled = cbStep.isChecked
            }

            sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
            sensorManager?.registerListener(runner, sensorManager!!.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR), SensorManager.SENSOR_DELAY_GAME)
            sensorManager?.registerListener(runner, sensorManager!!.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR), SensorManager.SENSOR_DELAY_GAME)
            tvInfo.text = "Çalışıyor"
        }

        findViewById<Button>(R.id.btnStopGrid).setOnClickListener {
            sensorManager?.unregisterListener(runner)
            tvInfo.text = "Durduruldu"
            tvProg.text = "${store?.filledCount() ?: 0} hücre"
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        sensorManager?.unregisterListener(runner)
        btSource?.close()
    }
}
