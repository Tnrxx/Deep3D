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
import java.io.InputStream
import java.io.OutputStream
import java.util.*
import kotlin.concurrent.thread

class RealtimeActivity : AppCompatActivity() {

    private lateinit var tvInfo: TextView
    private lateinit var etCmd: EditText
    private lateinit var btnSend: Button
    private lateinit var btnClear: Button
    private lateinit var btnCmdCRLF: Button
    private lateinit var btnCmds: Button
    private lateinit var btnCmdAA55: Button
    private lateinit var btnAutoProbe: Button

    private val bt by lazy { BluetoothAdapter.getDefaultAdapter() }

    private var socket: BluetoothSocket? = null
    private var inS: InputStream? = null
    private var outS: OutputStream? = null
    @Volatile private var running = false

    private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_realtime)

        tvInfo = findViewById(R.id.tvInfo)
        etCmd = findViewById(R.id.etCmd)
        btnSend = findViewById(R.id.btnSend)
        btnClear = findViewById(R.id.btnClear)
        btnCmdCRLF = findViewById(R.id.btnCmdCRLF)
        btnCmds = findViewById(R.id.btnCmds)
        btnCmdAA55 = findViewById(R.id.btnCmdAA55)
        btnAutoProbe = findViewById(R.id.btnAutoProbe)

        val mac = getSharedPreferences("deep3d", MODE_PRIVATE).getString("device_mac", null)
        uiAppend("Gerçek zamanlı ekran\nGerçek zamanlı ekran (cihaz: ${mac ?: "-"})")

        if (mac.isNullOrEmpty()) {
            toast("Seçili cihaz yok. Ana ekrandan bir cihaz seç.")
            return
        }
        connectAndRead(mac)

        // --- UI actions ---
        btnClear.setOnClickListener { tvInfo.text = "" }

        btnSend.setOnClickListener {
            val text = etCmd.text.toString().trim()
            if (text.isNotEmpty()) {
                val isHex = text.replace("\\s".toRegex(), "")
                    .matches(Regex("(?i)[0-9a-f]+")) &&
                        text.replace("\\s".toRegex(), "").length % 2 == 0
                if (isHex) sendHexCommandWithCRLF(text) else sendAsciiWithCRLF(text)
            }
        }

        btnCmdCRLF.setOnClickListener { sendBytes(byteArrayOf(0x0D, 0x0A)) }         // \r\n
        btnCmdAA55.setOnClickListener { sendHexCommandWithCRLF("AA55") }            // 0xAA 0x55 + CRLF
        btnCmds.setOnClickListener { sendHexCommandWithCRLF("AA AA 55 55") }        // örnek set
        btnAutoProbe.setOnClickListener { sendAsciiWithCRLF("PROBE") }              // "PROBE\r\n"
    }

    override fun onDestroy() {
        super.onDestroy()
        running = false
        try { inS?.close() } catch (_: Exception) {}
        try { outS?.close() } catch (_: Exception) {}
        try { socket?.close() } catch (_: Exception) {}
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    /** TextView’a yazmayı her zaman UI thread’de yap. */
    private fun uiAppend(line: String) {
        runOnUiThread {
            val t = tvInfo.text.toString()
            tvInfo.text = if (t.isEmpty()) line else "$t\n$line"
        }
    }

    // ---------- CONNECT + READ + HANDSHAKE ----------
    @SuppressLint("MissingPermission")
    private fun connectAndRead(mac: String) {
        if (Build.VERSION.SDK_INT >= 31) {
            val ok = arrayOf(
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN
            ).all {
                ActivityCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
            }
            if (!ok) { toast("Bluetooth izinleri gerekli."); return }
        }

        val device: BluetoothDevice? = try { bt?.getRemoteDevice(mac) } catch (_: IllegalArgumentException) { null }
        if (device == null) { uiAppend("Hata: MAC geçersiz: $mac"); return }

        uiAppend("Bağlanıyor...")
        thread {
            try {
                bt?.cancelDiscovery()
                val sock = device.createInsecureRfcommSocketToServiceRecord(SPP_UUID)
                sock.connect()

                socket = sock
                inS = sock.inputStream
                outS = sock.outputStream

                uiAppend("Bağlandı. Veri okunuyor...")
                running = true

                // --- Handshake denemeleri (CRLF -> PROBE -> AA55) ---
                sendBytes(byteArrayOf(0x0D, 0x0A), logPrefix = "Handshake")
                Thread.sleep(80)
                sendAsciiWithCRLF("PROBE", logPrefix = "Handshake")
                Thread.sleep(80)
                sendHexCommandWithCRLF("AA55", logPrefix = "Handshake")

                // Okuma döngüsü
                readLoopWithAvailable()
            } catch (e: Exception) {
                uiAppend("Bağlantı hatası: ${e.javaClass.simpleName}: ${e.message}")
            }
        }
    }

    // ---------- READ (non-blocking style) ----------
    private fun readLoopWithAvailable() {
        val buf = ByteArray(1024)
        while (running) {
            try {
                val ins = inS ?: break
                val available = ins.available()
                if (available > 0) {
                    val toRead = if (available > buf.size) buf.size else available
                    val n = ins.read(buf, 0, toRead)
                    if (n > 0) {
                        val bytes = buf.copyOf(n)
                        val hex = bytes.joinToString(" ") { "%02X".format(it) }
                        val ascii = bytes.map { b ->
                            val c = b.toInt() and 0xFF
                            if (c in 32..126) c.toChar() else '.'
                        }.joinToString("")
                        uiAppend("RX ($n bayt): $hex    | ASCII: $ascii")
                    }
                } else {
                    Thread.sleep(20)
                }
            } catch (e: Exception) {
                uiAppend("Okuma bitti: ${e.javaClass.simpleName}: ${e.message}")
                // Otomatik yeniden bağlanma denemesi:
                running = false
                try { socket?.close() } catch (_: Exception) {}
                val mac = getSharedPreferences("deep3d", MODE_PRIVATE).getString("device_mac", null)
                if (!mac.isNullOrEmpty()) {
                    uiAppend("Yeniden bağlanmayı deniyor...")
                    connectAndRead(mac)
                }
                break
            }
        }
    }

    // ---------- SEND HELPERS (her zaman CRLF ekle) ----------
    private fun sendAsciiWithCRLF(text: String, logPrefix: String? = null) {
        val data = (text + "\r\n").toByteArray(Charsets.US_ASCII)
        sendBytes(data, logPrefix)
    }

    private fun sendHexCommandWithCRLF(input: String, logPrefix: String? = null) {
        val cleaned = input.replace("\\s".toRegex(), "")
        if (cleaned.isEmpty() || cleaned.length % 2 != 0) {
            uiAppend("Komut hatalı: hex uzunluğu çift olmalı.")
            return
        }
        val out = ByteArray(cleaned.length / 2 + 2) // + CRLF
        try {
            for (i in 0 until cleaned.length step 2) {
                out[i / 2] = cleaned.substring(i, i + 2).toInt(16).toByte()
            }
            out[out.size - 2] = 0x0D
            out[out.size - 1] = 0x0A
            sendBytes(out, logPrefix)
        } catch (e: Exception) {
            uiAppend("Komut parse hatası: ${e.message}")
        }
    }

    private fun sendBytes(data: ByteArray, logPrefix: String? = null) {
        try {
            val os = outS ?: run {
                uiAppend("${logPrefix?.let { "$it: " } ?: ""}Gönderilemedi (bağlı değil). " +
                        data.joinToString(" ") { "%02X".format(it) } + " (${data.size} bayt)")
                return
            }
            os.write(data)
            os.flush()
            uiAppend("${logPrefix?.let { "$it: " } ?: ""}Gönder: " +
                    data.joinToString(" ") { "%02X".format(it) } + "  (${data.size} bayt)")
        } catch (e: Exception) {
            uiAppend("Gönderim hatası: ${e.javaClass.simpleName}: ${e.message}")
        }
    }
}
