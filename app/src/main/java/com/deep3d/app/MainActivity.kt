// app/src/main/java/com/deep3d/app/MainActivity.kt
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

    // Son bağlanılan cihaz adresini tutacağız
    private var lastAddress: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvState = findViewById(R.id.tvState)       // XML'deki id: tvState
        btnConnect = findViewById(R.id.btnConnect) // btnConnect
        btnRealtime = findViewById(R.id.btnRealtime) // btnRealtime
        btnGrid = findViewById(R.id.btnGrid)       // btnGrid

        tvState.text = "Hazır"

        // 1) Cihaz listesine git
        btnConnect.setOnClickListener {
            val i = Intent(this, DeviceListActivity::class.java)
            startActivityForResult(i, 2001)
        }

        // 2) Realtime’a sadece adres varsa git
        btnRealtime.setOnClickListener {
            val addr = lastAddress
            if (addr.isNullOrEmpty()) {
                Toast.makeText(this, "Önce cihaza bağlan.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val i = Intent(this, RealtimeActivity::class.java)
            i.putExtra("deviceAddress", addr)
            startActivity(i)
        }

        // 3) Grid/Harita şimdilik pasif
        btnGrid.setOnClickListener {
            Toast.makeText(this, "Grid/Harita ekranı henüz ekli değil.", Toast.LENGTH_SHORT).show()
        }
    }

    // DeviceListActivity'den dönen adresi al
    @Deprecated("Basit kullanım için yeterli")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 2001 && resultCode == Activity.RESULT_OK) {
            val addr = data?.getStringExtra("deviceAddress") ?: return
            lastAddress = addr
            tvState.text = "Bağlandı: $addr"
            Toast.makeText(this, "Cihaz bağlandı ($addr)", Toast.LENGTH_SHORT).show()
        }
    }
}
