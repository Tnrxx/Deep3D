package com.deep3d.app

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import java.io.IOException
import java.util.UUID
import kotlin.concurrent.thread

class RealtimeActivity : AppCompatActivity() {

    companion object {
        private const val REQ_BT_PERMS = 1001
        private val SPP_UUID: UUID =
            UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
        private const val PREFS = "bt_prefs"
        private const val KEY_MAC = "BT_MAC"
    }

    private var socket: BluetoothSocket? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Mevcut düzen dosyan aynı kalsın
        setContentView(R.layout.activity_realtime)

        if (needsBtPerms()) {
            ActivityCompat.requestPermissions(this, requiredBtPerms(), REQ_BT_PERMS)
        } else {
            ensureBondedDeviceThenConnect()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_BT_PERMS) {
            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                ensureBondedDeviceThenConnect()
            } else {
                Toast.makeText(this, "Bluetooth izinleri gerekli", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun needsBtPerms(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            requiredBtPerms().any {
                ActivityCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
            }
        } else false
    }

    private fun requiredBtPerms(): Array<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT
            )
        else emptyArray()

    /** Kayıtlı MAC varsa bağlanır; yoksa eşleşmiş cihazlardan seçim ister. */
    private fun ensureBondedDeviceThenConnect() {
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        val savedMac = prefs.getString(KEY_MAC, null)

        if (!savedMac.isNullOrEmpty()) {
            connectTo(savedMac)
            return
        }

        val adapter = BluetoothAdapter.getDefaultAdapter()
        if (adapter == null) {
            Toast.makeText(this, "Bu cihazda Bluetooth yok", Toast.LENGTH_LONG).show()
            return
        }

        val bonded = adapter.bondedDevices?.toList().orEmpty()
        if (bonded.isEmpty()) {
            Toast.makeText(this, "Önce sistem Bluetooth ayarlarından cihazla eşleştirin.", Toast.LENGTH_LONG).show()
            return
        }

        val items = bonded.map { "${it.name} (${it.address})" }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Cihaz seçin")
            .setItems(items) { _, which ->
                val dev = bonded[which]
                getSharedPreferences(PREFS, MODE_PRIVATE)
                    .edit().putString(KEY_MAC, dev.address).apply()
                connectTo(dev.address)
            }
            .setNegativeButton("İptal", null)
            .show()
    }

    /** SPP (RFCOMM) ile bağlanma */
    private fun connectTo(mac: String) {
        val adapter = BluetoothAdapter.getDefaultAdapter()
        val device: BluetoothDevice = try {
            adapter.getRemoteDevice(mac)
        } catch (e: IllegalArgumentException) {
            Toast.makeText(this, "Geçersiz MAC: $mac", Toast.LENGTH_LONG).show()
            return
        }

        thread {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                    ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                    != PackageManager.PERMISSION_GRANTED
                ) {
                    runOnUiThread {
                        Toast.makeText(this, "BLUETOOTH_CONNECT izni yok", Toast.LENGTH_LONG).show()
                    }
                    return@thread
                }

                adapter.cancelDiscovery()
                val sock = device.createRfcommSocketToServiceRecord(SPP_UUID)
                sock.connect()
                socket = sock

                runOnUiThread {
                    Toast.makeText(this, "Bağlandı: ${device.name}", Toast.LENGTH_LONG).show()
                }

                // TODO: Burada inputStream'den veri okuyup ekrana yansıtacağız.
                // şimdilik sadece bağlantıyı doğruluyoruz.

            } catch (ex: IOException) {
                runOnUiThread {
                    Toast.makeText(this, "Bağlantı hatası: ${ex.message}", Toast.LENGTH_LONG).show()
                }
                try { socket?.close() } catch (_: Exception) {}
                socket = null
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try { socket?.close() } catch (_: Exception) {}
    }
}
