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
    private lateinit var btnHexSend: Button
    private lateinit var txtStatus: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_realtime)

        edtCmd = findViewById(R.id.edtCmd)
        btnSend = findViewById(R.id.btnSend)
        btnClr = findViewById(R.id.btnClr)
        btnCrLf = findViewById(R.id.btnCrLf)
        btnAA55 = findViewById(R.id.btnAA55)
        btnAutoProbe = findViewById(R.id.btnAutoProbe)
        btnHexSend = findViewById(R.id.btnHexSend)     // layout’ta “HEX GÖNDER” butonu
        txtStatus = findViewById(R.id.txtStatus)

        ensureConnected()
        startListening() // RX dinlemeyi başlat

        btnSend.setOnClickListener {
            val t = edtCmd.text?.toString()?.trim() ?: ""
            if (t.isNotEmpty()) {
                sendText(t)
            }
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
            // AA 55 ayrı write (bazı cihazlar kabul eder)
            sendBytes(byteArrayOf(0xAA.toByte(), 0x55.toByte()))
            addLog("TX: AA 55")
        }

        btnHexSend.setOnClickListener {
            // EN NET TEST: AA 55 0D 0A TEK PAKET
            sendAA55_CRLF_onePacket()
        }

        btnAutoProbe.setOnClickListener {
            // Basit demo: tek paket + kısa bekleme + tekrar
            thread {
                sendAA55_CRLF_onePacket()
                Thread.sleep(120)
                sendAA55_CRLF_onePacket()
            }
            Toast.makeText(this, "Oto Probe (demo)", Toast.LENGTH_SHORT).show()
        }
    }

    private fun ensureConnected() {
        if (!ConnectionManager.isConnected) {
            Toast.makeText(this, "Cihaz bağlı değil – bağlanılıyor…", Toast.LENGTH_SHORT).show()
            startActivity(Intent(this, DeviceListActivity::class.java))
        } else {
            addLog("Bağlı: OK")
            // SPP doğrulaması için MAC yaz
            ConnectionManager.socket?.remoteDevice?.address?.let {
                addLog("SPP bağlandı: $it")
            }
        }
    }

    // **** RX DİNLEYİCİ ****
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

    // **** GÖNDERME ****
    private fun sendText(s: String) {
        val data = s.toByteArray(Charset.forName("UTF-8"))
        sendBytes(data)
        addLog("TX: $s")
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

    // Test yardımcıları
    private fun sendAA55_CRLF_onePacket() {
        sendBytes(byteArrayOf(0xAA.toByte(), 0x55.toByte(), 0x0D, 0x0A))
        addLog("TX: AA 55 0D 0A (tek paket)")
    }

    private fun addLog(s: String) {
        txtStatus.append("\n$s")
    }
}
