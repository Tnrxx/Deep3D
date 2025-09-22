package com.deep3d.app

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var btnConnect: Button
    private lateinit var btnRealtime: Button
    private lateinit var btnGrid: Button
    private lateinit var txtStatus: TextView

    private val prefs by lazy { getSharedPreferences("deep3d_prefs", Context.MODE_PRIVATE) }
    private val btAdapter: BluetoothAdapter? by lazy { BluetoothAdapter.getDefaultAdapter() }

    private val askEnableBt = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        // BT açıldıysa devam et
        if (btAdapter?.isEnabled == true) {
            pickBondedDevice()
        } else {
            toast("Bluetooth kapalı")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        btnConnect = findViewById(R.id.btnConnect)
        btnRealtime = findViewById(R.id.btnRealtime)
        btnGrid = findViewById(R.id.btnGrid)
        txtStatus = findViewById(R.id.txtStatus)

        updateStatusLabel()

        btnConnect.setOnClickListener { startConnectFlow() }
        btnRealtime.setOnClickListener {
            startActivity(Intent(this, RealtimeActivity::class.java))
        }
        btnGrid.setOnClickListener {
            startActivity(Intent(this, GridActivity::class.java))
        }
    }

    private fun startConnectFlow() {
        if (btAdapter == null) {
            toast("Bu cihaz Bluetooth desteklemiyor")
            return
        }

        if (!ensureBtPermissions()) return

        if (btAdapter?.isEnabled != true) {
            // Bluetooth’u açtır
            val intent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            askEnableBt.launch(intent)
            return
        }

        // BT açık ve izinler var → eşleşmiş cihaz listesi
        pickBondedDevice()
    }

    /** Android 12+ için BLUETOOTH_* izinlerini ve 12- için konum iznini kontrol eder. */
    private fun ensureBtPermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val need = arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT
            ).filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
            if (need.isNotEmpty()) {
                ActivityCompat.requestPermissions(this, need.toTypedArray(), 2001)
                false
            } else true
        } else {
            // Eski cihazlarda eşleştirilmiş cihazı görebilmek için konum izni gerekebilir
            val p = Manifest.permission.ACCESS_FINE_LOCATION
            if (ContextCompat.checkSelfPermission(this, p) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(p), 2002)
                false
            } else true
        }
    }

    /** Eşleşmiş (paired) cihazlardan seçim penceresi aç. */
    @SuppressLint("MissingPermission")
    private fun pickBondedDevice() {
        val bonded = btAdapter?.bondedDevices?.toList().orEmpty()
        if (bonded.isEmpty()) {
            AlertDialog.Builder(this)
                .setTitle("Önce eşleştir")
                .setMessage("Eşleştirilmiş cihaz bulunamadı. Bluetooth ayarlarına gidip cihazla eşleştirin (örn. DEEP 3D).")
                .setPositiveButton("Ayarları Aç") { _, _ ->
                    startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                }
                .setNegativeButton("İptal", null)
                .show()
            return
        }

        val labels = bonded.map { niceName(it) }
        AlertDialog.Builder(this)
            .setTitle("Cihaz seç")
            .setItems(labels.toTypedArray()) { _, which ->
                val dev = bonded[which]
                saveDevice(dev)
            }
            .setNegativeButton("İptal", null)
            .show()
    }

    @SuppressLint("MissingPermission")
    private fun niceName(d: BluetoothDevice): String =
        "${d.name ?: "Bilinmeyen"}  (${d.address})"

    /** Seçilen cihazı kaydet ve statüyü güncelle. */
    @SuppressLint("MissingPermission")
    private fun saveDevice(device: BluetoothDevice) {
        prefs.edit()
            .putString("bt_name", device.name ?: "Cihaz")
            .putString("bt_addr", device.address)
            .apply()
        toast("Seçildi: ${device.name ?: "Cihaz"}")
        updateStatusLabel()
    }

    private fun updateStatusLabel() {
        val name = prefs.getString("bt_name", null)
        val addr = prefs.getString("bt_addr", null)
        txtStatus.text =
            if (addr.isNullOrBlank()) "Hazır\n(bağlı cihaz yok)"
            else "Hazır\nBağlı: $name\n$addr"
    }

    private fun toast(msg: String) =
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    // Kullanıcı izin ekranından dönünce tekrar statüyü güncelle
    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (grantResults.any { it != PackageManager.PERMISSION_GRANTED }) {
            toast("Gerekli izin verilmedi")
            return
        }
        if (requestCode == 2001 || requestCode == 2002) {
            startConnectFlow()
        }
    }
}
