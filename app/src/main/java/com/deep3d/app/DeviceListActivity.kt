package com.deep3d.app

import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat

class DeviceListActivity : AppCompatActivity() {
    private lateinit var list: ListView
    private lateinit var empty: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_device_list)

        list = findViewById(R.id.listDevices)
        empty = findViewById(R.id.txtEmpty)
        list.emptyView = empty

        val manager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val adapter = manager.adapter ?: run {
            Toast.makeText(this, "Bluetooth yok", Toast.LENGTH_SHORT).show()
            finish(); return
        }

        if (Build.VERSION.SDK_INT >= 31 &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
            != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "BLUETOOTH_CONNECT izni gerekli", Toast.LENGTH_LONG).show()
            startActivity(Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS))
            finish(); return
        }

        val bonded = adapter.bondedDevices.toList().sortedBy { it.name ?: it.address }
        val items = bonded.map { "${it.name ?: "(isimsiz)"}\n${it.address}" }
        list.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, items)

        list.setOnItemClickListener { _, _, position, _ ->
            val chosen = bonded[position]
            getSharedPreferences("deep3d", MODE_PRIVATE).edit()
                .putString("bt_address", chosen.address).apply()
            Toast.makeText(this, "Seçildi: ${chosen.name}", Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}
