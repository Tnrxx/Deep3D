package com.deep3d.app.bt

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import com.deep3d.upgrade.bt.IBtSource
import java.io.InputStream
import java.util.UUID

class RfcommBtSource(private val device: BluetoothDevice): IBtSource {
    private var socket: BluetoothSocket? = null
    private var ins: InputStream? = null

    // SPP UUID (çoğu cihaz için)
    private val sppUUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    fun connect(): Boolean {
        return try {
            socket = device.createRfcommSocketToServiceRecord(sppUUID).also { it.connect() }
            ins = socket!!.inputStream
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    override fun readC(): Double {
        // NOTE: This is a placeholder parser.
        // Replace with your device packet parsing.
        // Here we read one signed 16-bit little-endian value as C.
        val s = ins ?: throw IllegalStateException("Not connected")
        val b0 = s.read()
        val b1 = s.read()
        if (b0 < 0 || b1 < 0) return 0.0
        val v = (b0 or (b1 shl 8))
        val signed = if (v and 0x8000 != 0) v - 0x10000 else v
        return signed.toDouble()
    }

    fun close() {
        try { ins?.close() } catch (_: Exception) {}
        try { socket?.close() } catch (_: Exception) {}
    }
}
