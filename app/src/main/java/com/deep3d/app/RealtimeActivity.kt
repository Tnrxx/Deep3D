package com.deep3d.app

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import java.io.IOException
import java.util.UUID
import kotlin.concurrent.thread

class RealtimeActivity : AppCompatActivity() {

    private lateinit var tvInfo: TextView
    private lateinit var btnCmdCRLF: Button
    private lateinit var btnCmdS: Button
    private lateinit var btnCmdAA55: Button
    private lateinit var btnAutoProbe: Button
    private lateinit var btnClear: Button

    private val sppUuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    private var socket: BluetoothSocket? = null
    @Volatile private var readerRunning = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_realtime)

        tvInfo = findViewById(R.id.tvInfo)
        btnCmdCRLF = findViewById(R.id.btnCmdCRLF)
        btnCmdS = findViewById(R.id.btnCmdS)
        btnCmdAA55 = findViewById(R.id.btnCmdAA55)
        btnAutoProbe = findViewById(R.id.btnAutoProbe)
        btnClear = findViewById(R.id.btnClear)

        val addr = intent.getStringExtra("deviceAddress")
        if (addr.isNullOrBlank()) { tvInfo.text = "Önce cihaza bağlan."; return }
        tvInfo.text = "Gerçek zamanlı ekran (cihaz: $addr)\nBağlanıyor…"

        btnClear.setOnClickListener { tvInfo.text = "" }

        btnCmdCRLF.setOnClickListener { send("CRLF", byteArrayOf(0x0D, 0x0A)) }
        btnCmdS.setOnClickListener { send("'S'", byteArrayOf('S'.code.toByte())) }
        btnCmdAA55.setOnClickListener { send("AA55", byteArrayOf(0xAA.toByte(), 0x55.toByte())) }

        btnAutoProbe.setOnClickListener {
            thread {
                val seq = listOf(
                    "CRLF" to byteArrayOf(0x0D, 0x0A),
                    "S" to byteArrayOf('S'.code.toByte()),
                    "START\\r\\n" to "START\r\n".toByteArray(),
                    "R\\r\\n" to "R\r\n".toByteArray(),
                    "AA55" to byteArrayOf(0xAA.toByte(), 0x55.toByte()),
                    "A55A" to byteArrayOf(0xA5.toByte(), 0x5A.toByte()),
                    "AT" to "AT".toByteArray()
                )
                seq.forEach { (name, bytes) ->
                    send(name, bytes)
                    Thread.sleep(1000)
                }
            }
        }

        connectAndStartReader(addr)
    }

    override fun onDestroy() {
        readerRunning = false
        runCatching { socket?.close() }
        super.onDestroy()
    }

    private fun append(line: String) = runOnUiThread {
        tvInfo.append(line + "\n")
        val s = tvInfo.text.toString()
        if (s.length > 20000) tvInfo.text = s.takeLast(20000)
    }

    private fun send(tag: String, bytes: ByteArray) {
        val out = socket?.outputStream
        if (out == null) { append("TX başarısız: soket yok."); return }
        runCatching {
            out.write(bytes)
            out.flush()
            append("TX (${bytes.size}B) $tag")
        }.onFailure { append("Gönderme hatası: ${it.message}") }
    }

    @SuppressLint("MissingPermission")
    private fun connectAndStartReader(address: String) {
        val bt = BluetoothAdapter.getDefaultAdapter() ?: run { append("Bluetooth yok"); return }
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

                val ins = s.inputStream
                val buf = ByteArray(512)
                readerRunning = true
                while (readerRunning) {
                    val n = try { ins.read(buf) } catch (_: IOException) { -1 }
                    if (n <= 0) break
                    val hex = buildString {
                        for (i in 0 until n) append(String.format("%02X ", buf[i]))
                    }
                    val ascii = buf.copyOfRange(0, n).map { b ->
                        val c = b.toInt() and 0xFF
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
}
