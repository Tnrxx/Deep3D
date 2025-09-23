package com.deep3d.app

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var txtStatus: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        txtStatus = findViewById(R.id.txtStatus)

        findViewById<Button>(R.id.btnConnect).setOnClickListener {
            // Bluetooth ayarlarını açıyoruz; eşleştirip geri dön
            startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
            txtStatus.text = "Bluetooth ayarları açıldı. Cihazı eşleştirip uygulamaya geri dön."
        }

        findViewById<Button>(R.id.btnRealtime).setOnClickListener {
            startActivity(Intent(this, RealtimeActivity::class.java))
        }

        findViewById<Button>(R.id.btnGrid).setOnClickListener {
            startActivity(Intent(this, GridActivity::class.java))
        }
    }
}
