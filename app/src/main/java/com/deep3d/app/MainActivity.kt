package com.deep3d.app

import android.app.AlertDialog
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Context
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

    private val btAdapter: BluetoothAdapter? by lazy { BluetoothAdapter.getDefaultAdapter() }

    companion object {
        const val EXTRA_ADDR = "extra_device_addr"
        private const val PREFS = "deep3d_prefs"
        private const val KEY_ADDR = "last_addr"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvState = findViewById(R.id.tvState)
        btnConnect = findViewById(R.id.btnConnect)
        btnRealtime = findViewById(R.id.btnRealtime)
        btnGrid = findViewById(R.id.btnGrid)

        // Son kayıtlı adresi göster
        val last = getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_ADDR, null)
        tvState.text = if (last.isNullOrBlank()) "Hazır" else "Bağlandı: $last"

        btnConnect.setOnClickListener { showPicker() }

        btnRealtime.setOnClickListener {
            val addr = currentAddr()
            if (addr.isNullOrBlank()) {
                Toast.makeText(this, "Önce cihaza bağlan.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val i = Intent(this, RealtimeActivity::class.java)
            i.putExtra(EXTRA_ADDR, addr)
            startActivity(i)
        }

        btnGrid.setOnClickListener {
            Toast.makeText(this, "Grid/Harita ekranı henüz ekli değil.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun currentAddr(): String? =
        getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_ADDR, null)

    private fun showPicker() {
        val adapter = btAdapter
        if (adapter == null) {
            Toast.makeText(this, "Bu cihaz Bluetooth desteklemiyor.", Toast.LENGTH_LONG).show()
            return
        }
        if (!adapter.isEnabled) {
            Toast.makeText(this, "Lütfen Bluetooth'u açın.", Toast.LENGTH_LONG).show()
            return
        }
        val bonded = adapter.bondedDevices?.toList().orEmpty()
        if (bonded.isEmpty()) {
            Toast.makeText(this, "Eşleşmiş cihaz yok.", Toast.LENGTH_LONG).show()
            return
        }

        val labels = bonded.map { "${it.name}\n${it.address}" }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Cihaz seç")
            .setItems(labels) { _, which ->
                val dev: BluetoothDevice = bonded[which]
                getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .edit().putString(KEY_ADDR, dev.address).apply()
                tvState.text = "Bağlandı: ${dev.address}"
                Toast.makeText(this, "Seçildi: ${dev.name}", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("İptal", null)
            .show()
    }
}
