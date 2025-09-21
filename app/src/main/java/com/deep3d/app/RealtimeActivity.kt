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
import java.io.IOException
import java.nio.charset.Charset
import java.util.UUID

class RealtimeActivity : AppCompatActivity() {

    private lateinit var tvInfo: TextView
    private var socket: BluetoothSocket? = null
    private var readThread: Thread? = null

    private val SPP_UUID: UUID =
        UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_realtime)
        tvInfo = findViewById(R.id.tvInfo)

        val addr = intent.getStringExtra("deviceAddress")
        if (addr.isNullOrEmpty()) {
            Toast.makeText(this, "Adres yok (önce cihaza bağlan)", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        tvInfo.append("Gerçek zamanlı ekran (cihaz: $addr)\nBağlanıyor...\n")
        connectAndStart(addr)
    }

    @SuppressLint("MissingPermission")
    private fun connectAndStart(address: String) {
        val bt = BluetoothAdapter.getDefaultAdapter()
        if (bt == null) {
            tvInfo.append("Bluetooth yok\n"); return
        }
        if (Build.VERSION.SDK_INT >= 31) {
            val okC = ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
            if (!okC) { tvInfo.append("İzin gerekli: BLUETOOTH_CONNECT\n"); return }
        }

        val dev: BluetoothDevice? = try { bt.getRemoteDevice(address) } catch (_: IllegalArgumentException) { null }
        if (dev == null) { tvInfo.append("Cihaz bulunamadı: $address\n"); return }

        readThread = Thread {
            try {
                val s = dev.createRfcommSocketToServiceRecord(SPP_UUID)
                socket = s
                bt.cancelDiscovery()
                s.connect()
                runOnUiThread { tvInfo.append("Bağlandı. Veri okunuyor...\n") }

                // 1) Bazı cihazlar tetik bekler: ufak “ping”
                try {
                    val out = s.outputStream
                    out.write("\n".toByteArray())
                    out.write("START\r\n".toByteArray()) // cihaz görmezse yok sayar
                    out.flush()
                } catch (_: Exception) { /* yazamasak da sorun değil */ }

                // 2) Ham bayt okuma döngüsü
                val inp = s.inputStream
                val buf = ByteArray(1024)
                val lineBuffer = StringBuilder()

                while (!Thread.currentThread().isInterrupted) {
                    val n = inp.read(buf)  // bloklayıcı okuma
                    if (n <= 0) continue

                    // Gelen veriyi ASCII'ye çevir
                    val chunk = String(buf, 0, n, Charset.forName("UTF-8"))
                    lineBuffer.append(chunk)

                    // Satır sonları varsa satır satır göster
                    var idx = lineBuffer.indexOf("\n")
                    while (idx >= 0) {
                        val line = lineBuffer.substring(0, idx).trimEnd('\r')
                        val toShow = if (line.isNotEmpty()) line else "(boş satır)"
                        runOnUiThread { tvInfo.append("$toShow\n") }
                        lineBuffer.delete(0, idx + 1)
                        idx = lineBuffer.indexOf("\n")
                    }

                    // Hiç newline yoksa, yine de bir şeyler göstermek için
                    if (!chunk.contains("\n") && lineBuffer.length > 200) {
                        val preview = lineBuffer.toString()
                        val hex = buf.copyOf(n).joinToString(" ") { b -> "%02X".format(b) }
                        runOnUiThread {
                            tvInfo.append("[NY] ${preview.take(80)}\n")
                            tvInfo.append("[HEX] $hex\n")
                        }
                        // tamponu çok büyütmemek için kıs
                        if (lineBuffer.length > 1000) lineBuffer.delete(0, lineBuffer.length - 200)
                    }
                }
            } catch (e: IOException) {
                runOnUiThread {
                    tvInfo.append("Hata: ${e.message}\n")
                    Toast.makeText(this, "Bağlantı koptu / okunamadı", Toast.LENGTH_LONG).show()
                }
            }
        }.also { it.start() }
    }

    override fun onDestroy() {
        super.onDestroy()
        readThread?.interrupt()
        try { socket?.close() } catch (_: Exception) {}
    }
}
