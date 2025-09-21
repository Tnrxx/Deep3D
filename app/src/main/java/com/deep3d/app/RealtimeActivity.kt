package com.deep3d.app

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class RealtimeActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_realtime)

        // Basit bir yazı gösterelim (derleme hatası riski olmasın)
        findViewById<TextView>(R.id.tvRealtime).text =
            "Gerçek zaman ekranı açıldı (demo)."
    }
}
