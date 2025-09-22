package com.deep3d.app

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
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

    private var socket: BluetoothSocket? = null
    private var inStream: InputStream? = null
    private var outStream: OutputStream? = null
    private var readerThread: Thread? = null

    // SPP UUID (klasik seri port profili)
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

        tvInfo.text = "Gerçek zamanlı ekran"

        val deviceAddress = intent.getStringExtra("DEVICE_ADDRESS")
        val deviceName = intent.getStringExtra("DEVICE_NAME") ?: "BT-Device"

        tvInfo.append("\nGerçek zamanlı ekran (cihaz: ${deviceAddress ?: "-"})")
        tvInfo.append("\nBağlanıyor...")

        if (deviceAddress.isNullOrEmpty()) {
            tvInfo.append("\nCihaz adresi yok. Ana ekrandan bağlanın.")
            disableButtons()
            return
        } else {
            enableButtons()
            connectAndStartIO(deviceAddress, deviceName)
        }

        // ----- Butonlar -----
        btnSend.setOnClickListener {
            val txt = etCmd.text.toString().trim()
            if (txt.isEmpty()) return@setOnClickListener
            sendAsciiOrHex(txt)
        }

        btnClear.setOnClickListener {
            tvInfo.text = "Gerçek zamanlı ekran\nGerçek zamanlı ekran (cihaz: ${deviceAddress})"
        }

        btnCmdCRLF.setOnClickListener { sendBytes(byteArrayOf(0x0D, 0x0A)) }      // "\r\n"
        btnCmds.setOnClickListener { sendAscii("PROBE") }                         // örnek
        btnCmdAA55.setOnClickListener { sendBytes(byteArrayOf(0xAA.toByte(), 0x55.toByte())) }
        btnAutoProbe.setOnClickListener {
            // Protokol örnek dizisi – istersen burayı kendi dizine göre güncelleyebiliriz
            sendBytes(byteArrayOf(0x0D, 0x0A)) // CRLF
            sendAscii("PROBE")
            sendBytes(byteArrayOf(0xAA.toByte(), 0x55.toByte()))
        }
    }

    private fun connectAndStartIO(address: String, name: String) {
        thread {
            try {
                val adapter = BluetoothAdapter.getDefaultAdapter()
                val device: BluetoothDevice = adapter.getRemoteDevice(address)

                // Eski bağlantı varsa kapat
                closeSocketQuietly()

                // rfcomm socket
                val sock = device.createRfcommSocketToServiceRecord(SPP_UUID)
                adapter.cancelDiscovery()
                sock.connect()

                socket = sock
                inStream = sock.inputStream
                outStream = sock.outputStream

                runOnUiThread {
                    tvInfo.append("\nBağlandı. Veri okunuyor...")
                }

                // Okuma iş parçacığı
                readerThread = thread {
                    val buf = ByteArray(1024)
                    while (!Thread.currentThread().isInterrupted) {
                        try {
                            val n = inStream?.read(buf) ?: -1
                            if (n <= 0) break
                            val copy = buf.copyOf(n)
                            val hex = copy.joinToString(" ") { b -> "%02X".format(b) }
                            runOnUiThread {
                                tvInfo.append("\nGeldi: $hex  (${copy.size} bayt)")
                                scrollDown()
                            }
                        } catch (e: Exception) {
                            runOnUiThread {
                                tvInfo.append("\nOkuma bitti: ${e.message}")
                                scrollDown()
                            }
                            break
                        }
                    }
                }

            } catch (e: Exception) {
                runOnUiThread {
                    tvInfo.append("\nBağlantı hatası: ${e.message}")
                    Toast.makeText(this, "Bağlanılamadı: ${e.message}", Toast.LENGTH_LONG).show()
                    disableButtons()
                }
            }
        }
    }

    // Metin alanına yazılanı “HEX gibi” veya “ASCII” olarak gönder
    private fun sendAsciiOrHex(text: String) {
        // "AA55" veya "AA 55" gibi girildiyse HEX kabul edelim
        val cleaned = text.replace("\\s+".toRegex(), "")
        val isHex = cleaned.matches(Regex("(?i)[0-9A-F]+")) && cleaned.length % 2 == 0
        if (isHex) {
            val bytes = cleaned.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
            sendBytes(bytes)
        } else {
            sendAscii(text)
        }
    }

    private fun sendAscii(s: String) {
        sendBytes(s.toByteArray())
    }

    private fun sendBytes(bytes: ByteArray) {
        thread {
            try {
                val os = outStream ?: return@thread
                os.write(bytes)
                os.flush()
                val hex = bytes.joinToString(" ") { "%02X".format(it) }
                runOnUiThread {
                    tvInfo.append("\nGönder: $hex  (${bytes.size} bayt)")
                    scrollDown()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    tvInfo.append("\nGönderilemedi: ${e.message}")
                    scrollDown()
                }
            }
        }
    }

    private fun disableButtons() {
        btnSend.isEnabled = false
        btnClear.isEnabled = false
        btnCmdCRLF.isEnabled = false
        btnCmds.isEnabled = false
        btnCmdAA55.isEnabled = false
        btnAutoProbe.isEnabled = false
    }

    private fun enableButtons() {
        btnSend.isEnabled = true
        btnClear.isEnabled = true
        btnCmdCRLF.isEnabled = true
        btnCmds.isEnabled = true
        btnCmdAA55.isEnabled = true
        btnAutoProbe.isEnabled = true
    }

    private fun scrollDown() {
        // TextView uzun olunca otomatik alttan görünsün diye küçük bir hile:
        if (tvInfo.layout != null) {
            val amount = tvInfo.layout.getLineTop(tvInfo.lineCount) - tvInfo.height
            if (amount > 0) tvInfo.scrollTo(0, amount) else tvInfo.scrollTo(0, 0)
        }
    }

    private fun closeSocketQuietly() {
        try { inStream?.close() } catch (_: Exception) {}
        try { outStream?.close() } catch (_: Exception) {}
        try { socket?.close() } catch (_: Exception) {}
        inStream = null
        outStream = null
        socket = null
        readerThread?.interrupt()
        readerThread = null
    }

    override fun onStop() {
        super.onStop()
        closeSocketQuietly()
    }
}
