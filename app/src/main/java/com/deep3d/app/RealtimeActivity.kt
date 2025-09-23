package com.deep3d.app

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
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

    private fun toast(t: String) = Toast.makeText(this, t, Toast.LENGTH_SHORT).show()
}
