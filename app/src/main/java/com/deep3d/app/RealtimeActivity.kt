package com.deep3d.app

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class RealtimeActivity : AppCompatActivity() {

    private lateinit var tvRealtimeStatus: TextView
    private lateinit var btnStartStream: Button
    private lateinit var btnStopStream: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_realtime)

        tvRealtimeStatus = findViewById(R.id.tvRealtimeStatus)
        btnStartStream = findViewById(R.id.btnStartStream)
        btnStopStream = findViewById(R.id.btnStopStream)

        tvRealtimeStatus.text = "Hazır – bağlan ve başlat"

        btnStartStream.setOnClickListener {
            Toast.makeText(this, "Kalibrasyon/akış (demo)", Toast.LENGTH_SHORT).show()
        }
        btnStopStream.setOnClickListener {
            Toast.makeText(this, "Durduruldu (demo)", Toast.LENGTH_SHORT).show()
        }
    }
}
