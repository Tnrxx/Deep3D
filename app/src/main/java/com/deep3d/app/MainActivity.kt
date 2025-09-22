package com.deep3d.app

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

        // Ekranı ilk açarken durum metnini güncelle
        refreshStateLine()

        // 1) Bluetooth bağlantı/cihaz seçimi ekranınıza gider (mevcut akış neyse aynen kalsın)
        btnConnect.setOnClickListener {
            // Sizde hangi activity açılıyorsa onu çağırın; ör: DeviceListActivity
            startActivity(Intent(this, DeviceListActivity::class.java))
        }

        // 2) Gerçek Zamanlı (grafik) ekranına geç
        btnRealtime.setOnClickListener {
            val addr = SavedBtPrefs.getSavedMac(this) ?: ""
            val itRealtime = Intent(this, RealtimeActivity::class.java).apply {
                // RealtimeActivity’de tanımladığımız sabit:
                // const val EXTRA_DEVICE_ADDRESS = "extra_device_address"
                putExtra(RealtimeActivity.EXTRA_DEVICE_ADDRESS, addr)
            }
            startActivity(itRealtime)
        }

        // 3) Grid/Harita (siz hangi ekranı kullanıyorsanız)
        btnGrid.setOnClickListener {
            startActivity(Intent(this, GridActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        // Başka ekranda MAC kaydedildiyse geri dönünce üst yazıyı güncelle
        refreshStateLine()
    }

    private fun refreshStateLine() {
        val mac = SavedBtPrefs.getSavedMac(this)
        tvState.text = if (mac.isNullOrEmpty()) {
            "Hazır"
        } else {
            "Bağlandı: $mac"
        }
    }

    /**
     * Basit SharedPreferences yardımcı sınıfı:
     * Başka bir ekranda/akışta MAC seçildiğinde şu şekilde kaydedin:
     * SavedBtPrefs.saveMac(context, macString)
     */
    object SavedBtPrefs {
        private const val PREFS = "deep3d_prefs"
        private const val KEY_MAC = "saved_mac"

        fun saveMac(ctx: Context, mac: String?) {
            ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_MAC, mac ?: "")
                .apply()
        }

        fun getSavedMac(ctx: Context): String? {
            val s = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_MAC, "")
            return if (s.isNullOrBlank()) null else s
        }
    }
}
