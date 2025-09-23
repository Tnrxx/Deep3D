package com.deep3d.app

import android.bluetooth.BluetoothSocket
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
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

    // Aynı anda birden fazla dinleyici başlatmamak için
    @Volatile private var listeningStarted = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_realtime)

        edtCmd = findViewById(R.id.edtCmd)
        btnSend = findViewById(R.id.btnSend)
        btnClr = findViewById(R.id.btnClr)
        btnCrLf = findViewById(R.id.btnCrLf)
        btnAA55 = findViewById(R.id.btnAA55)
        btnAutoProbe = findViewById(R.id.btnAutoProbe)

        ensureConnected()

        btnSend.setOnClickListener {
            val t = edtCmd.text?.toString()?.trim() ?: ""
            if (t.isNotEmpty()) sendText(t)
        }

        btnClr.setOnClickListener {
            edtCmd.setText("")
            toast("Temizlendi")
        }

        btnCrLf.setOnClickListener {
            sendBytes(byteArrayOf('\r'.code.toByte(), '\n'.code.toByte()))
            toast("CRLF eklendi")
        }

        btnAA55.setOnClickListener {
            // AA55 iki byte hex komut
            sendBytes(byteArrayOf(0xAA.toByte(), 0x55.toByte()))
            toast("Gönderildi: AA55")
        }

        btnAutoProbe.setOnClickListener {
            // Basit bir demo akışı: AA55 + CRLF
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

            // Eğer soket erişimin varsa dinlemeyi başlat
            // (ConnectionManager.socket yoksa sorun değil; sadece dinleyici başlamaz)
            try {
                val sockField = ConnectionManager::class.java.getDeclaredField("socket")
                sockField.isAccessible = true
                val sock = sockField.get(null) as? BluetoothSocket
                if (sock != null) startListening(sock)
            } catch (_: Throwable) {
                // socket alanı yoksa sessizce geç
            }
        }
    }

    private fun sendText(s: String) {
        val data = s.toByteArray(Charset.forName("UTF-8"))
        sendBytes(data)
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

    // === GELEN VERİ DİNLEYİCİSİ ===
    private fun startListening(socket: BluetoothSocket) {
        if (listeningStarted) return
        listeningStarted = true

        Thread {
            val tag = "Deep3D-RT"
            val buffer = ByteArray(1024)

            try {
                val input = socket.inputStream
                while (true) {
                    val bytes = input.read(buffer)
                    if (bytes > 0) {
                        val received = buffer.copyOf(bytes)

                        // HEX dizgesi
                        val hex = received.joinToString(" ") { String.format("%02X", it) }

                        // Logcat'e dök
                        Log.d(tag, "RX [$bytes]: $hex")

                        // Ekrana kısa bildirim (ilk 8 byte'ı gösterelim ki rahatsız etmesin)
                        val preview = if (received.size > 8) received.copyOf(8) else received
                        val previewHex = preview.joinToString(" ") { String.format("%02X", it) }
                        runOnUiThread {
                            toast("Cevap: $previewHex" + if (bytes > 8) " …" else "")
                        }
                    }
                }
            } catch (e: IOException) {
                runOnUiThread { toast("Bağlantı kesildi: ${e.message}") }
                Log.e("Deep3D-RT", "Dinleme hatası", e)
                listeningStarted = false
            }
        }.start()
    }

    private fun toast(t: String) =
        Toast.makeText(this, t, Toast.LENGTH_SHORT).show()
}
