package com.deep3d.app

import android.content.Intent
import android.os.Bundle
import android.widget.*
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
        txtStatus = findViewById(R.id.txtStatus)

        ensureConnected()

        btnSend.setOnClickListener {
            val t = edtCmd.text?.toString()?.trim() ?: ""
            if (t.isNotEmpty()) sendText(t)
        }

        btnClr.setOnClickListener {
            edtCmd.setText("")
            txtStatus.text = ""
            toast("Temizlendi")
        }

        btnCrLf.setOnClickListener {
            sendBytes(byteArrayOf('\r'.code.toByte(), '\n'.code.toByte()))
            toast("CRLF eklendi")
        }

        btnAA55.setOnClickListener {
            sendBytes(byteArrayOf(0xAA.toByte(), 0x55.toByte()))
            toast("Gönderildi: AA55")
        }

        btnAutoProbe.setOnClickListener {
            thread {
                sendBytes(byteArrayOf(0xAA.toByte(), 0x55.toByte()))
                Thread.sleep(100)
                sendBytes(byteArrayOf('\r'.code.toByte(), '\n'.code.toByte()))
            }
            toast("Oto Probe (demo)")
        }
    }

    override fun onResume() {
        super.onResume()
        // Ekran görünür olunca dinlemeyi başlat/yeniden başlat
        ConnectionManager.socket?.let { startListening(it) }
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

    // ---- Gelen veriyi oku ve ekrana bas ----
    private fun startListening(socket: android.bluetooth.BluetoothSocket) {
        thread {
            val buffer = ByteArray(1024)
            try {
                val input = socket.inputStream
                while (true) {
                    val bytes = input.read(buffer)      // veri gelince döner
                    if (bytes > 0) {
                        val copy = buffer.copyOf(bytes)
                        val hex = copy.joinToString(" ") { "%02X".format(it) }
                        runOnUiThread {
                            txtStatus.append("RX: $hex\n")
                        }
                    }
                }
            } catch (e: IOException) {
                runOnUiThread {
                    txtStatus.append("Bağlantı kesildi: ${e.message}\n")
                }
            }
        }
    }

    private fun toast(t: String) =
        Toast.makeText(this, t, Toast.LENGTH_SHORT).show()
}
