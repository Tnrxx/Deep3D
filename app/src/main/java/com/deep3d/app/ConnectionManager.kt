package com.deep3d.app

import android.bluetooth.BluetoothSocket
import java.io.InputStream
import java.io.OutputStream

object ConnectionManager {
    var socket: BluetoothSocket? = null
    val isConnected: Boolean
        get() = socket?.isConnected == true

    val out: OutputStream?
        get() = socket?.outputStream

    val inn: InputStream?
        get() = socket?.inputStream

    fun close() {
        try { socket?.close() } catch (_: Exception) {}
        socket = null
    }
}
