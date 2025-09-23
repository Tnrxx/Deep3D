package com.deep3d.app

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothSocket
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import java.io.OutputStream
import java.util.*
import kotlin.concurrent.thread

class RealtimeActivity : AppCompatActivity() {

    private lateinit var edtCmd: EditText
    private lateinit var btnSend: Button
    private lateinit var btnClr: Button
    private lateinit var btnCrLf: Button
    private lateinit var btnAA55: Button
    private lateinit var btnAuto: Button
    private lateinit var txtStatus: TextView

    private var socket: BluetoothSocket? = null
    private var out: OutputStream? = null
    private val sppUUID: UUID =
        UUID.fromString("00001101-0000-1000-8000-00805F9B34FB") // SPP

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_realtime)

        txtStatus = findViewById(R.id.txtStatus)
        edtCmd    = findViewById(R.id.edtCmd)
        btnSend   = findViewById(R.id.btnSend)
        btnClr    = findViewById(R.id.btnClear)
        btnCrLf   = findViewById(R.id.btnCrLf)
        btnAA55   = findViewById(R.id.btnAA55)
        btnAuto   = findViewById(R.id.btnAuto)

        setButtonsEnabled(false)

        val addr = getSharedPreferences("deep3d", MODE_PRIVATE)
            .getString("device_address", null)

        if (addr.isNullOrBlank()) {
            txtStatus.text = "Cihaz adresi yok. Ana ekrandan bağlanın."
            toast("Önce ‘Bağlan’ ile cihaz seç")
        } else {
            connect(addr)
        }

        btnSend.setOnClickListener {
            val s = edtCmd.text.toString().trim()
            if (s.isNotEmpty()) {
                sendText(s)
                toast("Gönderildi: $s")
            }
        }
        btnClr.setOnClickListener { edtCmd.setText("") }
        btnCrLf.setOnClickListener {
            sendBytes(byteArrayOf(0x0D, 0x0A))
            toast("CRLF gönderildi")
        }
        btnAA55.setOnClickListener {
            sendBytes(byteArrayOf(0xAA.toByte(), 0x55.toByte()))
            toast("AA55 gönderildi")
        }
        btnAuto.setOnClickListener {
            // basit demo (ör: başlat komutu)
            sendBytes(byteArrayOf(0xAA.toByte(), 0x55.toByte(), 0x01))
            toast("Oto Probe (demo)")
        }
    }

    private fun connect(address: String) {
        setButtonsEnabled(false)
        txtStatus.text = "Bağlanıyor… ($address)"
        val adapter = BluetoothAdapter.getDefaultAdapter()
        val device = try { adapter.getRemoteDevice(address) }
        catch (e: IllegalArgumentException) { null }

        if (device == null) {
            txtStatus.text = "Cihaz bulunamadı. Yeniden seçin."
            return
        }

        thread {
            try {
                if (adapter.isDiscovering) adapter.cancelDiscovery()
                val sock = device.createRfcommSocketToServiceRecord(sppUUID)
                sock.connect()
                socket = sock
                out = sock.outputStream
                runOnUiThread {
                    txtStatus.text = "Bağlı: ${device.name ?: "Cihaz"}"
                    setButtonsEnabled(true)
                }
            } catch (e: Exception) {
                runOnUiThread {
                    txtStatus.text = "Bağlantı hatası: ${e.message}"
                    setButtonsEnabled(false)
                }
                closeSocket()
            }
        }
    }

    private fun setButtonsEnabled(v: Boolean) {
        btnSend.isEnabled = v; btnClr.isEnabled = v
        btnCrLf.isEnabled = v; btnAA55.isEnabled = v; btnAuto.isEnabled = v
    }

    private fun sendText(text: String) = sendBytes((text + "\r\n").toByteArray())
    private fun sendBytes(data: ByteArray) {
        try {
            out?.write(data) ?: toast("Bağlı değil")
        } catch (e: Exception) {
            toast("Gönderme hatası: ${e.message}")
            closeSocket(); setButtonsEnabled(false)
        }
    }

    private fun closeSocket() {
        runCatching { out?.flush() }
        runCatching { socket?.close() }
        out = null; socket = null
    }

    private fun toast(s: String) =
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

    override fun onDestroy() {
        super.onDestroy(); closeSocket()
    }
}
