package com.deep3d.app

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

@Suppress("DEPRECATION") // CI'da garantili derlensin diye eski API'yi kullanıyoruz
class MainActivity : AppCompatActivity() {

    private lateinit var tvState: TextView
    private lateinit var btnConnect: Button
    private lateinit var btnRealtime: Button
    private lateinit var btnGrid: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvState = findViewById(R.id.tvState)
        btnConnect = findViewById(R.id.btnConnect)
        btnRealtime = findViewById(R.id.btnRealtime)
        btnGrid = findViewById(R.id.btnGrid)

        tvState.text = "Hazır"

        // 1) Bağlan → DeviceListActivity
        btnConnect.setOnClickListener {
            val i = Intent(this, DeviceListActivity::class.java)
            startActivityForResult(i, 2001)
        }

        // 2) Ekranlar daha sonra
        btnRealtime.setOnClickListener {
            Toast.makeText(this, "Realtime ekranı henüz ekli değil.", Toast.LENGTH_SHORT).show()
        }
        btnGrid.setOnClickListener {
            Toast.makeText(this, "Grid/Harita ekranı henüz ekli değil.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 2001 && resultCode == Activity.RESULT_OK) {
            val addr = data?.getStringExtra("deviceAddress") ?: return
            tvState.text = "Bağlandı: $addr"
            Toast.makeText(this, "Cihaz bağlandı ($addr)", Toast.LENGTH_SHORT).show()
        }
    }
}
