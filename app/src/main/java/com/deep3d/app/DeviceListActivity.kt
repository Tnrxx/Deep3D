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
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.util.UUID
import kotlin.concurrent.thread

class DeviceListActivity : AppCompatActivity() {

    companion object {
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
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!hasAllPermissions()) {
            ActivityCompat.requestPermissions(this, needBtPermissions, REQ_BT_PERMS)
        } else autoConnect()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_BT_PERMS) {
            if (hasAllPermissions()) autoConnect()
            else { toast("Bluetooth izinleri verilmedi"); finish() }
        }
    }

    private fun hasAllPermissions(): Boolean =
        needBtPermissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }

    private fun autoConnect() {
        val btMgr = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val adapter: BluetoothAdapter? = btMgr.adapter
        if (adapter == null || !adapter.isEnabled) { toast("Bluetooth kapalı"); finish(); return }

        val bonded: Set<BluetoothDevice> = if (Build.VERSION.SDK_INT >= 31) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                != PackageManager.PERMISSION_GRANTED) { toast("BLUETOOTH_CONNECT izni yok"); finish(); return }
            adapter.bondedDevices
        } else adapter.bondedDevices

        if (bonded.isEmpty()) { toast("Eşleşmiş cihaz yok"); finish(); return }

        val target = bonded.firstOrNull { (it.name ?: "").contains("DEEP", true) } ?: bonded.first()
        toast("Bağlanıyor: ${target.name ?: "?"}")

        thread {
            try {
                ConnectionManager.close()
                val sock = target.createRfcommSocketToServiceRecord(SPP_UUID)
                try { adapter.cancelDiscovery() } catch (_: Exception) {}
                sock.connect()
                ConnectionManager.socket = sock
                runOnUiThread { toast("Bağlantı OK (${target.name})"); setResult(RESULT_OK); finish() }
            } catch (e: Exception) {
                runOnUiThread { toast("Bağlantı başarısız: ${e.message}"); setResult(RESULT_CANCELED); finish() }
            }
        }
    }

    private fun toast(t: String) =
        Toast.makeText(this, t, Toast.LENGTH_LONG).show()
}
