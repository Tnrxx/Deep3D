package com.deep3d.app

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.os.*
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
    private val sppUuid: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    private var socket: BluetoothSocket? = null
    private var input: InputStream? = null
    private var output: OutputStream? = null
    @Volatile private var running = false

    // Oto probe
    @Volatile private var autoProbe = false
    private var autoProbeThread: Thread? = null

    private val main = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_realtime)

        // View refs (ID'ler xml'inle birebir: tvInfo, etCmd, btnSend, btnClear, btnCmdCRLF, btnCmds, btnCmdAA55, btnAutoProbe)
        tvInfo       = findViewById(R.id.tvInfo)
        etCmd        = findViewById(R.id.etCmd)
        btnSend      = findViewById(R.id.btnSend)
        btnClear     = findViewById(R.id.btnClear)
        btnCmdCRLF   = findViewById(R.id.btnCmdCRLF)
        btnCmds      = findViewById(R.id.btnCmds)
        btnCmdAA55   = findViewById(R.id.btnCmdAA55)
        btnAutoProbe = findViewById(R.id.btnAutoProbe)

        btnSend.setOnClickListener { sendCommand(etCmd.text.toString()) }
        btnClear.setOnClickListener { tvInfo.text = headerText() }
        btnCmdCRLF.setOnClickListener { sendHex("0D 0A", "CRLF") }
        btnCmds.setOnClickListener { sendHex("AA AA 55 55", "AA AA 55 55") }
        btnCmdAA55.setOnClickListener { sendHex("AA 55 0D 0A", "AA 55 0D 0A") }
        btnAutoProbe.setOnClickListener { toggleAutoProbe() }

        append("Bağlanıyor...")
        connectAndRead() // otomatik bağlanma
    }

    // Ekranın başlık/metin kısmı
    private fun headerText(): String {
        val addr = currentAddressOrDash()
        return "Gerçek zamanlı ekran\nGerçek zamanlı ekran (cihaz: $addr)\n"
    }

    private fun currentAddressOrDash(): String {
        val fromIntent: String? = intent.getStringExtra("device_address")
        val fromPrefs: String? = getSharedPreferences("deep3d_prefs", MODE_PRIVATE)
            .getString("last_device_address", null)
        return fromIntent ?: fromPrefs ?: "-"
    }

    private fun append(line: String) {
        tvInfo.append(line + "\n")
    }

    private fun setAutoProbeButton() {
        btnAutoProbe.text = if (autoProbe) "Oto Probe (AÇIK)" else "Oto Probe"
    }

    private fun toggleAutoProbe() {
        autoProbe = !autoProbe
        setAutoProbeButton()

        if (autoProbe) {
            autoProbeThread?.interrupt()
            autoProbeThread = Thread {
                try {
                    while (autoProbe && socket?.isConnected == true) {
                        sendAsciiInternal("PROBE", label = "PROBE", fromAuto = true)
                        Thread.sleep(300)
                    }
                } catch (_: InterruptedException) { /* no-op */ }
            }.also { it.start() }
        } else {
            autoProbeThread?.interrupt()
        }
    }

    /** ---- BAĞLAN & OKU ---- */
    private fun connectAndRead() {
        // Adresi iki yerden dene; ikisi de boşsa bağlanma
        val address: String? = intent.getStringExtra("device_address")
            ?: getSharedPreferences("deep3d_prefs", MODE_PRIVATE)
                .getString("last_device_address", null)

        tvInfo.text = headerText()

        if (address.isNullOrBlank()) {
            append("Cihaz adresi yok. Ana ekrandan bağlanın.")
            return
        }

        // Başlıkta adres görünsün
        tvInfo.text = "Gerçek zamanlı ekran\nGerçek zamanlı ekran (cihaz: $address)\nBağlanıyor...\n"

        Thread {
            val adapter = BluetoothAdapter.getDefaultAdapter()
            try {
                val device: BluetoothDevice = adapter.getRemoteDevice(address)
                val s = device.createRfcommSocketToServiceRecord(sppUuid)
                adapter.cancelDiscovery()
                s.connect()

                socket = s
                input = s.inputStream
                output = s.outputStream

                main.post {
                    append("Bağlandı. Veri okunuyor...")
                    // Basit handshake – senin cihazın bu sırayı seviyor gibi
                    sendHex("0D 0A", "Handshake")
                    sendAsciiInternal("PROBE", label = "Handshake", fromAuto = true)
                    sendHex("AA 55 0D 0A", "Handshake")
                }

                running = true
                val buf = ByteArray(1024)
                while (running) {
                    val n = input?.read(buf) ?: -1
                    if (n <= 0) break
                    val got = buf.copyOf(n)
                    val hex = bytesToHex(got)
                    main.post { append("Alındı ($n bayt): $hex") }
                }
            } catch (e: IOException) {
                main.post { append("Bağlantı hatası: ${e.message}") }
            } finally {
                running = false
                try { input?.close() } catch (_: Exception) {}
                try { output?.close() } catch (_: Exception) {}
                try { socket?.close() } catch (_: Exception) {}
            }
        }.start()
    }

    /** ---- GÖNDERME ---- */
    private fun ensureConnected(): Boolean {
        val ok = socket?.isConnected == true && output != null
        if (!ok) append("Gönderilmedi (bağlı değil).")
        return ok
    }

    private fun sendCommand(raw: String) {
        val t = raw.trim()
        if (t.isEmpty()) return
        when (t.uppercase()) {
            "CRLF" -> sendHex("0D 0A", "CRLF")
            "AA55" -> sendHex("AA 55 0D 0A", "AA55")
            "PROBE" -> sendAsciiInternal("PROBE", label = "PROBE")
            else -> {
                if (t.matches(Regex("^[0-9A-Fa-f\\s]+$"))) {
                    // "AA 55 0D 0A" gibi hex yazdıysa
                    sendHex(t, t)
                } else {
                    // normal metni ASCII gönder
                    sendAsciiInternal(t, label = t)
                }
            }
        }
    }

    private fun sendHex(hex: String, label: String = hex) {
        if (!ensureConnected()) return
        val bytes = parseHex(hex)
        try {
            output!!.write(bytes)
            output!!.flush()
            append("Gönder: $label  (${bytes.size} bayt)")
        } catch (e: IOException) {
            append("Yazma hatası: ${e.message}")
        }
    }

    private fun sendAsciiInternal(text: String, label: String = text, fromAuto: Boolean = false) {
        if (!ensureConnected()) return
        try {
            val b = text.toByteArray(Charsets.US_ASCII)
            output!!.write(b)
            output!!.flush()
            append(if (fromAuto) "$label: Gönder: \"$text\"" else "Gönder: \"$text\"")
        } catch (e: IOException) {
            append("Yazma hatası: ${e.message}")
        }
    }

    /** ---- Yardımcılar ---- */
    private fun parseHex(s: String): ByteArray {
        val clean = s.replace("[^0-9A-Fa-f]".toRegex(), "")
        val out = ByteArray(clean.length / 2)
        var i = 0
        while (i < clean.length) {
            out[i / 2] = clean.substring(i, i + 2).toInt(16).toByte()
            i += 2
        }
        return out
    }

    private fun bytesToHex(b: ByteArray): String {
        val sb = StringBuilder()
        for (x in b) sb.append(String.format("%02X ", x))
        return sb.toString().trim()
    }

    override fun onDestroy() {
        super.onDestroy()
        running = false
        autoProbe = false
        autoProbeThread?.interrupt()
        try { input?.close() } catch (_: Exception) {}
        try { output?.close() } catch (_: Exception) {}
        try { socket?.close() } catch (_: Exception) {}
    }
}
