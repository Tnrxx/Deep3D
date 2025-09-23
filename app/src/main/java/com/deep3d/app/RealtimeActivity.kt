package com.deep3d.app

import android.content.Intent
import android.os.Bundle
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
    private lateinit var btnAutoProbe: Button
    private lateinit var txtRx: TextView

    @Volatile private var readerRunning = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_realtime)

        edtCmd = findViewById(R.id.edtCmd)
        btnSend = findViewById(R.id.btnSend)
        btnClr = findViewById(R.id.btnClr)
        btnCrLf = findViewById(R.id.btnCrLf)
        btnAA55 = findViewById(R.id.btnAA55)
        btnAutoProbe = findViewById(R.id.btnAutoProbe)
        txtRx = findViewById(R.id.txtRx)

        ensureConnected()

        btnSend.setOnClickListener {
            val t = edtCmd.text?.toString()?.trim() ?: ""
            if (t.isNotEmpty()) {
                // ASCII gönder (cihaz “AA55” metni bekliyorsa bunu kullan)
                sendText(t)
            }
        }

        btnClr.setOnClickListener {
            edtCmd.setText("")
            toast("Temizlendi")
        }

        btnCrLf.setOnClickListener {
            // \r\n
            sendBytes(byteArrayOf('\r'.code.toByte(), '\n'.code.toByte()))
            appendRx("TX: 0D 0A")
            toast("CRLF eklendi")
        }

        btnAA55.setOnClickListener {
            // İkili 0xAA 0x55 gönder (cihaz ham bayt bekliyorsa bunu kullan)
            val bytes = byteArrayOf(0xAA.toByte(), 0x55.toByte())
            sendBytes(bytes)
            appendRx("TX: AA 55")
            toast("Gönderildi: AA55")
        }

        btnAutoProbe.setOnClickListener {
            // Basit demo: AA55 ardından CRLF
            thread {
                val aa55 = byteArrayOf(0xAA.toByte(), 0x55.toByte())
                sendBytes(aa55)
                runOnUiThread { appendRx("TX: AA 55") }
                Thread.sleep(120)
                val crlf = byteArrayOf('\r'.code.toByte(), '\n'.code.toByte())
                sendBytes(crlf)
                runOnUiThread { appendRx("TX: 0D 0A") }
            }
            toast("Oto Probe (demo)")
        }
    }

    override fun onResume() {
        super.onResume()
        // Bağlıysa RX dinleyiciyi başlat
        ConnectionManager.socket?.let { sock ->
            startListening()
        } ?: run {
            ensureConnected()
        }
    }

    override fun onPause() {
        super.onPause()
        readerRunning = false
    }

    private fun ensureConnected() {
        if (!ConnectionManager.isConnected) {
            toast("Cihaz bağlı değil – bağlanılıyor…")
            startActivity(Intent(this, DeviceListActivity::class.java))
        } else {
            toast("Bağlı: OK")
        }
    }

    private fun sendText(s: String) {
        val data = s.toByteArray(Charset.forName("UTF-8"))
        sendBytes(data)
        appendRx("TX: $s")
        toast("Gönderildi: $s")
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

    /** RX tarafı: gelen veriyi hexdump + ASCII olarak gösterir */
    private fun startListening() {
        if (readerRunning) return
        readerRunning = true
        appendRx("RX dinleyici başlatıldı…")

        thread(name = "Deep3D-RX") {
            val sock = ConnectionManager.socket
            if (sock == null) {
                runOnUiThread { appendRx("RX: soket yok") }
                readerRunning = false
                return@thread
            }

            val input = sock.inputStream
            val buf = ByteArray(1024)

            try {
                while (readerRunning) {
                    val n = input.read(buf) // veri gelene kadar bloklar
                    if (n > 0) {
                        val bytes = buf.copyOf(n)
                        val hex = bytes.joinToString(" ") { String.format("%02X", it) }
                        val ascii = bytes.map { b ->
                            val c = (b.toInt() and 0xFF)
                            if (c in 32..126) c.toChar() else '.'
                        }.joinToString("")
                        runOnUiThread {
                            appendRx("RX: $hex    |$ascii|")
                        }
                    }
                }
            } catch (io: IOException) {
                runOnUiThread { appendRx("RX kapandı: ${io.message}") }
            }
        }
    }

    private fun appendRx(line: String) {
        val cur = txtRx.text?.toString() ?: ""
        val next = if (cur.isEmpty()) line else "$cur\n$line"
        txtRx.text = next
    }

    private fun toast(t: String) = Toast.makeText(this, t, Toast.LENGTH_SHORT).show()
}
