package com.deep3d.app

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.os.Build
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.io.IOException
import java.util.UUID

class RealtimeActivity : AppCompatActivity() {

    private lateinit var tvInfo: TextView

    private val bt: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private var socket: BluetoothSocket? = null
    private var readerThread: Thread? = null

    // Klasik SPP UUID
    private val SPP_UUID: UUID =
        UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_realtime)

        tvInfo = findViewById(R.id.tvInfo)

        val addr = intent.getStringExtra("deviceAddress")
        if (addr.isNullOrBlank()) {
            Toast.makeText(this, "Cihaz adresi gelmedi (önce bağlan).", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        tvInfo.text = "Gerçek zamanlı ekran (cihaz: $addr)\nBağlanıyor…"
        connectAndStartReading(addr)
    }

    @SuppressLint("MissingPermission")
    private fun connectAndStartReading(address: String) {
        val device: BluetoothDevice? = try {
            bt?.getRemoteDevice(address)
        } catch (_: IllegalArgumentException) {
            null
        }

        if (device == null) {
            tvInfo.append("\nCihaz bulunamadı.")
            return
        }

        Thread {
            try {
                if (bt?.isDiscovering == true) bt.cancelDiscovery()

                // Android 12+ için de aynı yöntem geçerli
                val sock = device.createRfcommSocketToServiceRecord(SPP_UUID)
                sock.connect()
                socket = sock

                runOnUiThread {
                    tvInfo.append("\nBağlandı. Veri okunuyor…")
                    Toast.makeText(this, "Realtime bağlı", Toast.LENGTH_SHORT).show()
                }

                val input = sock.inputStream
                val buf = ByteArray(1024)
                val sb = StringBuilder()

                // Basit okuyucu döngüsü (karakter/ satır biriktirme)
                while (!Thread.currentThread().isInterrupted) {
                    val n = input.read(buf)
                    if (n > 0) {
                        val chunk = String(buf, 0, n)
                        sb.append(chunk)

                        // Satır sonu gördükçe ekrana yaz (ileride parse/grafik yapacağız)
                        var idx = sb.indexOf("\n")
                        while (idx >= 0) {
                            val line = sb.substring(0, idx).trim()
                            if (line.isNotEmpty()) {
                                runOnUiThread { tvInfo.append("\n$line") }
                            }
                            sb.delete(0, idx + 1)
                            idx = sb.indexOf("\n")
                        }
                    }
                }
            } catch (e: IOException) {
                runOnUiThread {
                    tvInfo.append("\nBağlantı/okuma hatası: ${e.message}")
                    Toast.makeText(this, "Realtime hata: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }.also { t ->
            readerThread = t
            t.start()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        readerThread?.interrupt()
        runCatching { socket?.close() }
    }
}
