package com.deep3d.app

import android.app.AlertDialog
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
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

    // Kaydettiğimiz adres (SharedPreferences)
    private var connectedDeviceAddress: String?
        get() = getSharedPreferences("bt", Context.MODE_PRIVATE)
            .getString("addr", null)
        set(value) {
            getSharedPreferences("bt", Context.MODE_PRIVATE)
                .edit().putString("addr", value).apply()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvState = findViewById(R.id.tvState)
        btnConnect = findViewById(R.id.btnConnect)
        btnRealtime = findViewById(R.id.btnRealtime)
        btnGrid = findViewById(R.id.btnGrid)

        updateUiWithSavedAddress()

        btnConnect.setOnClickListener { pickPairedDevice() }

        btnRealtime.setOnClickListener {
            val addr = connectedDeviceAddress
            if (addr.isNullOrBlank()) {
                Toast.makeText(this, "Önce cihaza bağlan.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            // RealtimeActivity soket açacak; adrese buradan gidiyoruz
            val i = Intent(this, RealtimeActivity::class.java)
            i.putExtra("device_address", addr)
            startActivity(i)
        }

        btnGrid.setOnClickListener {
            Toast.makeText(this, "Grid/Harita ekranı henüz hazır değil.", Toast.LENGTH_SHORT).show()
        }
    }

    /** Eşleştirilmiş cihazlar listesinden seçim yaptırır ve adresi kaydeder */
    private fun pickPairedDevice() {
        val adapter = btAdapter
        if (adapter == null) {
            Toast.makeText(this, "Bu cihazda Bluetooth yok.", Toast.LENGTH_LONG).show()
            return
        }
        if (!adapter.isEnabled) {
            startActivity(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
            Toast.makeText(this, "Bluetooth’u açıp tekrar deneyin.", Toast.LENGTH_SHORT).show()
            return
        }

        val bonded = adapter.bondedDevices?.toList().orEmpty()
        if (bonded.isEmpty()) {
            Toast.makeText(this, "Eşleştirilmiş cihaz yok. Ayarlardan eşleştirin.", Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
            return
        }

        val items = bonded.map { "${it.name ?: "Bilinmiyor"}\n${it.address}" }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Cihaz seç")
            .setItems(items) { dialog, which ->
                val device = bonded[which]
                setConnectedDevice(device) // MAC’i yaz ve kaydet
                dialog.dismiss()
            }
            .setNegativeButton("İptal", null)
            .show()
    }

    /** UI’ı ve kaydı günceller (BluetoothDevice ile) */
    private fun setConnectedDevice(device: BluetoothDevice?) {
        if (device == null) {
            connectedDeviceAddress = null
            tvState.text = "Hazır"
        } else {
            connectedDeviceAddress = device.address
            tvState.text = "Bağlandı: ${device.address}"
        }
    }

    /** Uygulama açıldığında daha önce seçilmiş adres varsa yaz */
    private fun updateUiWithSavedAddress() {
        val addr = connectedDeviceAddress
        tvState.text = if (addr.isNullOrBlank()) "Hazır" else "Bağlandı: $addr"
    }
}
