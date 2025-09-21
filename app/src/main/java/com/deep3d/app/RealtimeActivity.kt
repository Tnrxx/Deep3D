package com.deep3d.app

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothSocket
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.*
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

class RealtimeActivity : AppCompatActivity() {

    // UI
    private lateinit var tvInfo: TextView
    private var etCmd: EditText? = null
    private var btnSend: Button? = null
    private var btnClear: Button? = null
    private var btnCmdCRLF: Button? = null
    private var btnCmds: Button? = null
    private var btnCmdAA55: Button? = null
    private var btnAutoProbe: Button? = null

    // BT
    private var deviceAddress: String? = null
    private var socket: BluetoothSocket? = null
    private var input: InputStream? = null
    private var output: OutputStream? = null

    companion object {
        // Standart Seri Port Profili UUID
        val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_realtime)

        // Views
        tvInfo = findViewById(R.id.tvInfo)
        etCmd = findViewById(R.id.etCmd)
        btnSend = findViewById(R.id.btnSend)
        btnClear = findViewById(R.id.btnClear)
        btnCmdCRLF = findViewById(R.id.btnCmdCRLF)
        btnCmds = findViewById(R.id.btnCmds)
        btnCmdAA55 = findViewById(R.id.btnCmdAA55)
        btnAutoProbe = findViewById(R.id.btnAutoProbe)

        // Cihaz adresi MainActivity'den geliyor
        deviceAddress = intent.getStringExtra("deviceAddress")
        append("Gerçek zamanlı ekran")
        append("Gerçek zamanlı ekran (cihaz: ${deviceAddress ?: "-"})")

        // Bağlan & Oku
        lifecycleScope.launch { connectAndRead() }

        // Butonlar
        btnSend?.setOnClickListener {
            val text = etCmd?.text?.toString()?.trim().orEmpty()
            sendTextOrHex(text)
        }

        btnClear?.setOnClickListener {
            tvInfo.text = ""
        }

        btnCmdCRLF?.setOnClickListener {
            sendBytes(byteArrayOf(0x0D, 0x0A), label = "CRLF")
        }

        btnCmdAA55?.setOnClickListener {
            sendBytes(byteArrayOf(0xAA.toByte(), 0x55.toByte()), label = "AA55")
        }

        btnCmds?.setOnClickListener {
            append("Hızlı komutlar")
            append("Gönder tuşu: \"AA AA 55 55\"")
            append("Gönder tuşu: \"AA AA 55 55\"")
            append("Hızlı: Oto Probe")
            append("Hızlı: CRLF")
            append("Hızlı: CRLF")
            append("Hızlı: AA55")
            append("Hızlı: Oto Probe")
        }

