package com.deep3d.app

import android.app.Activity
import android.bluetooth.BluetoothAdapter
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

    companion object {
        private const val REQ_SELECT_DEVICE = 1001
        const val EXTRA_DEVICE_ADDRESS = "device_address"
        private const val PREF_LAST_MAC = "last_mac"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvState = findViewById(R.id.tvState)            // XML’deki id doğru: tvState
        btnConnect = findViewById(R.id.btnConnect)
        btnRealtime = findViewById(R.id.btnRealtime)
        btnGrid = findViewById(R.id.btnGrid)

        // Üstte durum çubuğu: son bilinen MAC yaz
        val lastMac = getLastMac()
        if (!lastMac.isNullOrBlank()) {
            tvState.text = "Bağlandı: $lastMac"
        } else {
            tvState.text = "Hazır"
        }

        // “Bağlan (Bluetooth)”
        btnConnect.setOnClickListener {
            val bt = BluetoothAdapter.getDefaultAdapter()
            if (bt == null) {
                tvState.text = "Bluetooth yok"
                return@setOnClickListener
            }
            if (!bt.isEnabled) {
                startActivityForResult(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE), 0)
                return@setOnClickListener
            }
            // Projede zaten DeviceListActivity.kt olduğunu loglardan görüyoruz.
            // Oradan seçilen cihazın MAC'ini EXTRA_DEVICE_ADDRESS ile geri alacağız.
            val i = Intent(this, DeviceListActivity::class.java)
            startActivityForResult(i, REQ_SELECT_DEVICE)
        }

        // “Gerçek Zamanlı (grafik)”
        btnRealtime.setOnClickListener {
            val mac = getLastMac() // Seçilmiş/adresi kayıtlı cihaz
            val i = Intent(this, RealtimeActivity::class.java)
            // RealtimeActivity içinde nullable okunacak; yoksa SharedPreferences’a bakacak.
            i.putExtra(EXTRA_DEVICE_ADDRESS, mac)
            startActivity(i)
        }

        // “Grid / Harita” (varsa)
        btnGrid.setOnClickListener {
            startActivity(Intent(this, GridActivity::class.java))
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_SELECT_DEVICE && resultCode == Activity.RESULT_OK && data != null) {
            val mac = data.getStringExtra(EXTRA_DEVICE_ADDRESS)
            if (!mac.isNullOrBlank()) {
                saveLastMac(mac)
                tvState.text = "Bağlandı: $mac"
            }
        }
    }

    // ---- küçük yardımcılar ----
    private fun saveLastMac(mac: String) {
        getSharedPreferences("deep3d_prefs", MODE_PRIVATE)
            .edit().putString(PREF_LAST_MAC, mac).apply()
    }

    private fun getLastMac(): String? {
        return getSharedPreferences("deep3d_prefs", MODE_PRIVATE)
            .getString(PREF_LAST_MAC, null)
    }
}
