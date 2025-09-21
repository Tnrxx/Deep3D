package com.deep3d.app

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class RealtimeActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_realtime)

        val tvInfo: TextView = findViewById(R.id.tvInfo)

        // MainActivity'den gelen adres (varsa)
        val addr = intent.getStringExtra("deviceAddress")
        tvInfo.text = if (!addr.isNullOrEmpty()) {
            "Gerçek zamanlı ekran (placeholder)\nBağlı cihaz: $addr"
        } else {
            "Gerçek zamanlı ekran (placeholder)\nBağlı cihaz yok."
        }
    }
}
