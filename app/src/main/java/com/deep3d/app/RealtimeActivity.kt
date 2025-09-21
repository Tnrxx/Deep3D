package com.deep3d.app

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class RealtimeActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_realtime)

        val tv = findViewById<TextView>(R.id.tvInfo)
        val btnStart = findViewById<Button>(R.id.btnStart)
        val btnStop = findViewById<Button>(R.id.btnStop)

        tv.text = "Gerçek zamanlı akış (demo)"

        btnStart.setOnClickListener {
            Toast.makeText(this, "Başlatıldı (demo)", Toast.LENGTH_SHORT).show()
        }

        btnStop.setOnClickListener {
            Toast.makeText(this, "Durduruldu (demo)", Toast.LENGTH_SHORT).show()
        }
    }
}
