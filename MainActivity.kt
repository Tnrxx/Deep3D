package com.deep3d.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat

class MainActivity : AppCompatActivity() {

    private lateinit var status: TextView
    private lateinit var btnConnect: Button
    private lateinit var btnRealtime: Button
    private lateinit var btnGrid: Button

    private val permLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* dönüş gerekmiyor; izin verildiyse butonlar çalışır */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        status = findViewById(R.id.txtStatus)
        btnConnect = findViewById(R.id.btnConnect)
        btnRealtime = findViewById(R.id.btnRealtime)
        btnGrid = findViewById(R.id.btnGrid)

        status.text = "Hazır"

        // Gerekli izinleri iste
        requestNeededPermissions()

        // 1) Bağlan (Bluetooth ayarlarını aç – hızlı eşleştirme)
        btnConnect.setOnClickListener {
            // Telefonun Bluetooth ayarlarına gitsin
            startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
        }

        // 2) Gerçek zamanlı ekran
        btnRealtime.setOnClickListener {
            startActivity(Intent(this, RealtimeActivity::class.java))
        }

        // 3) Grid/Harita ekranı
        btnGrid.setOnClickListener {
            startActivity(Intent(this, GridActivity::class.java))
        }
    }

    private fun requestNeededPermissions() {
        val wants = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            wants += Manifest.permission.BLUETOOTH_SCAN
            wants += Manifest.permission.BLUETOOTH_CONNECT
        } else {
            // Eski sürümler için konum gerekli olabilir
            wants += Manifest.permission.ACCESS_FINE_LOCATION
        }
        if (wants.any { ActivityCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }) {
            permLauncher.launch(wants.toTypedArray())
        }
    }
}
