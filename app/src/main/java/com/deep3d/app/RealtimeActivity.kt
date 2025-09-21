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
import java.util.UUID
import kotlin.concurrent.thread

class RealtimeActivity : AppCompatActivity() {

    private lateinit var tvInfo: TextView

    private val sppUuid: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    private var socket: BluetoothSocket? = null
    @Volatile private var readerRunning = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_realtime)
        tvInfo = findViewById(R.id.tvInfo)

        val addr = intent.getStringExtra("deviceAddress")
        if (addr.isNullOrBlank()) {
            tvInfo.text = "Önce cihaza bağlan."
            return
        }
        tvInfo.text = "Gerçek zamanlı ekran (cihaz: $addr)\nBağlanıyor…"

        connectAndRead(addr)
    }

    override fun onDestroy() {
        readerRunning = false
        runCatching { socket?.close() }
        super.onDestroy()
    }

    @SuppressLint("MissingPermission")
    private fun connectAndRead(address: String) {
        val bt = BluetoothAdapter.getDefaultAdapter()
        if (bt == null) {
            tvInfo.append("\nBu cihazda Bluetooth yok.")
            return
        }

        // Android 12+ izin kontrolü
        if (Build.VERSION.SDK_INT >= 31) {
            val okConnect = ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
            if (!okConnect) {
                tvInfo.append("\nBLUETOOTH_CONNECT izni gerekli.")
                return
            }
        }

        val dev: BluetoothDevice = bt.getRemoteDevice(address)

        thread {
            try {
                // Eski bir soket varsa kapat
                runCatching { socket?.close() }

                val s = dev.createRfcommSocketToServiceRecord(sppUuid)
                socket = s
                // Discovery açık ise kapat (bağlantıyı yavaşlatır)
                if (bt.isDiscovering) bt.cancelDiscovery()
                s.connect()

                runOnUiThread {
                    tvInfo.append("\nBağlandı. Veri okunuyor…")
                }

                // --- Test “probe” paketleri (cihaz veri akışını tetikliyorsa görürüz)
                val out = s.outputStream
                val probes = listOf(
                    byteArrayOf(0x0D, 0x0A),          // CRLF
                    byteArrayOf('S'.code.toByte()),   // 'S' başlat
                    byteArrayOf(0xAA.toByte(), 0x55.toByte()) // AA55
                )
                for (p in probes) {
                    runCatching {
                        out.write(p)
                        out.flush()
                        runOnUiThread { tvInfo.append("\nProbe gönderildi (${p.size} bayt)") }
                        Thread.sleep(150)
                    }
                }

                // --- Sürekli okuma (HEX dump)
                val `in` = s.inputStream
                readerRunning = true
                val sb = StringBuilder()
                val buf = ByteArray(256)

                var lastAnyDataMs = System.currentTimeMillis()
                while (readerRunning) {
                    val n = try { `in`.read(buf) } catch (e: IOException) { -1 }
                    if (n <= 0) break

                    lastAnyDataMs = System.currentTimeMillis()
                    // geleni hex’e çevir
                    for (i in 0 until n) {
                        sb.append(String.format("%02X ", buf[i]))
                    }
                    sb.append(" | ")
                    // okunabilir ASCII karakterleri de göster
                    val ascii = buf.copyOfRange(0, n).map {
                        val c = it.toInt() and 0xFF
                        if (c in 32..126) c.toChar() else '.'
                    }.joinToString("")
                    sb.append(ascii)
                    sb.append('\n')

                    val text = sb.toString()
                    runOnUiThread {
                        // Aşırı büyümeyi engelle
                        val trimmed = if (text.length > 20000) text.takeLast(20000) else text
                        tvInfo.text = "Gerçek zamanlı ekran (cihaz: $address)\n$trimmed"
                    }
                }

                // hiç veri yoksa uyarı
                if (System.currentTimeMillis() - lastAnyDataMs > 5000) {
                    runOnUiThread {
                        tvInfo.append("\n5 sn içinde veri gelmedi. Cihaz akışı başlatmak için özel komut bekliyor olabilir.")
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    tvInfo.append("\nHata: ${e.message}")
                    Toast.makeText(this, "Bağlantı/okuma hatası: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}
