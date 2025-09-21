package com.deep3d.app

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import java.io.IOException
import java.nio.charset.Charset
import java.util.UUID
import kotlin.concurrent.thread

class RealtimeActivity : AppCompatActivity() {

    private lateinit var tvInfo: TextView
    private lateinit var spCmd: Spinner
    private lateinit var btnSend: Button
    private lateinit var btnClear: Button

    private val sppUuid: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    private var socket: BluetoothSocket? = null
    @Volatile private var readerRunning = false

    // Denenecek hazır komutlar (bir kısmı binary)
    private val canned = listOf(
        "CRLF (\\r\\n)"              to byteArrayOf(0x0D, 0x0A),
        "'S'"                        to byteArrayOf('S'.code.toByte()),
        "'START\\r\\n'"              to "START\r\n".toByteArray(),
        "'R\\r\\n'"                  to "R\r\n".toByteArray(),
        "0xAA 0x55"                  to byteArrayOf(0xAA.toByte(), 0x55.toByte()),
        "0xA5 0x5A"                  to byteArrayOf(0xA5.toByte(), 0x5A.toByte()),
        "'BEGIN\\n'"                 to "BEGIN\n".toByteArray(),
        "'AT'"                       to "AT".toByteArray(),
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_realtime)

        tvInfo = findViewById(R.id.tvInfo)
        spCmd  = findViewById(R.id.spCmd)
        btnSend = findViewById(R.id.btnSend)
        btnClear = findViewById(R.id.btnClear)

        spCmd.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            canned.map { it.first }
        )

        val addr = intent.getStringExtra("deviceAddress")
        if (addr.isNullOrBlank()) {
            tvInfo.text = "Önce cihaza bağlan."
            return
        }
        tvInfo.text = "Gerçek zamanlı ekran (cihaz: $addr)\nBağlanıyor…"

        btnClear.setOnClickListener {
            tvInfo.text = "Gerçek zamanlı ekran (cihaz: $addr)\n"
        }

        btnSend.setOnClickListener {
            val idx = spCmd.selectedItemPosition
            val payload = canned[idx].second
            sendBytes(payload)
            append("Komut gönderildi (${payload.size} bayt): ${canned[idx].first}")
        }

        connectAndStartReader(addr)
    }

    override fun onDestroy() {
        readerRunning = false
        runCatching { socket?.close() }
        super.onDestroy()
    }

    private fun append(line: String) = runOnUiThread {
        val newText = tvInfo.text.toString() + line + "\n"
        tvInfo.text = if (newText.length > 20000) newText.takeLast(20000) else newText
    }

    @SuppressLint("MissingPermission")
    private fun connectAndStartReader(address: String) {
        val bt = BluetoothAdapter.getDefaultAdapter()
        if (bt == null) {
            append("Bu cihazda Bluetooth yok.")
            return
        }
        if (Build.VERSION.SDK_INT >= 31) {
            val ok = ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
            if (!ok) { append("BLUETOOTH_CONNECT izni gerekli."); return }
        }

        val dev: BluetoothDevice = bt.getRemoteDevice(address)

        thread {
            try {
                runCatching { socket?.close() }
                val s = dev.createRfcommSocketToServiceRecord(sppUuid)
                socket = s
                if (bt.isDiscovering) bt.cancelDiscovery()
                s.connect()
                append("Bağlandı. Veri okunuyor…")

                // Okuyucu
                val ins = s.inputStream
                val buf = ByteArray(512)
                readerRunning = true
                while (readerRunning) {
                    val n = try { ins.read(buf) } catch (_: IOException) { -1 }
                    if (n <= 0) break

                    // HEX + ASCII göster
                    val hex = buildString {
                        for (i in 0 until n) append(String.format("%02X ", buf[i]))
                    }
                    val ascii = buf.copyOfRange(0, n).map {
                        val c = it.toInt() and 0xFF
                        if (c in 32..126) c.toChar() else '.'
                    }.joinToString("")
                    append("RX ${n}B  HEX: $hex | ASCII: $ascii")
                }
                append("Okuma durdu.")
            } catch (e: Exception) {
                append("Hata: ${e.message}")
            }
        }
    }

    private fun sendBytes(bytes: ByteArray) {
        val out = socket?.outputStream ?: return
        runCatching {
            out.write(bytes)
            out.flush()
        }.onFailure { append("Gönderme hatası: ${it.message}") }
    }
}
