package com.deep3d.app

import android.app.AlertDialog
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var tvState: TextView
    private lateinit var btnConnect: Button
    private lateinit var btnRealtime: Button
    private lateinit var btnGrid: Button

    private val btAdapter: BluetoothAdapter? by lazy { BluetoothAdapter.getDefaultAdapter() }

    // Seçilen cihazı burada saklıyoruz
    private var selectedDevice: BluetoothDevice? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvState = findViewById(R.id.tvState)
        btnConnect = findViewById(R.id.btnConnect)
        btnRealtime = findViewById(R.id.btnRealtime)
        btnGrid = findViewById(R.id.btnGrid)

        tvState.text = "Hazır"

        btnConnect.setOnClickListener { showPairedDevicesDialog() }

        btnRealtime.setOnClickListener {
            val dev = selectedDevice
            if (dev == null) {
                Toast.makeText(this, "Önce cihaza bağlan (Bluetooth)", Toast.LENGTH_SHORT).show()
            } else {
                // Cihaz adresi ve adı RealtimeActivity'ye gönderiliyor
                val intent = Intent(this, RealtimeActivity::class.java).apply {
                    putExtra("DEVICE_ADDRESS", dev.address)
                    putExtra("DEVICE_NAME", dev.name ?: "BT-Device")
                }
                startActivity(intent)
            }
        }

        btnGrid.setOnClickListener {
            Toast.makeText(this, "Grid/Harita ekranı henüz ekli değil.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showPairedDevicesDialog() {
        val adapter = btAdapter
        if (adapter == null) {
            Toast.makeText(this, "Bu cihaz Bluetooth desteklemiyor", Toast.LENGTH_LONG).show()
            return
        }
        if (!adapter.isEnabled) {
            Toast.makeText(this, "Lütfen Bluetooth'u açın", Toast.LENGTH_LONG).show()
            return
        }

        val bonded = adapter.bondedDevices?.toList().orEmpty()
        if (bonded.isEmpty()) {
            Toast.makeText(this, "Eşleştirilmiş cihaz bulunamadı", Toast.LENGTH_LONG).show()
            return
        }

        val items = bonded.map { d -> "${d.name ?: "Bilinmeyen"}\n${d.address}" }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("Cihaz seç")
            .setItems(items) { _, which ->
                selectedDevice = bonded[which]
                val addr = selectedDevice?.address ?: "-"
                tvState.text = "Bağlandı: $addr"
                Toast.makeText(this, "Seçildi: $addr", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("İptal", null)
            .show()
    }
}
