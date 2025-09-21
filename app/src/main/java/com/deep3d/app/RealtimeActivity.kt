package com.deep3d.app

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import kotlinx.coroutines.*
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

class RealtimeActivity : AppCompatActivity() {

    private lateinit var tvInfo: TextView
    private lateinit var etCmd: EditText
    private lateinit var btnSend: Button
    private lateinit var btnClear: Button
    private lateinit var btnCmdCRLF: Button
    private lateinit var btnCmds: Button
    private lateinit var btnCmdAA55: Button
    private lateinit var btnAutoProbe: Button

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var socket: BluetoothSocket? = null
    private var input: InputStream? = null
    private var output: OutputStream? = null

    private var deviceAddress: String? = null

    // Klasik SPP UUID
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

        deviceAddress = intent.getStringExtra("deviceAddress")
        append("Gerçek zamanlı ekran (cihaz: ${deviceAddress ?: "bilinmiyor"})")

        // Bağlan ve dinlemeyi başlat
        scope.launch { connectAndRead() }

        // Gönder butonu
        btnSend.setOnClickListener {
            val txt = etCmd.text?.toString()?.trim().orEmpty()
            if (txt.isEmpty()) {
                Toast.makeText(this, "Komut boş", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            scope.launch { sendAsciiOrHex(txt) }
        }

        // Temizle
        btnClear.setOnClickListener { runOnUiThread { tvInfo.text = "" } }

        // Hızlı komutlar
        btnCmdCRLF.setOnClickListener { scope.launch { sendRaw(byteArrayOf('\r'.code.toByte(), '\n'.code.toByte()), "CRLF") } }
        btnCmdAA55.setOnClickListener { scope.launch { sendRaw(byteArrayOf(0xAA.toByte(), 0x55.toByte()), "AA55") } }
        btnCmds.setOnClickListener { scope.launch { sendRaw(byteArrayOf(0xAA.toByte(), 0xAA.toByte(), 0x55.toByte(), 0x55.toByte()), "AA AA 55 55") } }
        btnAutoProbe.setOnClickListener { scope.launch { sendAsciiOrHex("PROBE") } }
    }

    private suspend fun connectAndRead() {
        withContext(Dispatchers.IO) {
            val addr = deviceAddress
            if (addr.isNullOrEmpty()) {
                append("HATA: cihaz adresi yok (MainActivity'den gelmedi).")
                return@withContext
            }

            try {
                append("Bağlanıyor…")
                val adapter = BluetoothAdapter.getDefaultAdapter()
                val device: BluetoothDevice = adapter.getRemoteDevice(addr)

                // Bağlantı
                val sock = device.createRfcommSocketToServiceRecord(SPP_UUID)
                adapter.cancelDiscovery()
                sock.connect()

                socket = sock
                input = sock.inputStream
                output = sock.outputStream

                append("Bağlandı. Veri okunuyor…")

                // Sürekli okuma döngüsü (BAYT BAZLI)
                val buf = ByteArray(1024)
                while (isActive && socket?.isConnected == true) {
                    val n = try {
                        input?.read(buf) ?: -1
                    } catch (e: IOException) {
                        -1
                    }
                    if (n <= 0) break

                    // RX edilen baytları HEX olarak yaz
                    val hex = buf.copyOf(n).joinToString(" ") { b -> String.format("%02X", b) }
                    append("RX ($n bayt): $hex")
                }
                append("Bağlantı kapandı / okuma bitti.")
            } catch (e: Exception) {
                append("Bağlantı/okuma hatası: ${e.message}")
            } finally {
                closeQuietly()
            }
        }
    }

    // Kullanıcı "AA55" veya "0xAA 0x55" veya "AA 55" yazarsa tanı ve gönder.
    private suspend fun sendAsciiOrHex(text: String) {
        val hexRegex = Regex("""^(0x)?([0-9A-Fa-f]{2})(\s*(0x)?[0-9A-Fa-f]{2})*$""")
        val data: ByteArray = if (hexRegex.matches(text)) {
            // "AA 55" türü HEX
            text
                .replace("0x", "", ignoreCase = true)
                .trim()
                .split(Regex("\\s+"))
                .filter { it.isNotBlank() }
                .map { it.toInt(16).toByte() }
                .toByteArray()
        } else {
            // Düz ASCII
            text.toByteArray()
        }
        sendRaw(data, text)
    }

    private suspend fun sendRaw(bytes: ByteArray, label: String) {
        withContext(Dispatchers.IO) {
            try {
                val out = output ?: return@withContext append("Gönderilemedi (bağlı değil)")
                out.write(bytes)
                out.flush()
                append("""Gönder: "$label"  (${bytes.size} bayt)""")
            } catch (e: Exception) {
                append("Gönderim hatası: ${e.message}")
            }
        }
    }

    private fun append(line: String) {
        runOnUiThread {
            tvInfo.append((if (tvInfo.text.isNullOrEmpty()) "" else "\n") + line)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
        closeQuietly()
    }

    private fun closeQuietly() {
        try { input?.close() } catch (_: Exception) {}
        try { output?.close() } catch (_: Exception) {}
        try { socket?.close() } catch (_: Exception) {}
        input = null; output = null; socket = null
    }
}
