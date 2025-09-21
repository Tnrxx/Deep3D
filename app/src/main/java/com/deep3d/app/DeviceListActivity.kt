package com.deep3d.app

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.*
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import java.io.IOException
import java.util.UUID

class DeviceListActivity : AppCompatActivity() {

    private lateinit var tvStatus: TextView
    private lateinit var lv: ListView
    private val items = ArrayList<String>()
    private val devices = ArrayList<BluetoothDevice>()
    private lateinit var adapter: ArrayAdapter<String>
    private val bt: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()

    // Klasik SPP UUID (cihazın seri/RFCOMM servisi için yaygın)
    private val SPP_UUID: UUID =
        UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    private val enableBt =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { /* no-op */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_device_list)

        tvStatus = findViewById(R.id.tvStatus)
        lv = findViewById(R.id.lvDevices)
        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, items)
        lv.adapter = adapter

        lv.setOnItemClickListener { _, _, pos, _ ->
            val d = devices[pos]
            connectTo(d)
        }

        checkPermissionsAndScan()
    }

    private fun checkPermissionsAndScan() {
        val need = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= 31) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED)
                need += Manifest.permission.BLUETOOTH_SCAN
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED)
                need += Manifest.permission.BLUETOOTH_CONNECT
        } else {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
                need += Manifest.permission.ACCESS_FINE_LOCATION
        }
        if (need.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, need.toTypedArray(), 1001)
        } else {
            startScan()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1001) startScan() else Unit
    }

    @SuppressLint("MissingPermission")
    private fun startScan() {
        if (bt == null) {
            tvStatus.text = "Bluetooth yok"
            return
        }
        if (bt?.isEnabled != true) {
            enableBt.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
        }

        items.clear()
        devices.clear()

        // Eşleşmiş cihazları ekle
        val bonded = try { bt?.bondedDevices ?: emptySet() } catch (_: SecurityException) { emptySet() }
        bonded.forEach { d ->
            devices += d
            items += "Eşleşmiş: ${d.name} (${d.address})"
        }
        adapter.notifyDataSetChanged()

        // Keşif başlat
        try {
            tvStatus.text = "Taranıyor…"
            if (bt?.isDiscovering == true) bt.cancelDiscovery()
            registerReceiver(foundReceiver, IntentFilter(BluetoothDevice.ACTION_FOUND))
            registerReceiver(doneReceiver, IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_FINISHED))
            bt?.startDiscovery()
        } catch (_: SecurityException) {
            tvStatus.text = "İzin gerekli (Bluetooth/Location)"
        }
    }

    private val foundReceiver = object : BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        override fun onReceive(context: Context?, intent: Intent?) {
            if (BluetoothDevice.ACTION_FOUND == intent?.action) {
                val d: BluetoothDevice? = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                if (d != null && devices.none { it.address == d.address }) {
                    devices += d
                    items += "Bulundu: ${d.name ?: "Bilinmiyor"} (${d.address})"
                    adapter.notifyDataSetChanged()
                }
            }
        }
    }

    private val doneReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            tvStatus.text = "Tarama bitti. Listeden seç."
        }
    }

    @SuppressLint("MissingPermission")
    private fun connectTo(d: BluetoothDevice) {
        tvStatus.text = "Bağlanıyor: ${d.name ?: d.address}"
        Thread {
            try {
                if (bt?.isDiscovering == true) bt.cancelDiscovery()
                val socket: BluetoothSocket =
                    if (Build.VERSION.SDK_INT >= 31) {
                        d.createRfcommSocketToServiceRecord(SPP_UUID)
                    } else {
                        d.createRfcommSocketToServiceRecord(SPP_UUID)
                    }
                socket.connect()
                runOnUiThread {
                    tvStatus.text = "Bağlandı: ${d.name ?: d.address}"
                    Toast.makeText(this, "Bağlantı OK", Toast.LENGTH_SHORT).show()
                    setResult(Activity.RESULT_OK, Intent().putExtra("deviceAddress", d.address))
                    finish()
                }
            } catch (e: IOException) {
                runOnUiThread {
                    tvStatus.text = "Bağlanılamadı: ${d.name ?: d.address}"
                    Toast.makeText(this, "Hata: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    override fun onDestroy() {
        super.onDestroy()
        runCatching { unregisterReceiver(foundReceiver) }
        runCatching { unregisterReceiver(doneReceiver) }
    }
}
