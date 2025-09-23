package com.deep3d.app

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var txtStatus: TextView
    private lateinit var btnConnect: Button
    private lateinit var btnRealtime: Button
    private lateinit var btnGrid: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // XML id'leri
        txtStatus   = findViewById(R.id.txtStatus)
        btnConnect  = findViewById(R.id.btnConnect)
        btnRealtime = findViewById(R.id.btnRealtime)
        btnGrid     = findViewById(R.id.btnGrid)

        btnConnect.setOnClickListener {
            startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
            txtStatus.text = "Bluetooth ayarları açıldı. Cihazı eşleştirip geri dön."
        }

        // Bu iki ekran sende farklı isimdeyse, sınıf adlarını burada değiştir
        btnRealtime.setOnClickListener {
            startActivity(Intent(this, RealtimeActivity::class.java))
        }

        btnGrid.setOnClickListener {
            startActivity(Intent(this, GridActivity::class.java))
        }
    }
}
