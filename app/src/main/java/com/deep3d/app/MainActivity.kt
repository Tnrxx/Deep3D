package com.deep3d.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var btnConnect: Button
    private lateinit var btnRealtime: Button
    private lateinit var btnGrid: Button

    private val permRequester = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* sonuç önemli değil; butonları açık bırakacağız */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        btnConnect = findViewById(R.id.btnConnect)
        btnRealtime = findViewById(R.id.btnRealtime)
        btnGrid = findViewById(R.id.btnGrid)

        statusText.text = "Hazır"

        // Gerekli izinleri iste (Bluetooth / Konum – Android sürümüne göre)
        requestNeededPermissions()

        // 1) Bağlan: şimdilik doğrudan Bluetooth ayarlarını açıyoruz
        btnConnect.setOnClickListener {
            val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
            startActivity(intent)
        }

        // 2) Gerçek Zamanlı ekran
        btnRealtime.setOnClickListener {
            startActivity(Intent(this, RealtimeActivity::class.java))
        }

        // 3) Grid / Harita ekranı
        btnGrid.setOnClickListener {
            startActivity(Intent(this, GridActivity::class.java))
        }
    }

    private fun requestNeededPermissions() {
        // Android 12+ için ek Bluetooth izinleri
        val perms = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            perms += Manifest.permission.BLUETOOTH_SCAN
            perms += Manifest.permission.BLUETOOTH_CONNECT
        } else {
            // Eski cihazlarda konum izni Bluetooth taraması için gerekebilir
            perms += Manifest.permission.ACCESS_FINE_LOCATION
        }

        val toAsk = perms.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()

        if (toAsk.isNotEmpty()) {
            permRequester.launch(toAsk)
        }
    }
}
