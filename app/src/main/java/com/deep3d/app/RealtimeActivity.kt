package com.deep3d.app

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.*
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.Locale
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
    private val sppUuid: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    private var socket: BluetoothSocket? = null
    private var inStream: InputStream? = null
    private var outStream: OutputStream? = null
    private var ioJob: Job? = null

    // Intent’ten gelecek
    private var deviceAddress: String? = null

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

        deviceAddress = intent.getStringExtra("deviceAddress")

        val head = if (deviceAddress.isNullOrBlank()) {
            "Gerçek zamanlı ekran\nGerçek zamanlı ekran (cihaz: -)"
        } else {
            "Gerçek zamanlı ekran\nGerçek zamanlı ekran (cihaz: ${deviceAddress})"
        }
        tvInfo.text = "$head\nBağlanıyor…"

        // ---- Butonlar ----
        btnSend.setOnClickListener {
            val txt = etCmd.text?.toString()?.trim().orEmpty()
            if (txt.isEmpty()) {
                toast("Komut boş")
            } else {
                sendSmart(txt)
            }
        }

        btnClear.setOnClickListener {
            tvInfo.text = head
        }

        btnCmdCRLF.setOnClickListener {
            appendLine("Hızlı: CRLF")
            sendRaw(byteArrayOf(0x0D, 0x0A))
        }

        btnCmdAA55.setOnClickListener {
            appendLine("Hızlı: AA55")
            sendHex("AA55")
        }

        btnCmds.setOnClickListener {
            appendLine("Hızlı: Komutlar")
            // "AA AA 55 55" + CRLF — cihaz bazı örneklerde bunu seviyor
            sendHex("AA AA 55 55")
            sendRaw(byteArrayOf(0x0D, 0x0A))
        }

        btnAutoProbe.setOnClickListener {
            appendLine("Hızlı: Oto Probe")
            // Sırayla birkaç deneme: CRLF, "PROBE", "AA55", "AA AA 55 55" (+CRLF)
            sendRaw(byteArrayOf(0x0D, 0x0A))
            sendAscii("PROBE")
            sendRaw(byteArrayOf(0x0D, 0x0A))
            sendHex("AA55")
            sendHex("AA AA 55 55")
            sendRaw(byteArrayOf(0x0D, 0x0A))
        }
    }

    override fun onStart() {
        super.onStart()
        // Ekrana girince bağlan
        connectAndRead()
    }

    override fun onStop() {
        super.onStop()
        // Ekrandan çıkınca kapat
        closeSocket()
    }

    // ============================================================
    // BT bağlan + okuma (tamamen yeni sürüm)
    // ============================================================
    @SuppressLint("MissingPermission")
    private fun connectAndRead() {
        val mac = deviceAddress
        if (mac.isNullOrBlank()) {
            appendLine("Cihaz adresi yok. Ana ekrandan bağlanın.")
            return
        }

        ioJob?.cancel()
        ioJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                appendLine("Bağlanıyor…")
                val adapter = BluetoothAdapter.getDefaultAdapter()
                if (adapter == null || !adapter.isEnabled) {
                    appendLine("HATA: Bluetooth kapalı.")
                    return@launch
                }

                val dev: BluetoothDevice = adapter.getRemoteDevice(mac)
                // Eski socket varsa kapat
                try { socket?.close() } catch (_: Exception) {}

                // RFCOMM SPP
                val tmp = dev.createRfcommSocketToServiceRecord(sppUuid)
                socket = tmp
                tmp.connect()

                inStream = tmp.inputStream
                outStream = tmp.outputStream
                appendLine("Bağlandı. Veri okunuyor…")

                // --- Handshake denemeleri (zararsız) ---
                // 1) CRLF
                sendRaw(byteArrayOf(0x0D, 0x0A), prefix = "Handshake")
                // 2) "PR" "OBE" yerine doğrudan "PROBE" (ASCII) + CRLF
                sendAscii("PROBE", prefix = "Handshake")
                sendRaw(byteArrayOf(0x0D, 0x0A), prefix = "Handshake")
                // 3) "AA55" (hex)
                sendHex("AA55", prefix = "Handshake")
                // 4) "AA AA 55 55" + CRLF
                sendHex("AA AA 55 55", prefix = "Handshake")
                sendRaw(byteArrayOf(0x0D, 0x0A), prefix = "Handshake")

                // --- Okuma döngüsü ---
                val input = inStream ?: return@launch
                val buf = ByteArray(1024)
                val frame = ArrayList<Byte>()

                while (isActive && socket?.isConnected == true) {
                    val n = try {
                        if (input.available() <= 0) {
                            delay(30)   // çok sıkı döngü yapma
                            continue
                        }
                        input.read(buf)
                    } catch (ioe: IOException) {
                        appendLine("Okuma bitti: IOException: ${ioe.message}")
                        break
                    }

                    if (n > 0) {
                        for (i in 0 until n) {
                            val b = buf[i]
                            frame.add(b)
                            // Satır sonu: LF ya da CR gelirse bir frame olarak bas
                            if (b == 0x0A.toByte() || b == 0x0D.toByte()) {
                                if (frame.isNotEmpty()) {
                                    val data = frame.toByteArray()
                                    appendLine("RX: ${toHex(data)}")
                                    frame.clear()
                                }
                            }
                        }
                        // Satır sonu yoksa ama veri geldi; küçük gecikme sonrası bas
                        if (frame.isNotEmpty()) {
                            delay(40)
                            if (input.available() == 0) {
                                val data = frame.toByteArray()
                                appendLine("RX: ${toHex(data)}")
                                frame.clear()
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                appendLine("Bağlantı hatası: ${e.message}")
            }
        }
    }

    // ============================================================
    // Yazma yardımcıları
    // ============================================================

    private fun sendSmart(text: String) {
        // "AA55" veya "AA 55" gibi hex mi? yoksa düz yazı mı?
        val t = text.trim()
        val maybeHex = t.replace(" ", "")
        if (maybeHex.matches(Regex("(?i)^[0-9a-f]+$")) && maybeHex.length % 2 == 0) {
            sendHex(t)
        } else {
            sendAscii(t)
        }
    }

    private fun sendHex(hex: String, prefix: String? = null) {
        val bytes = hexStringToBytes(hex)
        if (bytes.isEmpty()) {
            toast("Geçersiz hex")
            return
        }
        sendRaw(bytes, shown = "\"${hexNormalize(hex)}\"", prefix = prefix)
    }

    private fun hexNormalize(hex: String): String {
        return hex.uppercase(Locale.ROOT).trim().split(Regex("\\s+")).joinToString(" ")
    }

    private fun sendAscii(s: String, prefix: String? = null) {
        sendRaw(s.toByteArray(), shown = "\"$s\"", prefix = prefix)
    }

    private fun sendRaw(bytes: ByteArray, shown: String? = null, prefix: String? = null) {
        val out = outStream
        if (out == null) {
            appendLine("Gönderilemedi (bağlı değil). ${shown ?: toHex(bytes)}")
            return
        }
        try {
            out.write(bytes)
            out.flush()
            val tag = if (prefix.isNullOrBlank()) "Gönder" else "$prefix: Gönder"
            val display = shown ?: toHex(bytes)
            appendLine("$tag: $display  (${bytes.size} bayt)")
        } catch (e: Exception) {
            appendLine("Yazma hatası: ${e.message}")
        }
    }

    // ============================================================
    // Yardımcılar
    // ============================================================

    private fun toHex(data: ByteArray): String {
        val sb = StringBuilder()
        for (b in data) {
            sb.append(String.format("%02X ", b))
        }
        return sb.toString().trim()
    }

    private fun hexStringToBytes(hex: String): ByteArray {
        val clean = hex.replace("\\s+".toRegex(), "").uppercase(Locale.ROOT)
        if (clean.isEmpty() || clean.length % 2 != 0 || !clean.matches(Regex("^[0-9A-F]+$"))) {
            return byteArrayOf()
        }
        val out = ByteArray(clean.length / 2)
        var i = 0
        var j = 0
        while (i < clean.length) {
            out[j++] = ((clean[i].digitToInt(16) shl 4) + clean[i + 1].digitToInt(16)).toByte()
            i += 2
        }
        return out
    }

    private fun appendLine(s: String) {
        runOnUiThread {
            tvInfo.append("\n$s")
        }
    }

    private fun toast(msg: String) {
        runOnUiThread {
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }
    }

    private fun closeSocket() {
        ioJob?.cancel()
        ioJob = null
        try { inStream?.close() } catch (_: Exception) {}
        try { outStream?.close() } catch (_: Exception) {}
        try { socket?.close() } catch (_: Exception) {}
        inStream = null
        outStream = null
        socket = null
    }
}
