package com.deep3d.app

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import java.io.InputStream
import java.util.UUID

class RealtimeActivity : AppCompatActivity() {

    private lateinit var tvInfo: TextView

    private val sppUuid: UUID =
        UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    private var socket: BluetoothSocket? = null
    private var readerThread: Thread? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_realtime)

        tvInfo = findViewById(R.id.tvInfo)

        val addr = intent.getStringExtra("deviceAddress")
        if (addr.isNullOrBlank()) {
            toast("Önce cihaza bağlan.")
            finish()
            return
        }

        title = "Gerçek zamanlı ekran (cihaz: $addr)"
        tvInfo.text = "Bağlanıyor..."

        connectAndRead(addr)
    }

    // --- Yardımcılar ---
    private fun append(line: String) {
        tvInfo.text = buildString {
            append(tvInfo.text)
            if (tvInfo.text.isNotEmpty()) append("\n")
            append(line)
        }
    }
    private fun toast(msg: String) =
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    // --- Ana iş: bağlan + veri oku ---
    @SuppressLint("MissingPermission")
    private fun connectAndRead(address: String) {
        if (Build.VERSION.SDK_INT >= 31 &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
            != PackageManager.PERMISSION_GRANTED
        ) {
            toast("Bluetooth CONNECT izni gerekli")
            return
        }

        val bt = BluetoothAdapter.getDefaultAdapter()
        val device: BluetoothDevice? = runCatching { bt.getRemoteDevice(address) }.getOrNull()
        if (device == null) { append("Cihaz bulunamadı: $address"); return }

        readerThread = Thread {
            try {
                runCatching { socket?.close() }

                // 1) Secure SPP
                val s1 = runCatching { device.createRfcommSocketToServiceRecord(sppUuid) }.getOrNull()
                val sock = when {
                    tryConnect(bt, s1) != null -> s1!!
                    // 2) Insecure SPP
                    else -> {
                        val s2 = runCatching { device.createInsecureRfcommSocketToServiceRecord(sppUuid) }.getOrNull()
                        if (tryConnect(bt, s2) != null) s2!!
                        // 3) Kanal 1 fallback (bazı cihazlar böyle açılır)
                        else {
                            val s3 = runCatching {
                                val m = device.javaClass.getMethod("createRfcommSocket", Int::class.javaPrimitiveType)
                                m.invoke(device, 1) as BluetoothSocket
                            }.getOrNull()
                            if (tryConnect(bt, s3) != null) s3!!
                            else throw RuntimeException("Bağlantı kurulamadı (tüm yöntemler denendi).")
                        }
                    }
                }

                socket = sock
                runOnUiThread { append("Bağlandı. Veri okunuyor...") }

                // Bazı cihazlar veri için "uyanma" komutu ister — küçük probelar gönderiyoruz.
                val out = sock.outputStream
                val probes = arrayOf(
                    byteArrayOf(0x0A),                // LF
                    "PING\n".toByteArray(),           // ASCII
                    byteArrayOf(0x55, 0xAA.toByte()) // 0x55 0xAA
                )
                for (p in probes) {
                    runCatching { out.write(p); out.flush() }
                    runOnUiThread { append("Probe gönderildi (${p.size} bayt)") }
                    Thread.sleep(300)
                }

                // Okuma döngüsü
                val input: InputStream = sock.inputStream
                val buf = ByteArray(512)
                var total = 0
                while (!Thread.currentThread().isInterrupted) {
                    val n = runCatching { input.read(buf) }.getOrNull() ?: break
                    if (n <= 0) break
                    total += n
                    val preview = buf.copyOf(n).take(16).joinToString(" ") { "%02X".format(it) }
                    runOnUiThread { append("n=$n, toplam=$total, önizleme=$preview") }
                }

            } catch (e: Exception) {
                runOnUiThread { append("Hata: ${e.message}") }
            } finally {
                runCatching { socket?.close() }
            }
        }.also { it.start() }
    }

    @SuppressLint("MissingPermission")
    private fun tryConnect(bt: BluetoothAdapter, sock: BluetoothSocket?): BluetoothSocket? {
        if (sock == null) return null
        runCatching { if (bt.isDiscovering) bt.cancelDiscovery() }
        return try {
            sock.connect()
            sock
        } catch (_: Exception) {
            runCatching { sock.close() }
            null
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        readerThread?.interrupt()
        runCatching { socket?.close() }
    }
}
