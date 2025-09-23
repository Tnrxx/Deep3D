package com.deep3d.app

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

object ConnectionManager {

    // KLASİK BLUETOOTH SPP UUID (RFCOMM)
    private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    @Volatile var socket: BluetoothSocket? = null
        private set
    @Volatile var input: InputStream? = null
        private set
    @Volatile var out: OutputStream? = null
        private set

    val isConnected: Boolean
        get() = socket?.isConnected == true

    fun connect(device: BluetoothDevice): Boolean {
        close()
        return try {
            val s = device.createRfcommSocketToServiceRecord(SPP_UUID)
            // Not: discovery kapalı varsayımı
            s.connect()
            socket = s
            input = s.inputStream
            out = s.outputStream
            true
        } catch (e: Exception) {
            close()
            false
        }
    }

    fun close() {
        try { input?.close() } catch (_: Exception) {}
        try { out?.close() } catch (_: Exception) {}
        try { socket?.close() } catch (_: Exception) {}
        input = null
        out = null
        socket = null
    }
}
