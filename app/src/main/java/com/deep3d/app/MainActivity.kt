package com.deep3d.app

import android.bluetooth.BluetoothDevice
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import android.provider.Settings

class MainActivity : AppCompatActivity() {

    private lateinit var tvState: TextView
    private lateinit var btnConnect: Button
    private lateinit var btnRealtime: Button
    private lateinit var btnGrid: Button

    /** Bağlı cihazın MAC adresi burada tutulur */
    private var connectedDeviceAddress: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvState = findViewById(R.id.tvState)
        btnConnect = findViewById(R.id.btnConnect)
        btnRealtime = findViewById(R.id.btnRealtime)
        btnGrid = findViewById(R.id.btnGrid)

        tvState.text = "Hazır"

        // ◾ Bluetooth'a bağlanma butonu (kendi bağlanma akışın varsa onu kullan; yoksa ayarları açar)
        btnConnect.setOnClickListener {
            // KENDİ bağlanma metodun varsa burada çağır:
            // startYourOwnConnectFlow()

            // Şimdilik sistem Bluetooth ayarlarını açalım:
            startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
            Toast.makeText(
                this,
                "Bluetooth ayarlarını açtım. Cihaza bağlandıktan sonra uygulamaya dön.",
                Toast.LENGTH_SHORT
            ).show()
        }

        // ◾ Gerçek zamanlı ekran
        btnRealtime.setOnClickListener {
            val addr = connectedDeviceAddress
            if (addr.isNullOrBlank()) {
                Toast.makeText(this, "Önce cihaza bağlan.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val i = Intent(this, RealtimeActivity::class.java)
            i.putExtra("device_address", addr)
            startActivity(i)
        }

        // ◾ Grid/Harita (şimdilik placeholder)
        btnGrid.setOnClickListener {
            Toast.makeText(this, "Grid/Harita ekranı henüz hazır değil.", Toast.LENGTH_SHORT).show()
        }
    }

    // ======= BUNLARI KENDİ BAĞLANMA KODUNDAN ÇAĞIR =======

    /** Cihaza bağlandığın anda bu fonksiyonu çağır (BluetoothDevice nesnen varsa) */
    fun setConnectedDevice(device: BluetoothDevice?) {
        if (device == null) {
            connectedDeviceAddress = null
            tvState.text = "Hazır"
        } else {
            connectedDeviceAddress = device.address
            tvState.text = "Bağlandı: ${device.address}"
        }
    }

    /** Sadece MAC adresin varsa bunu çağır: */
    fun setConnectedAddress(address: String?) {
        connectedDeviceAddress = address
        tvState.text = if (address.isNullOrBlank()) "Hazır" else "Bağlandı: $address"
    }
}
