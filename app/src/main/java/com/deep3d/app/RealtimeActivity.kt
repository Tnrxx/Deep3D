package com.deep3d.app

import android.bluetooth.*
import android.content.Context
import android.content.pm.PackageManager
import android.os.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import java.io.IOException
import java.util.UUID

class RealtimeActivity : AppCompatActivity() {

    private lateinit var edtCmd: EditText
    private lateinit var btnSend: Button
    private lateinit var btnClear: Button
    private lateinit var btnCrLf: Button
    private lateinit var btnAA55: Button
    private lateinit var btnAutoProbe: Button
    private lateinit var btnCommands: Button

    private var socket: BluetoothSocket? = null
    private val sppUuid: UUID =
        UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_realtime)

        edtCmd = findViewById(R.id.edtCmd)
        btnSend = findViewById(R.id.btnSend)
        btnClear = findViewById(R.id.btnClear)
        btnCrLf = findViewById(R.id.btnCrLf)
        btnAA55 = findViewById(R.id.btnAA55)
        btnAutoProbe = findViewById(R.id.btnAutoProbe)
        btnCommands = findViewById(R.id.btnCommands)

        val address = getSharedPreferences("deep3d", MODE_PRIVATE)
            .getString("bt_address", null)
        if (address == null) {
            toast("Cihaz adresi yok. Ana ekrandan bağlanın.")
        } else {
            connect(address)
        }

        btnSend.setOnClickListener { sendText(edtCmd.text.toString()) }
        btnClear.setOnClickListener { edtCmd.setText("") }
        btnCrLf.setOnClickListener { sendBytes(byteArrayOf(0x0D, 0x0A)) }
        btnAA55.setOnClickListener { sendBytes(byteArrayOf(0xAA.toByte(), 0x55)) }
        btnAutoProbe.setOnClickListener { autoProbe() }
        btnCommands.setOnClickListener {
            edtCmd.setText("AA55")
            toast("AA55 eklendi")
        }
    }

    private fun connect(address: String) {
        val manager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val adapter = manager.adapter ?: run { toast("Bluetooth yok"); return }

        val device = try { adapter.getRemoteDevice(address) }
        catch (_: IllegalArgumentException) { toast("Adres hatalı"); return }

        if (Build.VERSION.SDK_INT >= 31 &&
            ActivityCompat.checkSelfPermission(
                this, android.Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED) {
            toast("BLUETOOTH_CONNECT izni gerekli")
            return
        }

        Thread {
            try {
                val tmp = device.createRfcommSocketToServiceRecord(sppUuid)
                runOnUiThread { toast("Bağlanıyor...") }
                tmp.connect()
                socket = tmp
                runOnUiThread { toast("Bağlandı") }
            } catch (e: IOException) {
                runOnUiThread { toast("Bağlantı hatası: ${e.message}") }
                try { socket?.close() } catch (_: IOException) {}
                socket = null
            }
        }.start()
    }

    private fun sendText(text: String) {
        if (text.isEmpty()) { toast("Komut girin"); return }
        sendBytes(text.toByteArray())
        toast("Gönderildi: $text")
    }

    private fun sendBytes(bytes: ByteArray) {
        val out = socket?.outputStream ?: run { toast("Bağlı değil"); return }
        try { out.write(bytes); out.flush() } catch (_: IOException) {
            toast("Gönderim hatası")
        }
    }

    private fun autoProbe() {
        sendBytes(byteArrayOf(0xAA.toByte(), 0x55, 0x0D, 0x0A))
        toast("Oto Probe (demo)")
    }

    private fun toast(s: String) =
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show()
}
