package com.deep3d.app

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat

class MainActivity : AppCompatActivity() {

    private lateinit var tvState: TextView
    private lateinit var btnConnect: Button
    private lateinit var btnRealtime: Button
    private lateinit var btnGrid: Button

    private val bt: BluetoothAdapter? by lazy { BluetoothAdapter.getDefaultAdapter() }

    private val reqBtOn = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { /* no-op */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvState = findViewById(R.id.tvState)
        btnConnect = findViewById(R.id.btnConnect)
        btnRealtime = findViewById(R.id.btnRealtime)
        btnGrid = findViewById(R.id.btnGrid)

        // Önceden seçilmiş cihaz varsa başlıkta göster
        val sp = getSharedPreferences("deep3d", MODE_PRIVATE)
        val mac = sp.getString("device_mac", null)
        tvState.text = if (mac.isNullOrEmpty()) "Hazır" else "Bağlandı: $mac"

        btnConnect.setOnClickListener { pickDevice() }

        btnRealtime.setOnClickListener {
            val saved = getSharedPreferences("deep3d", MODE_PRIVATE).getString("device_mac", null)
            if (saved.isNullOrEmpty()) {
                Toast.makeText(this, "Önce cihaza bağlan.", Toast.LENGTH_SHORT).show()
            } else {
                startActivity(Intent(this, RealtimeActivity::class.java))
            }
        }

        btnGrid.setOnClickListener {
            Toast.makeText(this, "Grid/Harita ekranı henüz ekli değil.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun ensurePerms(): Boolean {
        if (bt == null) {
            Toast.makeText(this, "Bu cihaz Bluetooth desteklemiyor.", Toast.LENGTH_LONG).show()
            return false
        }
        if (!(bt?.isEnabled ?: false)) {
            reqBtOn.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
            return false
        }
        if (Build.VERSION.SDK_INT >= 31) {
            val need = arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT
            ).any {
                ActivityCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
            }
            if (need) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(
                        Manifest.permission.BLUETOOTH_SCAN,
                        Manifest.permission.BLUETOOTH_CONNECT
                    ),
                    1001
                )
                return false
            }
        }
        return true
    }

    @SuppressLint("MissingPermission")
    private fun pickDevice() {
        if (!ensurePerms()) return
        val bonded = bt!!.bondedDevices?.toList().orEmpty()
        if (bonded.isEmpty()) {
            Toast.makeText(this, "Eşleştirilmiş cihaz yok.", Toast.LENGTH_LONG).show()
            return
        }
        val names = bonded.map { "${it.name}\n${it.address}" }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Cihaz seç")
            .setItems(names) { _, which ->
                val dev: BluetoothDevice = bonded[which]
                val sp = getSharedPreferences("deep3d", MODE_PRIVATE)
                sp.edit().putString("device_mac", dev.address).apply()
                tvState.text = "Bağlandı: ${dev.address}"
                Toast.makeText(this, "Seçildi: ${dev.name}", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("İptal", null)
            .show()
    }
}
