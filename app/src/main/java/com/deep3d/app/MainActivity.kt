package com.deep3d.app

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

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

        // Varsa önceki kaydı göster
        updateStateWithSavedMac()

        // 1) Bluetooth cihaz seçimi
        btnConnect.setOnClickListener {
            // Projende zaten var olan cihaz listesi ekranı
            val i = Intent(this, DeviceListActivity::class.java)
            startActivityForResult(i, REQ_PICK_DEVICE)
        }

        // 2) Gerçek zamanlı ekrana geçiş – MAC’i intent ile de, yoksa SharedPref’ten de alır
        btnRealtime.setOnClickListener {
            val i = Intent(this, RealtimeActivity::class.java)
            // intent ile geçir (varsa)
            readSavedMac()?.let { mac ->
                i.putExtra(EXTRA_DEVICE_ADDRESS, mac)
            }
            startActivity(i)
        }

        // Örnek: Grid/Harita (şimdilik sadece aç)
        btnGrid.setOnClickListener {
            startActivity(Intent(this, GridActivity::class.java))
        }
    }

    // Cihaz listesinden dönüşü yakala ve MAC’i kaydet
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_PICK_DEVICE && resultCode == Activity.RESULT_OK) {
            // Bazı projelerde anahtar farklı olabiliyor; ikisini de deniyoruz
            val mac = data?.getStringExtra(EXTRA_DEVICE_ADDRESS)
                ?: data?.getStringExtra("device_address")
            if (!mac.isNullOrBlank()) {
                saveMac(mac)
                updateStateWithSavedMac()
            } else {
                tvState.text = "Hazır"
            }
        }
    }

    private fun updateStateWithSavedMac() {
        val mac = readSavedMac()
        tvState.text = if (!mac.isNullOrBlank()) "Bağlandı: $mac" else "Hazır"
    }

    // --- SharedPreferences yardımcıları ---
    private fun saveMac(mac: String) {
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_MAC, mac)
            .apply()
    }

    private fun readSavedMac(): String? {
        return getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_MAC, null)
    }

    companion object {
        // Intent extra anahtarı – RealtimeActivity ile AYNISI olmalı
        const val EXTRA_DEVICE_ADDRESS = "EXTRA_DEVICE_ADDRESS"

        // Cihaz seçimi için request code
        private const val REQ_PICK_DEVICE = 1001

        // SharedPreferences
        private const val PREFS_NAME = "deep3d_prefs"
        private const val KEY_MAC = "mac_address"
    }
}
