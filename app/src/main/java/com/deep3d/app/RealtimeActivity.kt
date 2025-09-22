package com.deep3d.app

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import java.io.InputStream
import java.io.OutputStream
import java.util.*
import kotlin.concurrent.thread

class RealtimeActivity : AppCompatActivity() {

    private lateinit var tvInfo: TextView
    private lateinit var etCmd: EditText
    private lateinit var btnSend: Button
    private lateinit var btnClear: Button
    private lateinit var btnCmdCRLF: Button
    private lateinit var btnCmds: Button
    private lateinit var btnCmdAA55: Button
    private lateinit var btnAutoProbe: Button

    private val bt by lazy { BluetoothAdapter.getDefaultAdapter() }

    private var socket: BluetoothSocket? = null
    private var inS: InputStream? = null
    private var outS: OutputStream? = null
    @Volatile private var running = false

    private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_realtime)

        tvInfo = findViewById(R.id.tvInfo)
        etCmd = findViewById(R.id.etCmd)
        btnSend = findViewById(R.id.btnSend)
        btnClear = findViewById(R.id.btnClear)
        btnCmdCRLF = findViewById(R.id.btnCmdCRLF)
        btnCmds = findViewById(R.id.btnCmds)
        btnCmdAA55 = findViewById(R.id.btnCmdAA55)
        btnAutoProbe = findViewById(R.id.btnAutoProbe)

        val mac = getSharedPreferences("deep3d", MODE_PRIVATE).getString("device_mac", null)
        tvInfo.text = "Gerçek zamanlı ekran\nGerçek zamanlı ekran (cihaz: ${mac ?: "-"})"

        if (mac.isNullOrEmpty()) {
            toast("Seçili cihaz yok. Ana ekrandan bir cihaz seç.")
            return
        }
        connectAndRead(mac)

        // --- UI actions ---
        btnClear.setOnClickListener { tvInfo.text = "" }

        btnSend.setOnClickListener {
            val text = etCmd.text.toString().trim()
            if (text.isNotEmpty()) sendHexCommand(text)
        }

        btnCmdCRLF.setOnClickListener { sendBytes(byteArrayOf(0x0D, 0x0A)) }    // \r\n
        btnCmdAA55.setOnClickListener { sendHexCommand("AA55") }                // 0xAA 0x55
        btnCmds.setOnClickListener { sendHexCommand("AA AA 55 55") }            // örnek set
        btnAutoProbe.setOnClickListener { sendAscii("PROBE") }                  // saf ASCII
    }

    override fun onDestroy() {
        super.onDestroy()
        running = false
        inS?.close()
        outS?.close()
        socket?.close()
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    // ---------- CONNECT + READ ----------
    @SuppressLint("MissingPermission")
    private fun connectAndRead(mac: String) {
        if (Build.VERSION.SDK_INT >= 31) {
            val ok = arrayOf(
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN
            ).all {
                ActivityCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
            }
            if (!ok) {
                toast("Bluetooth izinleri gerekli.")
                return
            }
        }
        val device: BluetoothDevice? = try { bt?.getRemoteDevice(mac) } catch (_: IllegalArgumentException) { null }
        if (device == null) {
            append("Hata: MAC geçersiz: $mac")
            return
        }

        append("Bağlanıyor...")
        thread {
            try {
                // Güvensiz RFCOMM genelde SPP cihazlarında daha uyumlu
                val sock = device.createInsecureRfcommSocketToServiceRecord(SPP_UUID)
                bt?.cancelDiscovery()
                sock.connect()

                socket = sock
                inS = sock.inputStream
                outS = sock.outputStream

                runOnUiThread { append("Bağlandı. Veri okunuyor...") }
                running = true
                readLoop()
            } catch (e: Exception) {
                runOnUiThread { append("Bağlantı hatası: ${e.javaClass.simpleName}: ${e.message}") }
            }
        }
    }

    private fun readLoop() {
        val buffer = ByteArray(1024)
        while (running) {
            try {
                val ins = inS ?: break
                val n = ins.read(buffer)
                if (n > 0) {
                    val bytes = buffer.copyOf(n)
                    runOnUiThread {
                        append("RX (${n} bayt): " + bytes.joinToString(" ") { b -> "%02X".format(b) })
                    }
                }
            } catch (e: Exception) {
                runOnUiThread { append("Okuma bitti: ${e.javaClass.simpleName}: ${e.message}") }
                break
            }
        }
    }

    // ---------- SEND HELPERS ----------
    private fun sendAscii(text: String) {
        sendBytes(text.toByteArray(Charsets.US_ASCII))
    }

    private fun sendHexCommand(input: String) {
        // "AA55" veya "AA AA 55 55" gibi
        val cleaned = input.replace("\\s".toRegex(), "")
        if (cleaned.length % 2 != 0) {
            append("Komut hatalı: hex uzunluğu çift olmalı.")
            return
        }
        val out = ByteArray(cleaned.length / 2)
        try {
            for (i in out.indices) {
                val byteStr = cleaned.substring(i * 2, i * 2 + 2)
                out[i] = byteStr.toInt(16).toByte()
            }
            sendBytes(out)
        } catch (e: Exception) {
            append("Komut parse hatası: ${e.message}")
        }
    }

    private fun sendBytes(data: ByteArray) {
        try {
            val os = outS ?: run {
                append("Gönderilemedi (bağlı değil). ${hexPreview(data)}")
                return
            }
            os.write(data)
            os.flush()
            append("Gönder: ${hexPreview(data)}  (${data.size} bayt)")
        } catch (e: Exception) {
            append("Gönderim hatası: ${e.javaClass.simpleName}: ${e.message}")
        }
    }

    private fun hexPreview(bytes: ByteArray): String =
        bytes.joinToString(" ") { b -> "%02X".format(b) }

    private fun append(line: String) {
        val t = tvInfo.text.toString()
        tvInfo.text = if (t.isEmpty()) line else "$t\n$line"
    }
}
