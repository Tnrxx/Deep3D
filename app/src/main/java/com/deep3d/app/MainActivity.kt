package com.deep3d.app

import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import androidx.activity.ComponentActivity

class MainActivity : ComponentActivity() {

    private val btAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val tvStatus = findViewById<TextView>(R.id.tvStatus)
        val btnConnect = findViewById<Button>(R.id.btnConnect)
        val btnRealtime = findViewById<Button>(R.id.btnRealtime)
        val btnGrid = findViewById<Button>(R.id.btnGrid)

        tvStatus.text = if (btAdapter == null) {
            "Bluetooth yok"
        } else if (btAdapter!!.isEnabled) {
            "Bluetooth açık"
        } else {
            "Bluetooth kapalı"
        }

        btnConnect.setOnClickListener {
            // 1) Bluetooth kapalıysa açtır
            if (btAdapter != null && !btAdapter.isEnabled) {
                startActivity(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
            }
            // 2) Eşleşme/cihaz seçimi için sistem Bluetooth ayarlarını aç
            startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
        }

        btnRealtime.setOnClickListener {
            startActivity(Intent(this, RealtimeActivity::class.java))
        }

        btnGrid.setOnClickListener {
            startActivity(Intent(this, GridActivity::class.java))
        }
    }
}
