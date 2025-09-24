package com.deep3d.app

import android.content.Intent
import android.os.Bundle
import android.widget.*
import androidx.activity.ComponentActivity
import java.nio.charset.Charset
import kotlin.concurrent.thread

class RealtimeActivity : ComponentActivity() {

    private lateinit var edtCmd: EditText
    private lateinit var btnSend: Button
    private lateinit var btnClr: Button
    private lateinit var btnCrLf: Button
    private lateinit var btnAA55: Button
    private lateinit var btnAutoProbe: Button
    private lateinit var txtStatus: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_realtime)

        // Layout'ta zaten olan ID'ler
        edtCmd = findViewById(R.id.edtCmd)
        btnSend = findViewById(R.id.btnSend)
        btnClr = findViewById(R.id.btnClr)
        btnCrLf = findViewById(R.id.btnCrLf)
        btnAA55 = findViewById(R.id.btnAA55)
        btnAutoProbe = findViewById(R.id.btnAutoProbe)
        txtStatus = findViewById(R.id.txtStatus)

        ensureConnected()
        startListening()

        btnSend.setOnClickListener {
            val t = edtCmd.text?.toString()?.trim().orEmpty()
            if (t.isNotEmpty()) sendSmart(t)
        }
        btnClr.setOnClickListener {
            edtCmd.setText("")
            addLog("Temizlendi")
        }
        btnCrLf.setOnClickListener {
            sendBytes(byteArrayOf(0x0D, 0x0A))
            addLog("TX: 0D 0A")
        }
        btnAA55.setOnClickListener {
            sendBytes(byteArrayOf(0xAA.toByte(), 0x55.toByte()))
            addLog("TX: AA 55")
        }
        btnAutoProbe.setOnClickListener {
            thread {
                sendBytes(byteArrayOf(0xAA.toByte(), 0x55.toByte(), 0x0D, 0x0A))
                addLog("TX: AA 55 0D 0A")
                Thread.sleep(120)
                sendBytes(byteArrayOf(0xAA.toByte(), 0x55.toByte(), 0x0D, 0x0A))
                addLog("TX: AA 55 0D 0A")
            }
            Toast.makeText(this, "Oto Probe (demo)", Toast.LENGTH_SHORT).show()
        }
    }

    /** Bağlı değilse cihaz listesine döndür */
    private fun ensureConnected() {
        if (!ConnectionManager.isConnected) {
            Toast.makeText(this, "Cihaz bağlı değil – bağlanılıyor…", Toast.LENGTH_SHORT).show()
            startActivity(Intent(this, DeviceListActivity::class.java))
        } else {
            addLog("Bağlı: OK")
            ConnectionManager.socket?.remoteDevice?.address?.let {
                addLog("SPP bağlandı: $it")
            }
        }
    }

    /** RX dinleyici */
    private fun startListening() {
        addLog("RX bekleniyor...")
        val input = ConnectionManager.input ?: return
        thread {
            val buffer = ByteArray(1024)
            try {
                while (true) {
                    val n = input.read(buffer)
                    if (n > 0) {
                        val rx = buffer.copyOf(n)
                        runOnUiThread {
                            addLog("RX($n): " + rx.joinToString(" ") { String.format("%02X", it) })
                        }
                    }
                }
            } catch (e: Exception) {
                runOnUiThread { addLog("Bağlantı kesildi: ${e.message}") }
            }
        }
        addLog("RX dinleyici başlatıldı…")
    }

    /** Akıllı gönderim: HEX patern ise bayt; değilse UTF-8 metin */
    private fun sendSmart(text: String) {
        // Escape dizilerini işle (\r, \n, \t)
        val unescaped = text
            .replace("\\r", "\r")
            .replace("\\n", "\n")
            .replace("\\t", "\t")

        val hex = tryParseHex(unescaped)
        if (hex != null) {
            sendBytes(hex)
            addLog("TX: " + hex.joinToString(" ") { String.format("%02X", it) })
        } else {
            val data = unescaped.toByteArray(Charset.forName("UTF-8"))
            sendBytes(data)
            addLog("TX: $unescaped")
        }
    }

    /** "AA55", "AA 55 0D 0A", "0xAA 0x55" gibi girdiyi baytlara çevirir; uymuyorsa null */
    private fun tryParseHex(s: String): ByteArray? {
        val pattern = Regex("(?i)(0x)?[0-9a-f]{2}")
        val matches = pattern.findAll(s).map { it.value }.toList()
        if (matches.isEmpty()) return null

        // Eğer girilen karakterlerin büyük kısmı hex değilse metin say.
        val hexChars = Regex("(?i)[0-9a-fx\\s]").findAll(s).count()
        if (hexChars < s.filterNot { it.isWhitespace() }.length * 0.7) return null

        return try {
            matches.map { it.replace("0x", "", ignoreCase = true) }
                .map { it.toInt(16).toByte() }
                .toByteArray()
        } catch (_: Exception) {
            null
        }
    }

    private fun sendBytes(data: ByteArray) {
        val out = ConnectionManager.out
        if (out == null) {
            Toast.makeText(this, "Bağlantı yok (yeniden bağlan)", Toast.LENGTH_SHORT).show()
            startActivity(Intent(this, DeviceListActivity::class.java))
            return
        }
        try {
            out.write(data)
            out.flush()
        } catch (e: Exception) {
            Toast.makeText(this, "Yazma hatası: ${e.message}", Toast.LENGTH_SHORT).show()
            ConnectionManager.close()
        }
    }

    private fun addLog(s: String) {
        txtStatus.append("\n$s")
    }
}
