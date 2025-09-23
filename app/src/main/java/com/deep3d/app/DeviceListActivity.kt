package com.deep3d.app

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.*
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat

class DeviceListActivity : AppCompatActivity() {

    private lateinit var adapter: BluetoothAdapter
    private lateinit var listView: ListView
    private lateinit var btnScan: Button
    private val found = ArrayList<BluetoothDevice>()
    private lateinit var arrayAdapter: ArrayAdapter<String>

    private val needPerms: Array<String> by lazy {
        val p = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= 31) {
            p += Manifest.permission.BLUETOOTH_SCAN
            p += Manifest.permission.BLUETOOTH_CONNECT
        } else {
            p += Manifest.permission.ACCESS_FINE_LOCATION
        }
        p.toTypedArray()
    }

    private val enableBt =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { _ ->
            // geri dönünce taramayı başlatırız
            startScan()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_device_list)

        adapter = BluetoothAdapter.getDefaultAdapter()
            ?: run { toast("Bluetooth yok"); finish(); return }

        listView = findViewById(R.id.lstDevices)
        btnScan  = findViewById(R.id.btnScan)

        arrayAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, mutableListOf())
        listView.adapter = arrayAdapter

        // Eşleşmiş cihazları göster
        refreshBonded()

        btnScan.setOnClickListener { startScan() }

        listView.setOnItemClickListener { _, _, position, _ ->
            val device = found.getOrNull(position)
                ?: run { toast("Seçim hatası"); return@setOnItemClickListener }

            // Adresi kaydet
            getSharedPreferences("deep3d", MODE_PRIVATE)
                .edit().putString("device_address", device.address).apply()

            toast("Seçildi: ${device.name ?: "Cihaz"}")
            setResult(Activity.RESULT_OK, Intent().putExtra("address", device.address))
            finish() // Ana ekrana dön
        }
    }

    private fun refreshBonded() {
        found.clear()
        arrayAdapter.clear()
        adapter.bondedDevices?.forEach { d ->
            found.add(d)
            arrayAdapter.add("★ ${d.name ?: "Bilinmeyen"}\n${d.address}")
        }
        arrayAdapter.notifyDataSetChanged()
    }

    private fun startScan() {
        // Yetkiler
        val missing = needPerms.filter {
            ActivityCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missing.toTypedArray(), 100)
            return
        }
        if (!adapter.isEnabled) {
            enableBt.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
            return
        }

        toast("Taranıyor…")
        refreshBonded()
        // yeni bulduklarımız
        registerReceiver(receiver, IntentFilter(BluetoothDevice.ACTION_FOUND))
        registerReceiver(receiver, IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_FINISHED))
        if (adapter.isDiscovering) adapter.cancelDiscovery()
        adapter.startDiscovery()
        btnScan.isEnabled = false
        btnScan.text = "Taranıyor…"
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) {
            when (i?.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val d: BluetoothDevice? =
                        i.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    if (d != null && found.none { it.address == d.address }) {
                        found.add(d)
                        arrayAdapter.add("${d.name ?: "Bulunan"}\n${d.address}")
                        arrayAdapter.notifyDataSetChanged()
                    }
                }
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    btnScan.isEnabled = true
                    btnScan.text = "Yeniden Tara"
                    unregisterSafe(this)
                    toast("Tarama bitti")
                }
            }
        }
    }

    private fun unregisterSafe(br: BroadcastReceiver) = runCatching { unregisterReceiver(br) }
    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    override fun onDestroy() {
        super.onDestroy()
        if (adapter.isDiscovering) adapter.cancelDiscovery()
        runCatching { unregisterReceiver(receiver) }
    }
}
