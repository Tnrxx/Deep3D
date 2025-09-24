package com.deep3d.app

import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import java.io.IOException
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
    private lateinit var txtLog: TextView

    @Volatile private var rxThread: Thread? = null

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
        txtLog = findViewById(R.id.txtLog)

        appendLog("RX bekleniyor...")

        btnSend.setOnClickListener {
            val t = edtCmd.text?.toString()?.trim().orEmpty()
            if (t.isNotEmpty()) {
                sendBytes(t.toByteArray(Charset.forName("UTF-8")))
                appendLog("TX: $t")
            }
        }

        btnClr.setOnClickListener {
            edtCmd.setText("")
            txtLog.text = ""
            appendLog("Temizlendi")
        }

        btnCrLf.setOnClickListener {
            sendBytes(byteArrayOf('\r'.code.toByte(), '\n'.code.toByte()))
            appendLog("TX: 0D 0A")
        }

        btnAA55.setOnClickListener {
            sendBytes(byteArrayOf(0xAA.toByte(), 0x55.toByte()))
            appendLog("TX: AA 55")
        }

        btnHexSend.setOnClickListener {
            val data = parseHex(edtCmd.text?.toString().orEmpty())
            if (data == null) {
                toast("Hex format: AA55  /  AA 55  /  AA,55  /  0xAA 0x55")
            } else {
                sendBytes(data)
                appendLog("TX: " + data.joinToString(" ") { String.format("%02X", it) })
            }
        }

        btnAutoProbe.setOnClickListener {
            thread {
                sendBytes(byteArrayOf(0xAA.toByte(), 0x55.toByte()))
                Thread.sleep(60)
                sendBytes(byteArrayOf('\r'.code.toByte(), '\n'.code.toByte()))
            }
            toast("Oto Probe")
        }
    }

    override fun onResume() {
        super.onResume()
        if (!ConnectionManager.isConnected) {
            toast("Cihaz bağlı değil")
        } else {
            startListening()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        rxThread?.interrupt()
        rxThread = null
    }

    /** ConnectionManager.input ile dinle */
    private fun startListening() {
        if (rxThread?.isAlive == true) return
        val input = ConnectionManager.input ?: run {
            appendLog("RX başlatılamadı: input null")
            return
        }
        appendLog("RX dinleyici başlatıldı...")

        rxThread = thread(name = "rx-thread", start = true) {
            val buffer = ByteArray(1024)
            try {
                while (!Thread.currentThread().isInterrupted) {
                    val n = input.read(buffer)
                    if (n > 0) {
                        val received = buffer.copyOf(n)
                        val hex = received.joinToString(" ") { String.format("%02X", it) }
                        Log.d("Deep3D-RT", "RX $hex")
                        runOnUiThread { appendLog("RX: $hex") }
                    }
                }
            } catch (e: IOException) {
                runOnUiThread { appendLog("RX hata/kapandı: ${e.message}") }
            }
        }
    }

    /** AA55 / AA 55 / AA,55 / 0xAA 0x55 -> ByteArray */
    private fun parseHex(text: String): ByteArray? {
        val clean = text
            .replace(",", " ")
            .replace("0x", "", ignoreCase = true)
            .replace("\\s+".toRegex(), " ")
            .trim()
        if (clean.isEmpty()) return ByteArray(0)

        val out = ArrayList<Byte>()
        for (p in clean.split(" ")) {
            if (p.isBlank()) continue
            val v = try { p.toInt(16) } catch (_: Exception) { return null }
            if (v !in 0..255) return null
            out.add(v.toByte())
        }
        return out.toByteArray()
    }

    private fun sendBytes(data: ByteArray) {
        val out = ConnectionManager.out
        if (out == null) {
            toast("Bağlantı yok")
            return
        }
        try {
            out.write(data)
            out.flush()
        } catch (e: Exception) {
            toast("Yazma hatası: ${e.message}")
        }
    }

    private fun appendLog(s: String) {
        txtLog.append(s + "\n")
    }

    private fun toast(s: String) =
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show()
}
