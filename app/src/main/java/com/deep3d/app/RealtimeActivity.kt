package com.deep3d.app

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import kotlin.concurrent.thread

class RealtimeActivity : AppCompatActivity() {

    companion object {
        private const val PREFS_NAME = "deep3d_prefs"
        private const val KEY_LAST_MAC = "last_mac"
        const val EXTRA_ADDRESS = "bt_addr"
        private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }

    // UI
    private lateinit var tvInfo: TextView
    private lateinit var etCmd: EditText
    private lateinit var btnSend: Button
    private lateinit var btnClear: Button
    private lateinit var btnCmdCRLF: Button
    private lateinit var btnCmds: Button
    private lateinit var btnCmdAA55: Button
    private lateinit var btnAutoProbe: Button

    // BT
    private var socket: BluetoothSocket? = null
    private var out: OutputStream? = null
    private var inp: InputStream? = null

    @Volatile private var reading = false
    @Volatile private var autoProbe = false

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

        // 1) MAC’i al
        val macFromIntent = intent.getStringExtra(EXTRA_ADDRESS)
        val mac = macFromIntent ?: getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_LAST_MAC, null)

        if (mac.isNullOrBlank()) {
            // Adres yok -> UI pasif
            tvInfo.append("\nGerçek zamanlı ekran (cihaz: -)")
            tvInfo.append("\nBağlanıyor...\nCihaz adresi yok. Ana ekrandan bağlanın.")
            setButtonsEnabled(false)
            return
        } else {
            tvInfo.append("\nGerçek zamanlı ekran (cihaz: $mac)")
            setButtonsEnabled(false) // bağlanana kadar pasif
            connectAndRead(mac)      // bağlanmayı başlat
        }

        // ——— Buton aksiyonları ———
        btnClear.setOnClickListener { tvInfo.text = "Gerçek zamanlı ekran\nGerçek zamanlı ekran (cihaz: $mac)" }

        btnSend.setOnClickListener {
            val txt = etCmd.text.toString().trim()
            if (txt.isEmpty()) return@setOnClickListener
            sendAscii(txt)
        }

        btnCmdCRLF.setOnClickListener { sendHex(byteArrayOf(0x0D, 0x0A)) }
        btnCmdAA55.setOnClickListener { sendHex(byteArrayOf(0xAA.toByte(), 0x55)) }

        btnCmds.setOnClickListener {
            // “AA AA 55 55” örnek
            sendHex(byteArrayOf(0xAA.toByte(), 0xAA.toByte(), 0x55, 0x55))
        }

        btnAutoProbe.setOnClickListener {
            autoProbe = !autoProbe
            btnAutoProbe.text = if (autoProbe) "Oto Probe (AÇIK)" else "Oto Probe"
            if (autoProbe) {
                thread {
                    while (autoProbe && socket?.isConnected == true) {
                        sendAscii("PROBE")
                        Thread.sleep(300L)
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        autoProbe = false
        reading = false
        try { inp?.close() } catch (_: Throwable) {}
        try { out?.close() } catch (_: Throwable) {}
        try { socket?.close() } catch (_: Throwable) {}
    }

    // ——————————————————————
    // BAĞLAN & OKU
    // ——————————————————————
    private fun connectAndRead(mac: String) {
        tvInfo.append("\nBağlanıyor...")
        thread {
            try {
                val adapter = BluetoothAdapter.getDefaultAdapter()
                val device: BluetoothDevice = adapter.getRemoteDevice(mac)
                val sock = device.createRfcommSocketToServiceRecord(SPP_UUID)
                adapter.cancelDiscovery()
                sock.connect()

                socket = sock
                out = sock.outputStream
                inp = sock.inputStream

                runOnUiThread {
                    tvInfo.append("\nBağlandı. Veri okunuyor...")
                    setButtonsEnabled(true)
                }

                // İsteğe bağlı: handshake (sende çalışıyorsa bırakabilirsin)
                sendHex(byteArrayOf(0x0D, 0x0A))
                sendAscii("POR E\r\n")           // “50 52 4F 42 0D 0A” → "PROB\r\n" benzeri örnekler görmüştün
                sendHex(byteArrayOf(0xAA.toByte(), 0x55, 0x0D, 0x0A))

                // Okuma döngüsü
                val buffer = ByteArray(1024)
                reading = true
                while (reading) {
                    val n = inp?.read(buffer) ?: break
                    if (n <= 0) break
                    val data = buffer.copyOf(n)
                    val hex = data.joinToString(" ") { String.format("%02X", it) }
                    runOnUiThread {
                        tvInfo.append("\nGeldi ($n bayt): $hex")
                    }
                }
            } catch (t: Throwable) {
                runOnUiThread {
                    tvInfo.append("\nBağlantı hatası: ${t.message}")
                    setButtonsEnabled(false)
                }
            } finally {
                try { socket?.close() } catch (_: Throwable) {}
                socket = null
            }
        }
    }

    // ——————————————————————
    // GÖNDERME YARDIMCILARI
    // ——————————————————————
    private fun sendAscii(s: String) {
        val bytes = s.toByteArray(Charsets.US_ASCII)
        sendBytes(bytes, " \"$s\"")
    }

    private fun sendHex(bytes: ByteArray) {
        sendBytes(bytes, " (${bytes.size} bayt)")
    }

    private fun sendBytes(bytes: ByteArray, label: String) {
        val o = out
        if (o == null) {
            tvInfo.append("\nGönderilmedi (bağlı değil).$label")
            return
        }
        try {
            o.write(bytes)
            o.flush()
            val hex = bytes.joinToString(" ") { String.format("%02X", it) }
            tvInfo.append("\nGönder: $hex")
        } catch (t: Throwable) {
            tvInfo.append("\nGönderim hatası: ${t.message}")
        }
    }

    private fun setButtonsEnabled(enabled: Boolean) {
        btnSend.isEnabled = enabled
        btnClear.isEnabled = true                 // temizle hep çalışsın
        btnCmdCRLF.isEnabled = enabled
        btnCmds.isEnabled = enabled
        btnCmdAA55.isEnabled = enabled
        btnAutoProbe.isEnabled = enabled
    }
}
