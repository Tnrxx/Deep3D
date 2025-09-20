package com.deep3d.app.realtime

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.os.Bundle
import android.widget.Button
import android.widget.CheckBox
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.deep3d.app.R
import com.deep3d.app.bt.RfcommBtSource
import com.deep3d.app.ui.RealtimeChartView
import com.deep3d.upgrade.realtime.RealtimeController

class RealtimeActivity : AppCompatActivity() {
    private lateinit var controller: RealtimeController
    private var btSource: RfcommBtSource? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_realtime)

        val state = findViewById<TextView>(R.id.tvState)
        val chart = findViewById<RealtimeChartView>(R.id.chart)
        val cb = findViewById<CheckBox>(R.id.cbMoving)

        val deviceName = intent.getStringExtra("deviceName") ?: ""

        val adapter = BluetoothAdapter.getDefaultAdapter()
        val device: BluetoothDevice? = adapter?.bondedDevices?.firstOrNull { it.name == deviceName }

        btSource = device?.let { RfcommBtSource(it) }

        controller = RealtimeController(
            bt = requireNotNull(btSource) { "Bluetooth cihaz bulunamadı: $deviceName" },
            onChart = { c, base -> chart.add(c.toFloat(), base.toFloat()) },
            onEvent = { ev -> chart.mark(ev) },
            onState = { s -> state.text = s }
        )

        findViewById<Button>(R.id.btnConnect).setOnClickListener {
            state.text = if (btSource?.connect() == true) "Bağlandı" else "Bağlantı hatası"
        }
        findViewById<Button>(R.id.btnCalib).setOnClickListener { controller.calibrate(6) }
        findViewById<Button>(R.id.btnStart).setOnClickListener { controller.start() }
        findViewById<Button>(R.id.btnStop).setOnClickListener { controller.stop() }
        cb.setOnCheckedChangeListener { _, isChecked -> controller.setMoving(isChecked) }
    }

    override fun onDestroy() {
        super.onDestroy()
        controller.stop()
        btSource?.close()
    }
}
