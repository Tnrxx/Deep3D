package com.deep3d.app

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import kotlinx.coroutines.*
import java.io.IOException
import java.nio.charset.Charset
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
    private var ioJob: Job? = null
    private var autoProbeJob: Job? = null
    private var isConnected = false
        set(v) {
            field = v
            runOnUiThread {
                setButtonsEnabled(v)
            }
        }

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_realtime)

        // bind
        tvInfo = findViewById(R.id.tvInfo)
        etCmd = findViewById(R.id.etCmd)
        btnSend = findViewById(R.id.btnSend)
        btnClear = findViewById(R.id.btnClear)
        btnCmdCRLF = findViewById(R.id.btnCmdCRLF)
        btnCmds = findViewById(R.id.btnCmds)
        btnCmdAA55 = findViewById(R.id.btnCmdAA55)
        btnAutoProbe = findViewById(R.id.btnAutoProbe)

        setButtonsEnabled(false)

        // hedef adres: intent -> prefs
        val addrFromIntent = intent.getStringExtra("device_address")
        val prefs = getSharedPreferences("deep3d", MODE_PRIVATE)
        val address = (addrFromIntent ?: prefs.getString("last_device_address", null))?.trim()

        if (address.isNullOrEmpty()) {
            appendLine("Bağlanıyor...")
            appendLine("Cihaz adresi yok. Ana ekrandan bağlanın.")
            return
        }

        // Ekran başlığında adresi göster
        appendLine("Gerçek zamanlı ekran")
        appendLine("Gerçek zamanlı ekran (cihaz: $address)")
        appendLine("Bağlanıyor...")

        // bağlan
        connectAndRead(address)

        // UI clickler
        btnSend.setOnClickListener {
            val txt = etCmd.text.toString()
            if (txt.isNotBlank()) sendAscii("$txt\r\n")
        }
        btnClear.setOnClickListener { tvInfo.text = "Gerçek zamanlı ekran\nGerçek zamanlı ekran (cihaz: ${address})" }

        btnCmdCRLF.setOnClickListener { sendRaw(byteArrayOf(0x0D, 0x0A)) }
        btnCmdAA55.setOnClickListener { sendAscii("AA55\r\n") }

        btnCmds.setOnClickListener {
            // örnek toplu dizisi
            listOf(
                byteArrayOf(0x0D, 0x0A),
                "PROBE\r\n".toByteArray(Charset.forName("US-ASCII")),
                "AA55\r\n".toByteArray(Charset.forName("US-ASCII"))
            ).forEach { sendRaw(it) }
        }

        btnAutoProbe.setOnClickListener {
            if (autoProbeJob == null) {
                btnAutoProbe.text = "Oto Probe (AÇIK)"
                autoProbeJob = appScope.launch {
                    while (isActive) {
                        if (isConnected) sendAscii("PROBE\r\n") else appendLine("Gönderilmedi (bağlı değil). \"PROBE\"")
                        delay(500)
                    }
                }
            } else {
                btnAutoProbe.text = "Oto Probe"
                autoProbeJob?.cancel()
                autoProbeJob = null
            }
        }
    }

    private fun setButtonsEnabled(enabled: Boolean) {
        btnSend.isEnabled = enabled
        btnCmdCRLF.isEnabled = enabled
        btnCmds.isEnabled = enabled
        btnCmdAA55.isEnabled = enabled
        btnAutoProbe.isEnabled = true        // toggle her zaman basılabilir; bağlı değilse log yazar
    }

    private fun appendLine(s: String) {
        runOnUiThread { tvInfo.append("\n$s") }
    }

    private fun sendAscii(s: String) {
        sendRaw(s.toByteArray(Charset.forName("US-ASCII")))
    }

    private fun sendRaw(bytes: ByteArray) {
        val os = socket?.outputStream
        if (os == null || !isConnected) {
            appendLine("Gönderilmedi (bağlı değil). ${pretty(bytes)}")
            return
        }
        try {
            os.write(bytes)
            os.flush()
            appendLine("Gönder: ${pretty(bytes)}  (${bytes.size} bayt)")
        } catch (e: IOException) {
            appendLine("Yazma hatası: ${e.message}")
            isConnected = false
        }
    }

    private fun pretty(bytes: ByteArray): String {
        // ASCII yazılabilir ise ASCII göster, yoksa HEX.
        val printable = bytes.all { it in 0x20..0x7E || it == 0x0D.toByte() || it == 0x0A.toByte() }
        return if (printable) {
            val text = String(bytes, Charset.forName("US-ASCII")).replace("\r", "\\r").replace("\n", "\\n")
            "\"$text\""
        } else {
            bytes.joinToString(" ") { String.format("%02X", it) }
        }
    }

    @SuppressLint("MissingPermission")
    private fun connectAndRead(address: String) {
        ioJob?.cancel()
        ioJob = appScope.launch {
            val adapter = BluetoothAdapter.getDefaultAdapter()
            if (adapter == null || !adapter.isEnabled) {
                appendLine("Bluetooth kapalı.")
                return@launch
            }
            val dev: BluetoothDevice = try {
                adapter.getRemoteDevice(address)
            } catch (e: IllegalArgumentException) {
                appendLine("Geçersiz adres: $address")
                return@launch
            }

            // daha temiz bir soket oluştur
            try {
                adapter.cancelDiscovery()
            } catch (_: Exception) {}

            val sock = try {
                dev.createRfcommSocketToServiceRecord(sppUuid)
            } catch (e: IOException) {
                appendLine("Socket oluşturulamadı: ${e.message}")
                return@launch
            }

            try {
                sock.connect()
                socket = sock
                isConnected = true
                appendLine("Bağlandı. Veri okunuyor...")

                // handshake (bazı cihazlar bunu bekliyor)
                sendRaw(byteArrayOf(0x0D, 0x0A))                   // CRLF
                sendAscii("PROBE\r\n")                              // “PROBE”
                sendAscii("AA 55 0D 0A".replace(" ", "").chunked(2).map { it.toInt(16).toByte() }.toByteArray().let {
                    // Yukarıdaki satır sadece AA 55 0D 0A hex’ini üretmek içindi
                    // fakat kullanıcıda loglama okunaklı olsun diye ayrıca ASCII de yollayalım:
                    // Aslında cihaz hex bekliyorsa yukarıdaki it kullanılırdı; bir çok cihaz SPP’de ASCII bekler.
                    // Basit olsun: "AA55\r\n"
                    null
                })
                sendAscii("AA55\r\n")

                // okuma döngüsü
                val istream = sock.inputStream
                val buf = ByteArray(1024)
                while (isActive && isConnected) {
                    val n = try {
                        istream.read(buf)
                    } catch (e: IOException) {
                        appendLine("Okuma bitti: ${e.message}")
                        break
                    }
                    if (n <= 0) {
                        appendLine("Okuma bitti: read=$n")
                        break
                    }
                    val data = buf.copyOf(n)
                    // Gelen veriyi hem HEX hem ASCII özetle
                    val hex = data.joinToString(" ") { String.format("%02X", it) }
                    val ascii = data.map {
                        val b = it.toInt() and 0xFF
                        if (b in 32..126) b.toChar() else '.'
                    }.joinToString("")
                    appendLine("Geldi ($n): $hex  |  '$ascii'")
                }
            } catch (e: IOException) {
                appendLine("Bağlanamadı: ${e.message}")
            } finally {
                isConnected = false
                try { socket?.close() } catch (_: Exception) {}
                socket = null
                appendLine("Bağlantı kapandı.")
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        autoProbeJob?.cancel()
        ioJob?.cancel()
        try { socket?.close() } catch (_: Exception) {}
        socket = null
    }
}
