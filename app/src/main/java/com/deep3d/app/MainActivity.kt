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

    // Kalıcı saklama
    private val prefs by lazy { getSharedPreferences("deep3d_prefs", MODE_PRIVATE) }
    private val KEY_DEVICE_ADDR = "device_addr"

    // Hafızadaki durum (null değilse bağlı kabul)
    private var deviceAddress: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvState     = findViewById(R.id.tvState)
        btnConnect  = findViewById(R.id.btnConnect)
        btnRealtime = findViewById(R.id.btnRealtime)
        btnGrid     = findViewById(R.id.btnGrid)

        // Uygulama yeniden açılmış olsa da adresi geri yükle
        deviceAddress = prefs.getString(KEY_DEVICE_ADDR, null)
        tvState.text = if (deviceAddress.isNullOrEmpty()) "Hazır" else "Bağlandı: $deviceAddress"

        // 1) Cihaz listesi
        btnConnect.setOnClickListener {
            val i = Intent(this, DeviceListActivity::class.java)
            startActivityForResult(i, 2001)
        }

        // 2) Realtime
        btnRealtime.setOnClickListener {
            val addr = deviceAddress
            if (addr.isNullOrEmpty()) {
                Toast.makeText(this, "Önce cihaza bağlan.", Toast.LENGTH_SHORT).show()
            } else {
                val i = Intent(this, RealtimeActivity::class.java)
                i.putExtra("deviceAddress", addr)
                startActivity(i)
            }
        }

        // 3) Grid/Harita (şimdilik)
        btnGrid.setOnClickListener {
            Toast.makeText(this, "Grid/Harita ekranı henüz ekli değil.", Toast.LENGTH_SHORT).show()
        }
    }

    @Deprecated("Basit kullanım için onActivityResult yeterli")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 2001 && resultCode == Activity.RESULT_OK) {
            deviceAddress = data?.getStringExtra("deviceAddress")
            if (!deviceAddress.isNullOrEmpty()) {
                // Ekranı güncelle + kalıcı kaydet
                tvState.text = "Bağlandı: $deviceAddress"
                prefs.edit().putString(KEY_DEVICE_ADDR, deviceAddress).apply()
                Toast.makeText(this, "Cihaz bağlandı ($deviceAddress)", Toast.LENGTH_SHORT).show()
            } else {
                tvState.text = "Hazır"
            }
        }
    }
}
