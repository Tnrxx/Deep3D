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

    @SuppressLint("MissingPermission")
    private fun connectAndRead(address: String) {
        // Android 12+ için BLUETOOTH_CONNECT izni kontrolü
        if (Build.VERSION.SDK_INT >= 31 &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
            != PackageManager.PERMISSION_GRANTED
        ) {
            toast("Bluetooth CONNECT izni gerekli")
            return
        }

        val bt = BluetoothAdapter.getDefaultAdapter()
        val device: BluetoothDevice? = try { bt.getRemoteDevice(address) } catch (_: Exception) { null }
        if (device == null) {
            append("Cihaz bulunamadı: $address")
            return
        }

        readerThread = Thread {
            try {
                // varsa eski bağlantıyı kapat
                runCatching { socket?.close() }

                val s = device.createRfcommSocketToServiceRecord(sppUuid)
                socket = s

                // discovery varsa durdur; yoksa connect yavaşlar
                runCatching { if (bt.isDiscovering) bt.cancelDiscovery() }

                s.connect()
                runOnUiThread { append("Bağlandı. Veri okunuyor...") }

                val input: InputStream = s.inputStream
                val buf = ByteArray(256)
                var total = 0

                while (!Thread.currentThread().isInterrupted) {
                    val n = input.read(buf)  // bloklayıcı okuma
                    if (n <= 0) break
                    total += n

                    // ilk 16 baytı heks göster (hızlı teşhis)
                    val preview = buf.copyOf(n).take(16).joinToString(" ") { b ->
                        "%02X".format(b)
                    }

                    runOnUiThread {
                        append("n=$n, toplam=$total, önizleme= $preview")
                    }
                }

            } catch (e: Exception) {
                runOnUiThread { append("Hata: ${e.message}") }
            } finally {
                runCatching { socket?.close() }
            }
        }.also { it.start() }
    }

    private fun append(line: String) {
        tvInfo.text = buildString {
            append(tvInfo.text)
            if (tvInfo.text.isNotEmpty()) append("\n")
            append(line)
        }
    }

    private fun toast(msg: String) =
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    override fun onDestroy() {
        super.onDestroy()
        readerThread?.interrupt()
        runCatching { socket?.close() }
    }
}
