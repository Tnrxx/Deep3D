package com.deep3d.app

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.OutputStream
import java.util.UUID
import java.util.concurrent.Executors

class RealtimeActivity : AppCompatActivity() {

    private lateinit var edtCmd: EditText
    private lateinit var btnSend: Button
    private lateinit var btnClear: Button
    private lateinit var btnCrLf: Button
    private lateinit var btnAA55: Button
    private lateinit var btnAutoProbe: Button
    private lateinit var txtStatus: TextView

    private val executor = Executors.newSingleThreadExecutor()
    private var socket: BluetoothSocket? = null
    private var out: OutputStream? = null

    // SPP UUID (RFCOMM)
    private val SPP_UUID: UUID =
        UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    // EŞLEŞİK CİHAZ ADI / MAC
    private val KNOWN_NAME = "DEEP 3D"   // eşleştirme adınız buysa dokunmayın
    private val KNOWN_MAC: String? = null // isterseniz AA:BB:CC:DD:EE:FF yazın

    private val REQ_BT_PERMS = 1001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_realtime)

        edtCmd = findViewById(R.id.edtCmd)
        btnSend = findViewById(R.id.btnSend)
        btnClear = findViewById(R.id.btnClear)
        btnCrLf = findViewById(R.id.btnCrLf)
        btnAA55 = findViewById(R.id.btnAA55)
        btnAutoProbe = findViewById(R.id.btnAutoProbe)
        txtStatus = findViewById(R.id.txtStatus)

        setButtonsEnabled(false)
        txtStatus.text = "Gerçek zamanlı ekran (cihaz: bekleniyor)"

        // Android 12+ için çalışma zamanı izni
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val need = listOf(
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN
            ).any {
                ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
            }
            if (need) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(
                        Manifest.permission.BLUETOOTH_CONNECT,
                        Manifest.permission.BLUETOOTH_SCAN
                    ),
                    REQ_BT_PERMS
                )
            } else {
                connectIfPaired()
            }
        } else {
            connectIfPaired()
        }

        btnClear.setOnClickListener { edtCmd.setText("") }

        btnSend.setOnClickListener {
            val txt = edtCmd.text.toString().trim()
            if (txt.isEmpty()) return@setOnClickListener
            val bytes = parseAsHexOrAscii(txt)
            write(bytes)
        }

        btnCrLf.setOnClickListener { write("\r\n".toByteArray()) }

        // 0xAA 0x55 + CRLF
        btnAA55.setOnClickListener { write(byteArrayOf(0xAA.toByte(), 0x55.toByte(), 0x0D, 0x0A)) }

        // Oto Probe: cihazınız farklı bir komut istiyorsa alttaki satırı değiştirin
        btnAutoProbe.setOnClickListener {
            // örnek: "PRB" + CRLF
            write("PRB\r\n".toByteArray())
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_BT_PERMS && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
            connectIfPaired()
        } else {
            toast("Bluetooth izinleri verilmedi")
        }
    }

    private fun connectIfPaired() {
        val adapter = BluetoothAdapter.getDefaultAdapter()
        if (adapter == null) {
            txtStatus.text = "Bluetooth yok"
            return
        }
        if (!adapter.isEnabled) {
            txtStatus.text = "Bluetooth kapalı (Ana ekrandan Bağlan ile açın)"
            return
        }

        // Eşleşik cihazlar arasından bul
        val device: BluetoothDevice? = try {
            val bonded = adapter.bondedDevices ?: emptySet()
            bonded.firstOrNull { d ->
                (KNOWN_MAC != null && d.address.equals(KNOWN_MAC, true)) ||
                        d.name.equals(KNOWN_NAME, true)
            }
        } catch (_: SecurityException) {
            null
        }

        if (device == null) {
            txtStatus.text = "Cihaz adresi yok. Ana ekrandan bağlanın veya eşleştirin."
            return
        }

        txtStatus.text = "Bağlanıyor... (${device.name})"
        executor.execute {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                    ContextCompat.checkSelfPermission(
                        this, Manifest.permission.BLUETOOTH_CONNECT
                    ) != PackageManager.PERMISSION_GRANTED
                ) return@execute

                val s = device.createRfcommSocketToServiceRecord(SPP_UUID)
                BluetoothAdapter.getDefaultAdapter()?.cancelDiscovery()
                s.connect()
                socket = s
                out = s.outputStream

                runOnUiThread {
                    txtStatus.text = "Bağlı: ${device.name}"
                    setButtonsEnabled(true)
                    toast("Bluetooth bağlı")
                }
            } catch (e: Exception) {
                runOnUiThread {
                    txtStatus.text = "Bağlantı hatası: ${e.localizedMessage}"
                    setButtonsEnabled(false)
                }
            }
        }
    }

    private fun write(bytes: ByteArray) {
        executor.execute {
            try {
                out?.write(bytes)
                out?.flush()
            } catch (e: Exception) {
                runOnUiThread {
                    toast("Yazma hatası: ${e.localizedMessage}")
                    setButtonsEnabled(false)
                }
            }
        }
    }

    private fun setButtonsEnabled(enabled: Boolean) {
        listOf(btnSend, btnClear, btnCrLf, btnAA55, btnAutoProbe).forEach { it.isEnabled = enabled }
    }

    private fun parseAsHexOrAscii(input: String): ByteArray {
        val hex = input.replace(" ", "").uppercase()
        val isHex = hex.matches(Regex("^[0-9A-F]+$")) && hex.length % 2 == 0
        return if (isHex) {
            ByteArray(hex.length / 2) { i ->
                hex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
            }
        } else {
            // düz metin ise CRLF eklemeyiz; istersen sonradan yaz
            input.toByteArray()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try { out?.close() } catch (_: Exception) {}
        try { socket?.close() } catch (_: Exception) {}
        executor.shutdownNow()
    }

    private fun toast(msg: String) =
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
