package com.deep3d.app

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var tvState: TextView
    private lateinit var btnConnect: Button
    private lateinit var btnRealtime: Button
    private lateinit var btnGrid: Button

    // registerForActivityResult ile modern dönüş yakalama
    private val pickDevice =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val addr = result.data?.getStringExtra("deviceAddress")
                if (!addr.isNullOrEmpty()) {
                    tvState.text = "Bağlandı: $addr"
                    Toast.makeText(this, "Cihaz bağlandı ($addr)", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "Bağlantı iptal edildi", Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvState = findViewById(R.id.tvState)      // activity_main.xml içinde olmalı
        btnConnect = findViewById(R.id.btnConnect)
        btnRealtime = findViewById(R.id.btnRealtime)
        btnGrid = findViewById(R.id.btnGrid)

        tvState.text = "Hazır"

        // 1) Bağlan butonu → DeviceListActivity
        btnConnect.setOnClickListener {
            val intent = Intent(this, DeviceListActivity::class.java)
            pickDevice.launch(intent)
        }

        // 2) Realtime (şimdilik sadece bilgi)
        btnRealtime.setOnClickListener {
            Toast.makeText(this, "Realtime ekranı henüz ekli değil.", Toast.LENGTH_SHORT).show()
        }

        // 3) Grid/Harita (şimdilik sadece bilgi)
        btnGrid.setOnClickListener {
            Toast.makeText(this, "Grid/Harita ekranı henüz ekli değil.", Toast.LENGTH_SHORT).show()
        }
    }
}
