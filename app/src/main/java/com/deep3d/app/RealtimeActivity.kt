package com.deep3d.app

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.widget.*
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

    private val btAdapter: BluetoothAdapter? by lazy { BluetoothAdapter.getDefaultAdapter() }
    private var socket: BluetoothSocket? = null
    private var readerThread: Thread? = null
    private val reading = AtomicBoolean(false)

    private var deviceAddr: String? = null

    companion object {
        private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
        private const val TAG = "Realtime"
        private const val PREFS = "deep3d_prefs"
        private const val KEY_ADDR = "last_addr"
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

        tvInfo.text = "Gerçek zamanlı ekran"

        deviceAddr = intent.getStringExtra(MainActivity.EXTRA_ADDR)
            ?: getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_ADDR, null)

        append("Gerçek zamanlı ekran (cihaz: ${deviceAddr ?: "-"})")
        append("Bağlanıyor...")

        btnSend.setOnClickListener { sendManual() }
        btnClear.setOnClickListener { tvInfo.text = "" }

        btnCmdCRLF.setOnClickListener { writeAndLog(byteArrayOf(0x0D, 0x0A), "CRLF") }

        btnCmds.setOnClickListener {
            // Örnek toplu komut
            val arr = byteArrayOf(0x41, 0x41, 0x20, 0x41, 0x41, 0x20, 0x35, 0x35) // "AA AA 55" gibi demo
            writeAndLog(arr, "\"AA AA 55\"")
        }

        btnCmdAA55.setOnClickListener {
            val arr = byteArrayOf(0xAA.toByte(), 0x55.toByte())
            writeAndLog(arr, "AA55")
        }

        btnAutoProbe.setOnClickListener { autoProbe() }
    }

    override fun onResume() {
        super.onResume()
        connectAndRead()
    }

    override fun onPause() {
        super.onPause()
        stopReading()
        closeSocket()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopReading()
        closeSocket()
    }

    private fun sendManual() {
        val text = etCmd.text.toString().trim()
        if (text.isEmpty()) {
            Toast.makeText(this, "Komut boş.", Toast.LENGTH_SHORT).show()
            return
        }
        // "AA55" şeklinde hex yazılmışsa: AA55 -> [0xAA, 0x55]
        val bytes = parseHexOrAscii(text)
        writeAndLog(bytes, "\"$text\"")
    }

    private fun parseHexOrAscii(s: String): ByteArray {
        val clean = s.replace(" ", "")
        return if (clean.matches(Regex("(?i)^[0-9A-F]+$")) && clean.length % 2 == 0) {
            ByteArray(clean.length / 2) { i ->
                clean.substring(i * 2, i * 2 + 2).toInt(16).toByte()
            }
        } else {
            s.toByteArray(Charsets.UTF_8)
        }
    }

    private fun writeAndLog(bytes: ByteArray, label: String) {
        val sock = socket
        if (sock == null || !sock.isConnected) {
            append("Gönderilemedi (bağlı değil): $label")
            return
        }
        try {
            sock.outputStream.write(bytes)
            sock.outputStream.flush()
            append("Gönder: ${toHex(bytes)}  (${bytes.size} bayt)")
        } catch (e: IOException) {
            append("Yazma hatası: ${e.message}")
        }
    }

    private fun autoProbe() {
        // 1) CRLF
        writeAndLog(byteArrayOf(0x0D, 0x0A), "CRLF")
        sleep(120)

        // 2) "PROBE" + CRLF
        writeAndLog("PROBE".toByteArray(Charsets.UTF_8), "\"PROBE\"")
        sleep(20)
        writeAndLog(byteArrayOf(0x0D, 0x0A), "CRLF")
        sleep(150)

        // 3) AA 55 0D 0A
        writeAndLog(byteArrayOf(0xAA.toByte(), 0x55.toByte(), 0x0D, 0x0A), "AA55 CRLF")
    }

    private fun sleep(ms: Long) {
        try { Thread.sleep(ms) } catch (_: InterruptedException) { }
    }

    @SuppressLint("MissingPermission")
    private fun connectAndRead() {
        val addr = deviceAddr
        if (addr.isNullOrBlank()) {
            append("Cihaz adresi yok. Ana ekrandan bağlanın.")
            return
        }
        val adapter = btAdapter
        if (adapter == null || !adapter.isEnabled) {
            append("Bluetooth kapalı.")
            return
        }

        val dev: BluetoothDevice = adapter.getRemoteDevice(addr)
        // İnsecure RFCOMM — HC-05/06 tarzı modüllerde daha uyumlu olur
        val sock = try {
            dev.createInsecureRfcommSocketToServiceRecord(SPP_UUID)
        } catch (e: Exception) {
            append("Socket oluşturma hatası: ${e.message}")
            return
        }

        // Bağlan
        try {
            adapter.cancelDiscovery()
            sock.connect()
            socket = sock
            append("Bağlandı. Veri okunuyor...")

            // Bağlanır bağlanmaz küçük bir handshake dene
            append("Handshake: Gönder: 0D 0A  (2 bayt)")
            sock.outputStream.write(byteArrayOf(0x0D, 0x0A))
            sock.outputStream.flush()
            sleep(120)

            append("Handshake: Gönder: 50 52 4F 42 45 0D 0A  (7 bayt)")
            sock.outputStream.write("PROBE\r\n".toByteArray(Charsets.UTF_8))
            sock.outputStream.flush()
            sleep(150)

            append("Handshake: Gönder: AA 55 0D 0A  (4 bayt)")
            sock.outputStream.write(byteArrayOf(0xAA.toByte(), 0x55.toByte(), 0x0D, 0x0A))
            sock.outputStream.flush()

            // Okuma döngüsü
            startReader(sock)
        } catch (e: IOException) {
            append("Bağlantı hatası: ${e.message}")
            closeSocket()
        }
    }

    private fun startReader(sock: BluetoothSocket) {
        if (reading.get()) return
        reading.set(true)
        readerThread = Thread {
            val input = sock.inputStream
            val buf = ByteArray(1024)
            while (reading.get() && sock.isConnected) {
                try {
                    val avail = input.available()
                    if (avail > 0) {
                        val n = input.read(buf, 0, minOf(avail, buf.size))
                        if (n > 0) {
                            val chunk = buf.copyOf(n)
                            runOnUiThread {
                                append("RX: ${toHex(chunk)}  (${n} bayt)")
                            }
                        }
                    } else {
                        Thread.sleep(20)
                    }
                } catch (io: IOException) {
                    runOnUiThread { append("Okuma bitti: ${io.message}") }
                    break
                } catch (_: InterruptedException) {
                    break
                }
            }
            reading.set(false)
        }.apply { start() }
    }

    private fun stopReading() {
        reading.set(false)
        readerThread?.interrupt()
        readerThread = null
    }

    private fun closeSocket() {
        try { socket?.close() } catch (_: Exception) {}
        socket = null
    }

    private fun append(line: String) {
        Log.d(TAG, line)
        tvInfo.append(if (tvInfo.text.isNullOrBlank()) line else "\n$line")
    }

    private fun toHex(bytes: ByteArray): String =
        bytes.joinToString(" ") { b -> "%02X".format(b) }
}
