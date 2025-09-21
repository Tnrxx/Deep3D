package com.deep3d.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat

class MainActivity : AppCompatActivity() {

    // Çalışma zamanı izinleri (Android 12+ için BT izinleri)
    private val reqPermsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { /* no-op */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Gerekli izinleri iste
        requestBtPermissionsIfNeeded()

        // 1) BAĞLAN (Bluetooth) → DeviceListActivity
        findViewById<View>(R.id.btnConnect).setOnClickListener {
            startActivity(Intent(this, DeviceListActivity::class.java))
        }

        // 2) GERÇEK ZAMANLI (grafik) – varsa aç, yoksa uyar
        findViewById<View>(R.id.btnRealtime).setOnClickListener {
            try {
                startActivity(Intent(this, RealtimeActivity::class.java))
            } catch (_: Exception) {
                Toast.makeText(this, "Realtime ekranı henüz ekli değil.", Toast.LENGTH_SHORT).show()
            }
        }

        // 3) GRID / HARİTA – varsa aç, yoksa uyar
        findViewById<View>(R.id.btnGrid).setOnClickListener {
            try {
                startActivity(Intent(this, GridActivity::class.java))
            } catch (_: Exception) {
                Toast.makeText(this, "Grid/Harita ekranı henüz ekli değil.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun requestBtPermissionsIfNeeded() {
        val needed = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= 31) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN)
                != PackageManager.PERMISSION_GRANTED
            ) needed += Manifest.permission.BLUETOOTH_SCAN
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                != PackageManager.PERMISSION_GRANTED
            ) needed += Manifest.permission.BLUETOOTH_CONNECT
        } else {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED
            ) needed += Manifest.permission.ACCESS_FINE_LOCATION
        }
        if (needed.isNotEmpty()) reqPermsLauncher.launch(needed.toTypedArray())
    }
}
