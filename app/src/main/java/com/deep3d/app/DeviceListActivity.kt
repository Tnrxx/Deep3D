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
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
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

    /** Klasik SPP (RFCOMM) UUID */
    private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    /** BT açma isteği */
    private val enableBt =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            // Kullanıcı BT'yi açtıysa taramayı başlat
            if (bt?.isEnabled == true) startScan()
        }

    private var receiversRegistered = false

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

    /** Gerekli izinleri iste ve ardından taramayı başlat */
    private fun checkPermissionsAndScan() {
        val needs = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= 31) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN)
                != PackageManager.PERMISSION_GRANTED
            ) needs += Manifest.permission.BLUETOOTH_SCAN

            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                != PackageManager.PERMISSION_GRANTED
            ) needs += Manifest.permission.BLUETOOTH_CONNECT
        } else {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED
            ) needs += Manifest.permission.ACCESS_FINE_LOCATION
        }

        if (needs.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, needs.toTypedArray(), 1001)
        } else {
            startScan()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1001) startScan()
    }

    @SuppressLint("MissingPermission")
    private fun startScan() {
        if (bt == null) {
            tvStatus.text = "Bluetooth bulunamadı"
            return
        }
        if (bt.isEnabled != true) {
            // Kullanıcıyı BT'yi açmaya yönlendir
            enableBt.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
            return
        }

        // Listeyi temizle
        items.clear()
        devices.clear()
        adapter.notifyDataSetChanged()

        // Eşleşmiş cihazları ekle
        val bonded = try {
            bt.bondedDevices ?: emptySet()
        } catch (_: SecurityException) {
            emptySet()
        }

        for (d in bonded) {
            devices += d
            items += "Eşleşmiş: ${d.name ?: "Bilinmiyor"} (${d.address})"
        }
        adapter.notifyDataSetChanged()

        // Keşfi başlat
        try {
            tvStatus.text = "Taranıyor…"
            if (bt.isDiscovering) bt.cancelDiscovery()

            if (!receiversRegistered) {
                registerReceiver(foundReceiver, IntentFilter(BluetoothDevice.ACTION_FOUND))
                registerReceiver(doneReceiver, IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_FINISHED))
                receiversRegistered = true
            }
            bt.startDiscovery()
        } catch (_: SecurityException) {
            tvStatus.text = "İzin gerekli (Bluetooth/Location)"
        }
    }

    /** Bulunan cihazları listeye ekle */
    private val foundReceiver = object : BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == BluetoothDevice.ACTION_FOUND) {
                val d: BluetoothDevice? =
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                if (d != null && devices.none { it.address == d.address }) {
                    devices += d
                    items += "Bulundu: ${d.name ?: "Bilinmiyor"} (${d.address})"
                    adapter.notifyDataSetChanged()
                }
            }
        }
    }

    /** Taramanın bittiğini kullanıcıya bildir */
    private val doneReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            tvStatus.text = "Tarama bitti. Listeden bir cihaz seç."
        }
    }

    /** Seçilen cihaza bağlan */
    @SuppressLint("MissingPermission")
    private fun connectTo(d: BluetoothDevice) {
        tvStatus.text = "Bağlanıyor: ${d.name ?: d.address}"

        Thread {
            try {
                if (bt?.isDiscovering == true) bt.cancelDiscovery()

                val socket: BluetoothSocket =
                    d.createRfcommSocketToServiceRecord(SPP_UUID)

                socket.connect()

                runOnUiThread {
                    tvStatus.text = "Bağlandı: ${d.name ?: d.address}"
                    Toast.makeText(this, "Bağlantı OK", Toast.LENGTH_SHORT).show()
                    // Ana ekrana başarıyı döndür
                    setResult(
                        Activity.RESULT_OK,
                        Intent().putExtra("deviceAddress", d.address)
                    )
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
        if (receiversRegistered) {
            runCatching { unregisterReceiver(foundReceiver) }
            runCatching { unregisterReceiver(doneReceiver) }
            receiversRegistered = false
        }
        try { if (bt?.isDiscovering == true) bt.cancelDiscovery() } catch (_: Exception) {}
    }
}
