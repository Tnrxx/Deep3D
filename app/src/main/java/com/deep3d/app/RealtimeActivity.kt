package com.deep3d.app

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class RealtimeActivity : AppCompatActivity() {

    private lateinit var tvInfo: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_realtime)

        tvInfo = findViewById(R.id.tvInfo)

        val intentAddr = intent.getStringExtra("deviceAddress")
        val prefsAddr = getSharedPreferences("deep3d_prefs", MODE_PRIVATE)
            .getString("device_addr", "—")
        val addr = intentAddr ?: prefsAddr

        tvInfo.text = "Gerçek zamanlı ekran (cihaz: $addr)"
    }
}
