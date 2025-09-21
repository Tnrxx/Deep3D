package com.deep3d.app

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

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
    private var socket: BluetoothSocket? = null
    private var inStream: InputStream? = null
    private var outStream: OutputStream? = null
    private var readThread: Thread? = null

    private fun uiLog(line: String) {
        runOnUiThread {
            val cur = tvInfo.text?.toString() ?: ""
            val add = if (cur.isEmpty()) line else "$cur\n$line"
            tvInfo.text = add
        }
    }

    @SuppressLint("MissingPermission")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_realtime)

        // bind UI
        tvInfo = findViewById(R.id.tvInfo)
        etCmd = findViewById(R.id.etCmd)
        btnSend = findViewById(R.id.btnSend)
        btnClear = findViewById(R.id.btnClear)
        btnCmdCRLF = findViewById(R.id.btnCmdCRLF)
        btnCmds = findViewById(R.id.btnCmds)
        btnCmdAA55 = findViewById(R.id.btnCmdAA55)
        btnAutoProbe = findViewById(R.id.btnAutoProbe)

        val addr = intent.getStringExtra("device_address")
        tvInfo.text = "Gerçek zamanlı ekran\nGerçek zamanlı ekran (cihaz: ${addr ?: "—"})"

        // bağlan & oku
        connectAndRead()

        // --- Butonlar ---
        btnSend.setOnClickListener {
            val txt = etCmd.text?.toString()?.trim().orEmpty()
            if (txt.isNotEmpty()) {
                // "AA55" gibi hex verirsen hexe çevir, normal yazı verirsen düz gönder
                val bytes = hexStringOrNull(txt) ?: (txt).toByteArray(Charsets.UTF_8)
                sendBytes(bytes, title = "Gönder")
            }
        }

        btnClear.setOnClickListener {
            tvInfo.text = ""
            etCmd.setText("")
        }

        btnCmdCRLF.setOnClickListener {
            sendBytes(byteArrayOf(0x0D, 0x0A), title = "CRLF")
        }

        btnCmdAA55.setOnClickListener {
            // 0xAA 0x55
            sendBytes(byteArrayOf(0xAA.toByte(), 0x55.toByte()), title = "AA55")
        }

        btnCmds.setOnClickListener {
            // Örnek toplu komut: "AA AA 55 55" (hex, aralıklı)
            val arr = hexWithSpaces("AA AA 55 55")
            sendBytes(arr, title = "AA AA 55 55")
        }

        btnAutoProbe.setOnClickListener {
            // Cihazın cevap vermesini tetiklemek için iki deneme: "PROBE" + CRLF ve tek AA55
            sendBytes("PROBE".toByteArray(Charsets.UTF_8), title = "PROBE")
            sendBytes(byteArrayOf(0x0D, 0x0A), title = "CRLF")
        }
    }

    // ---------- BT BAĞLANTI + OKUMA ----------
    @SuppressLint("MissingPermission")
    private fun connectAndRead() {
        readThread = Thread {
            uiLog("Bağlanıyor...")

            val addr = intent.getStringExtra("device_address")
            if (addr.isNullOrBlank()) {
                uiLog("Cihaz adresi yok.")
                return@Thread
            }

            val adapter: BluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
            val device: BluetoothDevice = adapter.getRemoteDevice(addr)
            val sppUuid: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

            try {
                adapter.cancelDiscovery()
                socket = device.createRfcommSocketToServiceRecord(sppUuid)
                socket?.connect()
                uiLog("Bağlandı. Veri okunuyor...")

                val input = socket!!.inputStream
                val output = socket!!.outputStream
                inStream = input
                outStream = output

                val buf = ByteArray(1024)

                // non-blocking gibi davran: available() yoksa kısa uyku
                while (!isFinishing && socket?.isConnected == true) {
                    val avail = try { input.available() } catch (_: IOException) { 0 }
                    if (avail <= 0) {
                        try { Thread.sleep(50) } catch (_: InterruptedException) {}
                        continue
                    }
                    val n = input.read(buf, 0, minOf(avail, buf.size))
                    if (n > 0) {
                        val chunk = String(buf, 0, n, Charsets.UTF_8)
                        uiLog("RX: " + chunk.replace("\r", "\\r").replace("\n", "\\n"))
                    }
                }
            } catch (e: IOException) {
                uiLog("Hata: ${e.message}")
            } finally {
                try { socket?.close() } catch (_: IOException) {}
            }
        }
        readThread?.start()
    }

    // ---------- GÖNDERME YARDIMCILARI ----------
    private fun sendBytes(bytes: ByteArray, title: String) {
        val os = outStream
        if (os == null) {
            uiLog("Gönderilemedi (bağlı değil): $title")
            return
        }
        try {
            os.write(bytes)
            os.flush()
            uiLog("Gönder: \"$title\"  (${bytes.size} bayt)")
        } catch (e: IOException) {
            uiLog("Gönderim hatası: ${e.message}")
        }
    }

    // "AA55" ya da "aa 55" -> byte[]
    private fun hexStringOrNull(s: String): ByteArray? {
        val clean = s.replace(" ", "")
        if (clean.length % 2 != 0) return null
        val hexChars = "0123456789abcdefABCDEF"
        if (clean.any { it !in hexChars }) return null
        return try {
            ByteArray(clean.length / 2) { i ->
                val hi = Character.digit(clean[i * 2], 16)
                val lo = Character.digit(clean[i * 2 + 1], 16)
                ((hi shl 4) or lo).toByte()
            }
        } catch (_: Exception) { null }
    }

    private fun hexWithSpaces(s: String): ByteArray {
        val parts = s.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        val out = ByteArray(parts.size)
        for ((i, p) in parts.withIndex()) {
            out[i] = p.toInt(16).toByte()
        }
        return out
    }

    // ---------- Yaşam Döngüsü ----------
    override fun onDestroy() {
        super.onDestroy()
        try { inStream?.close() } catch (_: IOException) {}
        try { outStream?.close() } catch (_: IOException) {}
        try { socket?.close() } catch (_: IOException) {}
        readThread?.interrupt()
    }
}