        btnAutoProbe?.setOnClickListener {
            lifecycleScope.launch(Dispatchers.IO) {
                // Küçük bir dizi dene
                sendBytes(byteArrayOf(0x0D, 0x0A), label = "CRLF", showOnUi = true) // CRLF
                delay(200)
                sendBytes(byteArrayOf(0xAA.toByte(), 0x55.toByte()), label = "AA55", showOnUi = true)
                delay(200)
                // "AA AA 55 55"
                sendBytes(byteArrayOf(0xAA.toByte(), 0xAA.toByte(), 0x55.toByte(), 0x55.toByte()), label = "AA AA 55 55", showOnUi = true)
                delay(200)
                // "PROBE"
                sendBytes("PROBE".toByteArray(), label = "PROBE", showOnUi = true)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        closeQuietly()
    }

    // ----------------------- BAĞLANTI & OKUMA -----------------------

    private suspend fun connectAndRead() {
        withContext(Dispatchers.IO) {
            val addr = deviceAddress
            if (addr.isNullOrEmpty()) {
                append("HATA: cihaz adresi yok.")
                return@withContext
            }

            val adapter = BluetoothAdapter.getDefaultAdapter()
            val device = try {
                adapter.getRemoteDevice(addr)
            } catch (e: Exception) {
                append("HATA: MAC hatalı: ${e.message}")
                return@withContext
            }

            // Denenecek yöntemler
            val tryList: MutableList<() -> BluetoothSocket> = mutableListOf(
                { device.createRfcommSocketToServiceRecord(SPP_UUID) },                 // 1) Secure SPP
                { device.createInsecureRfcommSocketToServiceRecord(SPP_UUID) },        // 2) Insecure SPP
                {
                    // 3) Reflection ile kanal 1
                    val m = device.javaClass.getMethod("createRfcommSocket", Int::class.javaPrimitiveType)
                    m.invoke(device, 1) as BluetoothSocket
                }
            )
            // 4) Cihaz yayınladığı UUID'den biri varsa ekle
            device.uuids?.firstOrNull()?.uuid?.let { u ->
                tryList.add { device.createRfcommSocketToServiceRecord(u) }
            }

            var sock: BluetoothSocket? = null
            var lastErr: Exception? = null

            for ((i, maker) in tryList.withIndex()) {
                try {
                    append("Bağlanıyor (yöntem ${i + 1}/${tryList.size})…")
                    adapter.cancelDiscovery()
                    val s = maker()
                    s.connect()
                    sock = s
                    append("Bağlandı (yöntem ${i + 1}).")
                    break
                } catch (e: Exception) {
                    lastErr = e
                    append("Bağlanamadı (yöntem ${i + 1}): ${e.message}")
                    try { sock?.close() } catch (_: Exception) {}
                    sock = null
                }
            }

            if (sock == null) {
                append("Tüm SPP denemeleri başarısız: ${lastErr?.message}")
                return@withContext
            }

            socket = sock
            input = sock.inputStream
            output = sock.outputStream

            // Okuma timeout (takılmayı önler)
            try { sock.soTimeout = 3000 } catch (_: Exception) {}

            append("Veri okunuyor…")

            val buf = ByteArray(1024)
            while (isActive && socket?.isConnected == true) {
                // 1) Varsa oku
                val n = try {
                    input?.read(buf) ?: -1
                } catch (e: IOException) {
                    -1
                }

                if (n > 0) {
                    val hex = buf.copyOf(n).joinToString(" ") { b -> String.format("%02X", b) }
                    append("RX ($n bayt): $hex")
                    continue
                }

                // 2) 1 sn sonra mini probe gönder (bazı cihazlar "uyandırma" ister)
                delay(1000)
                try {
                    output?.write(byteArrayOf(0x0D, 0x0A)) // CRLF mini probe
                    output?.flush()
                } catch (_: Exception) {
                    // yazılamıyorsa döngü devam etsin
                }
            }

            append("Bağlantı kapandı / okuma bitti.")
            closeQuietly()
        }
    }

    // ----------------------- GÖNDERME YARDIMCILARI -----------------------

    private fun sendTextOrHex(text: String) {
        if (text.isEmpty()) {
            append("Gönder tuşu: \"\"")
            return
        }
        // Boşluklu HEX gibi görünüyorsa (örn. "AA 55 0D 0A") parse et
        hexStringToBytesOrNull(text)?.let {
            sendBytes(it, label = text)
            return
        }
        // Değilse düz metin gönder
        sendBytes(text.toByteArray(), label = text)
    }

    private fun hexStringToBytesOrNull(s: String): ByteArray? {
        val clean = s.replace("[^0-9A-Fa-f]".toRegex(), "")
        if (clean.isEmpty() || clean.length % 2 != 0) return null
        return try {
            ByteArray(clean.length / 2) { i ->
                clean.substring(i * 2, i * 2 + 2).toInt(16).toByte()
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun sendBytes(bytes: ByteArray, label: String = "", showOnUi: Boolean = true) {
        lifecycleScope.launch(Dispatchers.IO) {
            val out = output
            if (out == null) {
                append("Gönderilemedi: bağlantı yok.")
                return@launch
            }
            try {
                out.write(bytes)
                out.flush()
                if (showOnUi) {
                    append("Gönder: \"$label\"  (${bytes.size} bayt)")
                }
            } catch (e: Exception) {
                append("Gönderim hatası: ${e.message}")
            }
        }
    }

    // ----------------------- GENEL YARDIMCILAR -----------------------

    private fun append(line: String) {
        runOnUiThread {
            val now = tvInfo.text?.toString().orEmpty()
            tvInfo.text = if (now.isEmpty()) line else "$now\n$line"
        }
    }

    private fun closeQuietly() {
        try { input?.close() } catch (_: Exception) {}
        try { output?.close() } catch (_: Exception) {}
        try { socket?.close() } catch (_: Exception) {}
        input = null
        output = null
        socket = null
    }
}
