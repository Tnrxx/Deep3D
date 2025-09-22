package com.deep3d.app

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.io.IOException
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

class RealtimeActivity : AppCompatActivity() {

    private lateinit var tvInfo: TextView
    private lateinit var etCmd: EditText
    private lateinit var btnSend: Button
    private lateinit var btnClear: Button
    private lateinit var btnCmdCRLF: Button
    private lateinit var btnCmds: Button
    private lateinit var btnCmdAA55: Button
    private lateinit var btnAutoProbe: Button

    private var socket: BluetoothSocket? = null
    private var readThread: Thread? = null
    private val reading = AtomicBoolean(false)

    companion object {
        private val SPP_UUID: UUID =
            UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

        const val EXTRA_DEVICE_ADDRESS = MainActivity.EXTRA_DEVICE_ADDRESS
        private const val PREF_LAST_MAC = "last_mac"
    }

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

        // 1) MAC’i intent’ten nullable al, yoksa SharedPreferences’tan dene
        val macFromIntent: String? = intent.getStringExtra(EXTRA_DEVICE_ADDRESS)
        val mac: String? = macFromIntent ?: getSharedPreferences("deep3d_prefs", MODE_PRIVATE)
            .getString(PREF_LAST_MAC, null)

        tvInfo.append("\nGerçek zamanlı ekran (cihaz: ${mac ?: "-"})")

        if (mac.isNullOrBlank()) {
            tvInfo.append("\nBağlanıyor...\nCihaz adresi yok. Ana ekrandan bağlanın.")
            setButtonsEnabled(false)
            return
        }

        // Bağlan ve okuma başlat
        connectAndRead(mac)

        // --- Kısa yol butonları ---
        btnClear.setOnClickListener { tvInfo.text = "Gerçek zamanlı ekran\nGerçek zamanlı ekran (cihaz: $mac)" }

        btnSend.setOnClickListener {
            val txt = etCmd.text.toString().trim()
            if (txt.isNotEmpty()) sendAscii(txt)
        }

        btnCmdCRLF.setOnClickListener { sendHex(byteArrayOf(0x0D, 0x0A)) }
        btnCmdAA55.setOnClickListener { sendHex(byteArrayOf(0xAA.toByte(), 0x55)) }
        btnCmds.setOnClickListener { sendAscii("PROBE") }

        // bas-konum gösterimli “Oto Probe”
        btnAutoProbe.setOnClickListener {
            sendAscii("PROBE")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopReading()
        socket?.closeSilently()
    }

    // ---- Bluetooth bağlan + okuma ----
    private fun connectAndRead(mac: String) {
        Thread {
            runOnUiThread { tvInfo.append("\nBağlanıyor...") }
            val bt = BluetoothAdapter.getDefaultAdapter()
            if (bt == null || !bt.isEnabled) {
                runOnUiThread { tvInfo.append("\nBluetooth kapalı.") }
                return@Thread
            }
            val dev: BluetoothDevice = try {
                bt.getRemoteDevice(mac)
            } catch (e: IllegalArgumentException) {
                runOnUiThread { tvInfo.append("\nHatalı MAC: $mac") }
                return@Thread
            }

            try {
                val tmp = dev.createRfcommSocketToServiceRecord(SPP_UUID)
                bt.cancelDiscovery()
                tmp.connect()
                socket = tmp
            } catch (e: IOException) {
                runOnUiThread { tvInfo.append("\nBağlanamadı: ${e.message}") }
                return@Thread
            }

            runOnUiThread { tvInfo.append("\nBağlandı. Veri okunuyor...") }
            startReading()
        }.start()
    }

    private fun startReading() {
        val s = socket ?: return
        if (reading.getAndSet(true)) return

        readThread = Thread {
            val buf = ByteArray(1024)
            try {
                val `in` = s.inputStream
                while (reading.get()) {
                    val n = `in`.read(buf)
                    if (n <= 0) break
                    val hex = buf.copyOf(n).joinToString(" ") { b ->
                        String.format("%02X", b)
                    }
                    runOnUiThread { tvInfo.append("\nGeldi: $hex  ($n bayt)") }
                }
            } catch (e: IOException) {
                runOnUiThread { tvInfo.append("\nOkuma bitti: ${e.message}") }
            } finally {
                reading.set(false)
                s.closeSilently()
            }
        }.also { it.start() }
    }

    private fun stopReading() {
        reading.set(false)
        readThread?.interrupt()
        readThread = null
    }

    // ---- gönderimler ----
    private fun sendAscii(text: String) {
        val s = socket
        if (s == null || !s.isConnected) {
            tvInfo.append("\nGönderilmedi (bağlı değil). \"$text\"")
            return
        }
        try {
            val bytes = (text + "\r\n").toByteArray(Charsets.US_ASCII)
            s.outputStream.write(bytes)
            tvInfo.append("\nGönder: $text  (${bytes.size} bayt)")
        } catch (e: IOException) {
            tvInfo.append("\nGönderilemedi: ${e.message}")
        }
    }

    private fun sendHex(bytes: ByteArray) {
        val s = socket
        if (s == null || !s.isConnected) {
            val hex = bytes.joinToString(" ") { String.format("%02X", it) }
            tvInfo.append("\nGönderilmedi (bağlı değil). $hex")
            return
        }
        try {
            s.outputStream.write(bytes)
            val hex = bytes.joinToString(" ") { String.format("%02X", it) }
            tvInfo.append("\nGönder: $hex  (${bytes.size} bayt)")
        } catch (e: IOException) {
            tvInfo.append("\nGönderilemedi: ${e.message}")
        }
    }

    private fun setButtonsEnabled(enabled: Boolean) {
        btnSend.isEnabled = enabled
        btnClear.isEnabled = enabled
        btnCmdCRLF.isEnabled = enabled
        btnCmds.isEnabled = enabled
        btnCmdAA55.isEnabled = enabled
        btnAutoProbe.isEnabled = enabled
    }

    // ---- küçük yardımcılar ----
    private fun BluetoothSocket.closeSilently() {
        try { close() } catch (_: Throwable) {}
    }
}
