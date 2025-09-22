package com.deep3d.app

import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

class RealtimeActivity : AppCompatActivity() {

    // UI
    private lateinit var tvInfo: TextView
    private lateinit var etCmd: EditText
    private lateinit var btnSend: Button
    private lateinit var btnClear: Button
    private lateinit var btnCmdCRLF: Button
    private lateinit var btnCmds: Button
    private lateinit var btnCmdAA55: Button
    private lateinit var btnAutoProbe: Button

    // BT
    private val sppUuid: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    private var socket: BluetoothSocket? = null
    private var inS: InputStream? = null
    private var outS: OutputStream? = null
    private val reading = AtomicBoolean(false)
    private var autoProbeOn = AtomicBoolean(false)
    private var deviceAddress: String? = null

    // ---- Activity ----
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_realtime)

        tvInfo       = findViewById(R.id.tvInfo)
        etCmd        = findViewById(R.id.etCmd)
        btnSend      = findViewById(R.id.btnSend)
        btnClear     = findViewById(R.id.btnClear)
        btnCmdCRLF   = findViewById(R.id.btnCmdCRLF)
        btnCmds      = findViewById(R.id.btnCmds)
        btnCmdAA55   = findViewById(R.id.btnCmdAA55)
        btnAutoProbe = findViewById(R.id.btnAutoProbe)

        // Adres: Intent -> yoksa SharedPreferences
        deviceAddress = intent.getStringExtra("BT_ADDR")
        if (deviceAddress.isNullOrBlank() || deviceAddress == "-") {
            deviceAddress = getSharedPreferences("deep3d_prefs", Context.MODE_PRIVATE)
                .getString("last_device_address", null)
        }
        append("Gerçek zamanlı ekran")
        append("Gerçek zamanlı ekran (cihaz: ${deviceAddress ?: "-"})")

        wireUi()
    }

    override fun onStart() {
        super.onStart()
        connectAndRead()
    }

    override fun onStop() {
        super.onStop()
        stopReading()
        closeSocket()
    }

    // ---- UI wiring ----
    private fun wireUi() {
        btnSend.setOnClickListener {
            val txt = etCmd.text.toString().trim()
            if (txt.isEmpty()) return@setOnClickListener
            sendAscii(txt)
        }
        btnClear.setOnClickListener { tvInfo.text = "" }

        btnCmdCRLF.setOnClickListener { sendCRLF() }
        btnCmdAA55.setOnClickListener { sendHex("AA55") }
        btnCmds.setOnClickListener { sendCommandSet() }

        btnAutoProbe.setOnClickListener {
            val newState = !autoProbeOn.get()
            autoProbeOn.set(newState)
            btnAutoProbe.text = if (newState) "Oto Probe (AÇIK)" else "Oto Probe"
            if (newState) startAutoProbe()    // 500 ms’de bir "PROBE\r\n"
        }
    }

    // ---- Connection + Reader ----
    @SuppressLint("MissingPermission")
    private fun connectAndRead() {
        if (socket?.isConnected == true) return

        append("Bağlanıyor...")
        val addr = deviceAddress
        if (addr.isNullOrBlank()) {
            append("Cihaz adresi yok. Ana ekrandan bağlanın.")
            return
        }

        Thread {
            try {
                val adapter = BluetoothAdapter.getDefaultAdapter()
                if (adapter == null) {
                    append("Bluetooth yok.")
                    return@Thread
                }
                if (adapter.isDiscovering) adapter.cancelDiscovery()

                val dev: BluetoothDevice = adapter.getRemoteDevice(addr)
                // createRfcommSocketToServiceRecord → SPP
                val tmp = dev.createRfcommSocketToServiceRecord(sppUuid)
                tmp.connect()
                socket = tmp
                inS = tmp.inputStream
                outS = tmp.outputStream

                append("Bağlandı. Veri okunuyor...")

                // El sıkışma – APK’lerde tipik denenen diziler
                // 1) CRLF
                sendCRLF()
                // 2) "PROBE\r\n"
                sendAscii("PROBE")
                // 3) "AA55\r\n"
                sendHex("AA55")
                sendCRLF()

                startReader()
            } catch (t: Throwable) {
                append("Bağlantı hatası: ${t.javaClass.simpleName}: ${t.message}")
                closeSocket()
            }
        }.start()
    }

    private fun startReader() {
        if (reading.getAndSet(true)) return

        Thread {
            val buf = ByteArray(1024)
            while (reading.get()) {
                try {
                    val ins = inS ?: break
                    val n = ins.read(buf)
                    if (n <= 0) {
                        append("Okuma bitti: read=$n")
                        break
                    }
                    val data = buf.copyOfRange(0, n)
                    append("GELEN ($n bayt): ${toHex(data)}")
                } catch (t: Throwable) {
                    append("Okuma hata: ${t.javaClass.simpleName}: ${t.message}")
                    break
                }
            }
            reading.set(false)
        }.start()
    }

    private fun stopReading() {
        reading.set(false)
        autoProbeOn.set(false)
    }

    private fun closeSocket() {
        try { inS?.close() } catch (_: Throwable) {}
        try { outS?.close() } catch (_: Throwable) {}
        try { socket?.close() } catch (_: Throwable) {}
        inS = null; outS = null; socket = null
    }

    // ---- Send helpers ----
    private fun sendCRLF() = sendBytes(byteArrayOf(0x0D, 0x0A))
    private fun startAutoProbe() {
        Thread {
            while (autoProbeOn.get()) {
                sendAscii("PROBE")
                try { Thread.sleep(500) } catch (_: Throwable) {}
            }
        }.start()
    }

    private fun sendCommandSet() {
        // APK’de denenen birkaç kalıp arka arkaya
        sendCRLF()
        sendAscii("PROBE")
        sendHex("AA55")
        sendCRLF()
    }

    private fun sendAscii(text: String) {
        // "TEXT\r\n" gönder
        val payload = text.encodeToByteArray() + byteArrayOf(0x0D, 0x0A)
        sendBytes(payload, shownAs = "\"$text\"")
    }

    private fun sendHex(hexNoSpaces: String) {
        // "AA55" → [0xAA, 0x55] + CRLF
        val hex = hexNoSpaces.replace(" ", "").uppercase()
        if (hex.length % 2 != 0) {
            append("HEX hata: uzunluk çift olmalı")
            return
        }
        val bytes = ByteArray(hex.length / 2) { i ->
            hex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
        // Birçok cihaz CRLF bekliyor
        val payload = bytes + byteArrayOf(0x0D, 0x0A)
        sendBytes(payload, shownAs = hex.chunked(2).joinToString(" "))
    }

    private fun sendBytes(bytes: ByteArray, shownAs: String? = null) {
        try {
            val outs = outS ?: run {
                append("Gönderilmedi (bağlı değil). ${shownAs ?: toHex(bytes)}")
                return
            }
            outs.write(bytes)
            outs.flush()
            val shown = shownAs ?: toHex(bytes)
            append("Gönder: $shown  (${bytes.size} bayt)")
        } catch (t: Throwable) {
            append("Gönderim hata: ${t.javaClass.simpleName}: ${t.message}")
        }
    }

    // ---- Utils ----
    private fun toHex(b: ByteArray): String =
        b.joinToString(" ") { String.format("%02X", it) }

    private fun append(msg: String) {
        runOnUiThread {
            tvInfo.append(if (tvInfo.text.isNullOrEmpty()) msg else "\n$msg")
        }
    }
}
