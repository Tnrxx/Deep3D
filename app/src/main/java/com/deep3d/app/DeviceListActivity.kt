package com.deep3d.app

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.app.ActivityCompat
import java.util.UUID
import kotlin.concurrent.thread

class DeviceListActivity : ComponentActivity() {

    companion object {
        // Klasik SPP UUID (seri port)
        private val SPP_UUID: UUID =
            UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
        private const val REQ_BT_PERMS = 1001
    }

    private val needBtPermissions: Array<String> by lazy {
        if (Build.VERSION.SDK_INT >= 31) {
            arrayOf(
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN
            )
        } else {
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION // bazı cihazlarda tarama için şart
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // İzinler
        if (!hasAllPermissions()) {
            ActivityCompat.requestPermissions(this, needBtPermissions, REQ_BT_PERMS)
        } else {
            autoConnect()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_BT_PERMS) {
            if (hasAllPermissions()) autoConnect()
            else {
                Toast.makeText(this, "Bluetooth izinleri verilmedi", Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }

    private fun hasAllPermissions(): Boolean {
        return needBtPermissions.all {
            ActivityCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun autoConnect() {
        val btMgr = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val adapter: BluetoothAdapter? = btMgr.adapter
        if (adapter == null || !adapter.isEnabled) {
            Toast.makeText(this, "Bluetooth kapalı", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        val bonded = if (Build.VERSION.SDK_INT >= 31) {
            if (ActivityCompat.checkSelfPermission(
                    this, Manifest.permission.BLUETOOTH_CONNECT
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                Toast.makeText(this, "BLUETOOTH_CONNECT izni yok", Toast.LENGTH_LONG).show()
                finish()
                return
            }
            adapter.bondedDevices
        } else {
            adapter.bondedDevices
        }

        if (bonded.isNullOrEmpty()) {
            Toast.makeText(this, "Eşleşmiş cihaz bulunamadı", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        // Öncelik: adı "DEEP" geçen cihaz
        val target: BluetoothDevice? =
            bonded.firstOrNull { (it.name ?: "").contains("DEEP", ignoreCase = true) }
                ?: bonded.first() // yoksa ilk eşleşilmişi dene

        Toast.makeText(this, "Bağlanıyor: ${target?.name ?: "?"}", Toast.LENGTH_SHORT).show()

        thread {
            try {
                // Eski bir soket varsa kapat
                ConnectionManager.close()

                // RFCOMM SPP
                val sock = target!!.createRfcommSocketToServiceRecord(SPP_UUID)

                // discovery yavaşlatmasın
                try { adapter.cancelDiscovery() } catch (_: Exception) {}

                sock.connect()  // >>> bağlan
                ConnectionManager.socket = sock

                runOnUiThread {
                    Toast.makeText(this, "Bağlantı OK (${target.name})", Toast.LENGTH_LONG).show()
                    setResult(RESULT_OK)
                    finish() // ana ekrana dön
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(
                        this,
                        "Bağlantı başarısız: ${e.message ?: ""}\nCihaz açık/eşleşmiş mi?",
                        Toast.LENGTH_LONG
                    ).show()
                    setResult(RESULT_CANCELED)
                    finish()
                }
            }
        }
    }
}
