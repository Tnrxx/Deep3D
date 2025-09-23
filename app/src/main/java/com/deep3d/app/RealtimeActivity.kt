package com.deep3d.app

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import java.io.InputStream
import java.nio.charset.Charset
import kotlin.concurrent.thread

class RealtimeActivity : ComponentActivity() {

    private lateinit var edtCmd: EditText
    private lateinit var btnSend: Button
    private lateinit var btnClr: Button
    private lateinit var btnCrLf: Button
    private lateinit var btnAA55: Button
    private lateinit var btnHexSend: Button
    private lateinit var btnAutoProbe: Button
    private lateinit var txtStatus: TextView

    @Volatile private var rxThreadStarted = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_realtime)

        edtCmd = findViewById(R.id.edtCmd)
        btnSend = findViewById(R.id.btnSend)
        btnClr = findViewById(R.id.btnClr)
        btnCrLf = findViewById(R.id.btnCrLf)
        btnAA55 = findViewById(R.id.btnAA55)
        btnHexSend = findViewById(R.id.btnHexSend)
        btnAutoProbe = findViewById(R.id.btnAutoProbe)
        txtStatus = findViewById(R.id.txtStatus)

        ensureConnected()
        startListeningIfNeeded()

        btnSend.setOnClickListener {
            val t = edtCmd.text?.toString()?.trim().orEmpty()
            if (t.isNotEmpty()) sendText(t)
        }

        btnClr.setOnClickListener {
            edtCmd.setText("")
            append("Temizlendi")
        }

        btnCrLf.setOnClickListener {
            sendBytes(byteArrayOf('\r'.code.toByte(), '\n'.code.toByte()))
            append("TX: 0D 0A")
        }

        btnAA55.setOnClickListener {
            sendBytes(byteArrayOf(0xAA.toByte(), 0x55.toByte()))
            append("TX: AA 55")
        }

        btnHexSend.setOnClickListener {
            val raw = edtCmd.text?.toString().orEmpty()
            val bytes = parseHex(raw)
            if (bytes == null || bytes.isEmpty()) {
                toast("Geçersiz HEX")
            } else {
                sendBytes(bytes)
                append("TX: " + bytes.joinToString(" ") { "%02X".format(it) })
            }
        }

        btnAutoProbe.setOnClickListener {
            // Basit demo: AA55 -> 100 ms -> CRLF
            thread {
                sendBytes(byteArrayOf(0xAA.toByte(), 0x55.toByte()))
                Thread.sleep(100)
                sendBytes(byteArrayOf('\r'.code.toByte(), '\n'.code.toByte()))
            }
            toast("Oto Probe (demo)")
        }
    }

    private fun ensureConnected() {
        if (!ConnectionManager.isConnected) {
            toast("Cihaz bağlı değil – bağlanılıyor…")
            startActivity(Intent(this, DeviceListActivity::class.java))
        } else {
            toast("Bağlı: OK")
        }
    }

    private fun startListeningIfNeeded() {
        if (rxThreadStarted) return
        val sock = ConnectionManager.socket
        if (sock == null) {
            append("RX başlatılamadı (socket yok)")
            return
        }
        startListening(sock.inputStream)
    }

    private fun startListening(input: InputStream) {
        rxThreadStarted = true
        append("RX dinleyici başlatıldı...")
        thread {
            val buffer = ByteArray(1024)
            try {
                while (true) {
                    val n = input.read(buffer)
                    if (n > 0) {
                        val bytes = buffer.copyOf(n)
                        val hex = bytes.joinToString(" ") { "%02X".format(it) }
                        runOnUiThread { append("RX: $hex") }
                    }
                }
            } catch (e: Exception) {
                runOnUiThread { append("Bağlantı kesildi: ${e.message}") }
            } finally {
                rxThreadStarted = false
            }
        }
    }

    private fun sendText(s: String) {
        val data = s.toByteArray(Charset.forName("UTF-8"))
        sendBytes(data)
        append("TX: $s")
    }

    private fun sendBytes(data: ByteArray) {
        val out = ConnectionManager.out
        if (out == null) {
            toast("Bağlantı yok (yeniden bağlan)")
            startActivity(Intent(this, DeviceListActivity::class.java))
            return
        }
        try {
            out.write(data)
            out.flush()
        } catch (e: Exception) {
            toast("Yazma hatası: ${e.message}")
            ConnectionManager.close()
        }
    }

    /** "AA55", "AA 55", "0xAA 0x55", "\xAA\x55", "AA-55", "AA,55" ... hepsini kabul eder */
    private fun parseHex(text: String): ByteArray? {
        val s = text.trim()
        if (s.isEmpty()) return ByteArray(0)

        // 0x.., \x.. veya iki haneli hex yakala
        val tokens = Regex("(?i)(?:0x|\\\\x)?([0-9a-f]{2})")
            .findAll(s)
            .map { it.groupValues[1] }
            .toList()

        val hexPairs = when {
            tokens.isNotEmpty() -> tokens
            s.matches(Regex("(?i)[0-9a-f]+")) && s.length % 2 == 0 -> s.chunked(2)
            else -> emptyList()
        }
        if (hexPairs.isEmpty()) return null

        return hexPairs.map { it.toInt(16).toByte() }.toByteArray()
    }

    private fun append(msg: String) {
        txtStatus.append("\n$msg")
    }

    private fun toast(t: String) =
        Toast.makeText(this, t, Toast.LENGTH_SHORT).show()
}
