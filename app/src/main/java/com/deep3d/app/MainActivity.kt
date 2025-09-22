package com.deep3d.app

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    companion object {
        private const val REQ_PICK_DEVICE = 101
        private const val PREFS_NAME = "deep3d_prefs"
        private const val KEY_LAST_MAC = "last_mac"
        const val EXTRA_ADDRESS = "bt_addr"
    }

    private lateinit var tvState: TextView
    private lateinit var btnConnect: Button
    private lateinit var btnRealtime: Button
    private lateinit var btnGrid: Button

    private var lastMac: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvState = findViewById(R.id.tvState)          // activity_main.xml’deki id
        btnConnect = findViewById(R.id.btnConnect)
        btnRealtime = findViewById(R.id.btnRealtime)
        btnGrid = findViewById(R.id.btnGrid)

        // Daha önce saklanan MAC'i yükle
        lastMac = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_LAST_MAC, null)
        renderState()

        // “Bağlan (Bluetooth)” -> cihaz seçme ekranına git
        btnConnect.setOnClickListener {
            val i = Intent(this, DeviceListActivity::class.java)
            startActivityForResult(i, REQ_PICK_DEVICE)
        }

        // “Gerçek Zamanlı (grafik)” -> RealtimeActivity'ye MAC ile geç
        btnRealtime.setOnClickListener {
            val mac = lastMac
            if (mac.isNullOrBlank()) {
                Toast.makeText(this, "Önce cihaz seçin/bağlanın.", Toast.LENGTH_SHORT).show()
                // İstersen otomatik cihaz seçim ekranını da açabiliriz:
                // startActivityForResult(Intent(this, DeviceListActivity::class.java), REQ_PICK_DEVICE)
                return@setOnClickListener
            }
            val i = Intent(this, RealtimeActivity::class.java)
            i.putExtra(EXTRA_ADDRESS, mac)
            startActivity(i)
        }

        // Grid / Harita butonuna şimdilik sadece bilgi ver
        btnGrid.setOnClickListener {
            Toast.makeText(this, "Grid/Harita henüz burada başlatılmıyor.", Toast.LENGTH_SHORT).show()
        }
    }

    // DeviceListActivity sonuç: “device_address” ile döner (projendeki mevcut isim böyleyse)
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_PICK_DEVICE && resultCode == Activity.RESULT_OK && data != null) {
            // Çoğu örnekte anahtar “device_address” olur. Sende farklıysa ona göre değiştir.
            val mac = data.getStringExtra("device_address")
            if (!mac.isNullOrBlank()) {
                saveMac(mac)
                Toast.makeText(this, "Cihaz seçildi: $mac", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "MAC adresi alınamadı.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun saveMac(mac: String) {
        lastMac = mac
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LAST_MAC, mac)
            .apply()
        renderState()
    }

    private fun renderState() {
        tvState.text = if (lastMac.isNullOrBlank()) {
            "Hazır"
        } else {
            "Bağlandı: $lastMac"
        }
    }
}
