package com.deep3d.app

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class RealtimeActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_realtime)

        val tv = findViewById<TextView>(R.id.tvRealtimeInfo)

        // MainActivity'nin kaydettiği son cihazı göster
        val last = getSharedPreferences("app", MODE_PRIVATE).getString("lastDevice", null)
        tv.text = if (last.isNullOrEmpty()) {
            "Gerçek Zamanlı ekran – henüz bağlı cihaz yok.\nÖnce 'Bağlan' ile cihaz seç."
        } else {
            "Gerçek Zamanlı ekran – son cihaz: $last"
        }
    }
}