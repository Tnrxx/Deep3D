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
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var status: TextView
    private lateinit var btnConnect: Button
    private lateinit var btnRealtime: Button
    private lateinit var btnGrid: Button

    private val btAdapter: BluetoothAdapter? by lazy { BluetoothAdapter.getDefaultAdapter() }

    // Android 12+ için Bluetooth çalışma zamanı izinleri
    private val requiredPerms by lazy {
        val perms = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            perms += Manifest.permission.BLUETOOTH_SCAN
            perms += Manifest.permission.BLUETOOTH_CONNECT
        }
        perms += Manifest.permission.ACCESS_FINE_LOCATION
        perms.toTypedArray()
    }

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { _ ->
            // izin sonrası sadece bilgi verelim
            Toast.makeText(this, "İzinler güncellendi", Toast.LENGTH_SHORT).show()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        status = findViewById(R.id.txtStatus)
        btnConnect = findViewById(R.id.btnConnect)
        btnRealtime = findViewById(R.id.btnRealtime)
        btnGrid = findViewById(R.id.btnGrid)

        status.text = "Hazır"

        btnConnect.setOnClickListener {
            ensurePerms()
            if (btAdapter == null) {
                toast("Bu cihazda Bluetooth yok")
                return@setOnClickListener
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                != PackageManager.PERMISSION_GRANTED
            ) {
                toast("Bluetooth izni gerekiyor")
                return@setOnClickListener
            }
            // Bluetooth ayarlarına yönlendir (şimdilik)
            startActivity(Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS))
        }

        btnRealtime.setOnClickListener {
            ensurePerms()
            startActivity(Intent(this, RealtimeActivity::class.java))
        }

        btnGrid.setOnClickListener {
            ensurePerms()
            startActivity(Intent(this, GridActivity::class.java))
        }
    }

    private fun ensurePerms() {
        val missing = requiredPerms.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            permissionLauncher.launch(missing.toTypedArray())
        }
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
