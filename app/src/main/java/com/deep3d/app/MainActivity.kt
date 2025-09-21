package com.deep3d.app

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var tvState: TextView
    private lateinit var btnConnect: Button
    private lateinit var btnRealtime: Button
    private lateinit var btnGrid: Button

    // Son bağlanılan cihazın adresini burada tutacağız
    private var lastDeviceAddress: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvState = findViewById(R.id.tvState)
        btnConnect = findViewById(R.id.btnConnect)
        btnRealtime = findViewById(R.id.btnRealtime)
        btnGrid = findViewById(R.id.btnGrid)

        tvState.text = "Hazır"

        // Başta realtime ve grid'i pasif yap (adres yokken basılamasın)
        btnRealtime.isEnabled = false
        btnGrid.isEnabled = false

        // 1) Bağlan: DeviceListActivity aç
        btnConnect.setOnClickListener {
            val i = Intent(this, DeviceListActivity::class.java)
            startActivityForResult(i, 2001)
        }

        // 2) Realtime: Adresi RealtimeActivity'ye gönder
        btnRealtime.setOnClickListener {
            val addr = lastDeviceAddress
            if (addr == null) {
                Toast.makeText(this, "Önce cihaza bağlan.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val i = Intent(this, RealtimeActivity::class.java)
            i.putExtra("deviceAddress", addr)
            startActivity(i)
        }

        // 3) Grid/Harita şimdilik bilgi amaçlı
        btnGrid.setOnClickListener {
            Toast.makeText(this, "Grid/Harita ekranı henüz ekli değil.", Toast.LENGTH_SHORT).show()
        }
    }

    @Deprecated("Basit senaryo için yeterli")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 2001 && resultCode == Activity.RESULT_OK) {
            val addr = data?.getStringExtra("deviceAddress") ?: return
            lastDeviceAddress = addr
            tvState.text = "Bağlandı: $addr"

            // Artık realtime ve grid'i aktif edebiliriz
            btnRealtime.isEnabled = true
            btnGrid.isEnabled = true
        }
    }
}
