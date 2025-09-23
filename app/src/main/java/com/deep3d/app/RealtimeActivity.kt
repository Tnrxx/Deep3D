package com.deep3d.app

import android.bluetooth.BluetoothSocket
import android.content.Intent
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
    private lateinit var btnAutoProbe: Button
    private lateinit var txtRx: TextView

    private var listenThread: Thread? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_realtime)

        edtCmd      = findViewById(R.id.edtCmd)
        btnSend     = findViewById(R.id.btnSend)
        btnClr      = findViewById(R.id.btnClr)
        btnCrLf     = findViewById(R.id.btnCrLf)
        btnAA55     = findViewById(R.id.btnAA55)
        btnAutoProbe= findViewById(R.id.btnAutoProbe)
        txtRx       = findViewById(R.id.txtRx)

        ensureConnectedAndListen()

        btnSend.setOnClickListener {
            val t = edtCmd.text?.toString()?.trim().orEmpty()
            if (t.isNotEmpty()) sendText(t)
        }

        btnClr.setOnClickListener {
            edtCmd.setText("")
            txtRx.text = ""
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
                Thread.sleep(80)
                sendBytes(byteArrayOf('\r'.code.toByte(), '\n'.code.toByte()))
            }
            toast("Oto Probe (demo)")
        }
    }

    override fun onResume() {
        super.onResume()
        ensureConnectedAndListen()
    }

    override fun onDestroy() {
        super.onDestroy()
        listenThread?.interrupt()
        listenThread = null
    }

    private fun ensureConnectedAndListen() {
        if (!ConnectionManager.isConnected || ConnectionManager.socket == null) {
            toast("Cihaz bağlı değil – bağlanılıyor…")
            startActivity(Intent(this, DeviceListActivity::class.java))
            return
        }
        toast("Bağlı: OK")
        startListening(ConnectionManager.socket!!)
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

    /** CİHAZDAN GELEN BAYTLARI DİNLER + EKRANDA GÖSTERİR */
    private fun startListening(socket: BluetoothSocket) {
        // Aynı anda iki dinleyici çalışmasın
        if (listenThread?.isAlive == true) return

        listenThread = thread(start = true) {
            val buffer = ByteArray(1024)
            try {
                val input = socket.inputStream
                while (!Thread.currentThread().isInterrupted) {
                    val bytes = input.read(buffer) // bloklar
                    if (bytes > 0) {
                        val received = buffer.copyOf(bytes)

                        // Logcat’e yaz
                        Log.d("Deep3D-RT", "RX (${bytes}B): " +
                                received.joinToString(" ") { String.format("%02X", it) })

                        // Ekrana yaz
                        runOnUiThread {
                            val hex = received.joinToString(" ") { String.format("%02X", it) }
                            val prev = txtRx.text?.toString().orEmpty()
                            txtRx.text = if (prev.isEmpty()) "RX: $hex" else "$prev\nRX: $hex"
                        }
                    }
                }
            } catch (e: IOException) {
                runOnUiThread {
                    val prev = txtRx.text?.toString().orEmpty()
                    txtRx.text = if (prev.isEmpty())
                        "Bağlantı kesildi: ${e.message}" else "$prev\nBağlantı kesildi: ${e.message}"
                }
            }
        }
    }

    private fun toast(t: String) =
        Toast.makeText(this, t, Toast.LENGTH_SHORT).show()
}
