package com.deep3d.app

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat

class MainActivity : AppCompatActivity() {

    private lateinit var tvConnected: TextView
    private lateinit var btnConnect: Button
    private lateinit var btnRealtime: Button
    private lateinit var btnGrid: Button

    private val btAdapter: BluetoothAdapter? by lazy { BluetoothAdapter.getDefaultAdapter() }

    private val enableBt =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            // Kullanıcı Bluetooth’u açtıysa hemen cihaz listesine geçebiliriz
            if (btAdapter?.isEnabled == true) showBondedDevices()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvConnected = findViewById(R.id.tvConnected)
        btnConnect  = findViewById(R.id.btnConnect)
        btnRealtime = findViewById(R.id.btnRealtime)
        btnGrid     = findViewById(R.id.btnGrid)

        // En son seçilen MAC'i başlıkta göster
        updateConnectedLabel(Prefs.getLastDevice(this))

        // 1) "Bağlan (Bluetooth)" → Eşleştirilmiş cihazları göster, seçileni kaydet
        btnConnect.setOnClickListener {
            ensureBtEnabled { showBondedDevices() }
        }

        // 2) "Gerçek Zamanlı (grafik)" → MAC'i intent ile RealtimeActivity'e gönder
        btnRealtime.setOnClickListener {
            val mac = Prefs.getLastDevice(this)
            if (mac.isNullOrBlank()) {
                toast("Cihaz adresi yok. Önce Bağlan (Bluetooth) ile cihaz seç.")
                return@setOnClickListener
            }
            val i = Intent(this, RealtimeActivity::class.java)
            i.putExtra("device_address", mac)
            startActivity(i)
        }

        // 3) "Grid / Harita" (varsa aç, yoksa bu kısmı silebilirsin)
        btnGrid.setOnClickListener {
            try {
                startActivity(Intent(this, GridActivity::class.java))
            } catch (_: Exception) {
                toast("Grid/Harita ekranı tanımlı değil.")
            }
        }
    }

    /** Eşleştirilmiş (bonded) cihazları listeler ve seçileni kaydeder */
    private fun showBondedDevices() {
        val adapter = btAdapter ?: run { toast("Bu cihazda Bluetooth yok."); return }
        if (!hasBtPermission()) { requestBtPermission(); return }

        val devices = adapter.bondedDevices?.toList().orEmpty()
        if (devices.isEmpty()) { toast("Eşleştirilmiş cihaz bulunamadı."); return }

        val labels = devices.map { "${it.name ?: "Bilinmeyen"}\n${it.address}" }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("Cihaz seç")
            .setItems(labels) { _, which ->
                val d = devices[which]
                val mac = d.address
                Prefs.saveLastDevice(this, mac)     // <<<< KAYDET
                updateConnectedLabel(mac)           // Ekranda göster
                toast("Seçildi: ${d.name} ($mac)")
            }
            .setNegativeButton("İptal", null)
            .show()
    }

    /** Üst başlığı günceller */
    private fun updateConnectedLabel(mac: String?) {
        tvConnected.text = if (mac.isNullOrBlank()) "Bağlandı: -" else "Bağlandı: $mac"
    }

    /** Bluetooth açık mı? Değilse açtır. */
    private fun ensureBtEnabled(onEnabled: () -> Unit) {
        val adapter = btAdapter ?: run { toast("Bluetooth desteklenmiyor."); return }
        if (!adapter.isEnabled) {
            val intent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            enableBt.launch(intent)
        } else {
            onEnabled()
        }
    }

    /** Android 12+ için BLUETOOTH_CONNECT izni kontrolü */
    private fun hasBtPermission(): Boolean =
        if (Build.VERSION.SDK_INT >= 31)
            ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        else true

    private fun requestBtPermission() {
        if (Build.VERSION.SDK_INT >= 31) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.BLUETOOTH_CONNECT),
                1001
            )
        }
    }

    private fun toast(msg: String) =
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}

/** Küçük SharedPreferences yardımcı sınıfı — MAC'i saklar/okur */
object Prefs {
    private const val KEY = "deep3d"
    private const val LAST_ADDR = "last_device_address"

    fun saveLastDevice(ctx: android.content.Context, mac: String) {
        ctx.getSharedPreferences(KEY, android.content.Context.MODE_PRIVATE)
            .edit().putString(LAST_ADDR, mac.trim()).apply()
    }

    fun getLastDevice(ctx: android.content.Context): String? =
        ctx.getSharedPreferences(KEY, android.content.Context.MODE_PRIVATE)
            .getString(LAST_ADDR, null)
}
