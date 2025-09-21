package com.deep3d.app

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private var tvState: TextView? = null
    private lateinit var btnConnect: Button
    private lateinit var btnRealtime: Button
    private lateinit var btnGrid: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // tvState zorunlu değil: varsa bul, yoksa null kalsın
        val tvId = resources.getIdentifier("tvState", "id", packageName)
        if (tvId != 0) tvState = findViewById(tvId)

        btnConnect = findViewById(R.id.btnConnect)
        btnRealtime = findViewById(R.id.btnRealtime)
        btnGrid = findViewById(R.id.btnGrid)

        tvState?.text = "Hazır"

        // Bağlan: cihaz listesini aç
        btnConnect.setOnClickListener {
            val i = Intent(this, DeviceListActivity::class.java)
            @Suppress("DEPRECATION")
            startActivityForResult(i, 2001)
        }

        // Realtime ve Grid: şimdilik bilgi mesajı
        btnRealtime.setOnClickListener {
            Toast.makeText(this, "Realtime ekranı henüz ekli değil.", Toast.LENGTH_SHORT).show()
        }
        btnGrid.setOnClickListener {
            Toast.makeText(this, "Grid/Harita ekranı henüz ekli değil.", Toast.LENGTH_SHORT).show()
        }
    }

    @Deprecated("Basit kullanım için uygun")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 2001 && resultCode == Activity.RESULT_OK) {
            val addr = data?.getStringExtra("deviceAddress") ?: return
            tvState?.text = "Bağlandı: $addr"
            Toast.makeText(this, "Cihaz bağlandı ($addr)", Toast.LENGTH_SHORT).show()
        }
    }
}
