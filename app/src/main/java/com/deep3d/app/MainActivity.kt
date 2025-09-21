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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvState = findViewById(R.id.tvState)
        btnConnect = findViewById(R.id.btnConnect)
        btnRealtime = findViewById(R.id.btnRealtime)
        btnGrid = findViewById(R.id.btnGrid)

        tvState.text = "Hazır"

        // 1) Bağlan ekranını aç (DeviceListActivity)
        btnConnect.setOnClickListener {
            val i = Intent(this, DeviceListActivity::class.java)
            startActivityForResult(i, 2001)
        }

        // 2) Gerçek zaman ekranını aç (RealtimeActivity)
        btnRealtime.setOnClickListener {
            startActivity(Intent(this, RealtimeActivity::class.java))
        }

        // 3) Grid/Harita şimdilik bilgi amaçlı
        btnGrid.setOnClickListener {
            Toast.makeText(this, "Grid/Harita ekranı henüz ekli değil.", Toast.LENGTH_SHORT).show()
        }

        // Son bağlanılan cihazı etiket olarak göster (varsa)
        val last = getSharedPreferences("app", MODE_PRIVATE).getString("lastDevice", null)
        if (!last.isNullOrEmpty()) {
            tvState.text = "Bağlı (en son): $last"
        }
    }

    @Deprecated("onActivityResult is fine for this simple case")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 2001 && resultCode == Activity.RESULT_OK) {
            val addr = data?.getStringExtra("deviceAddress") ?: return
            tvState.text = "Bağlandı: $addr"
            Toast.makeText(this, "Cihaz bağlandı ($addr)", Toast.LENGTH_SHORT).show()

            // RealtimeActivity'nin kullanabilmesi için sakla
            getSharedPreferences("app", MODE_PRIVATE)
                .edit()
                .putString("lastDevice", addr)
                .apply()
        }
    }
}
