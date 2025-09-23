package com.deep3d.app

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class RealtimeActivity : AppCompatActivity() {

    private lateinit var txtStatus: TextView
    private lateinit var edtCmd: EditText
    private lateinit var btnSend: Button
    private lateinit var btnClear: Button
    private lateinit var btnCrLf: Button
    private lateinit var btnAA55: Button
    private lateinit var btnAutoProbe: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_realtime)

        // View binding (findViewById) – eksik ID hatalarının sebebi buydu
        txtStatus   = findViewById(R.id.txtStatus)
        edtCmd      = findViewById(R.id.edtCmd)
        btnSend     = findViewById(R.id.btnSend)
        btnClear    = findViewById(R.id.btnClear)
        btnCrLf     = findViewById(R.id.btnCrLf)
        btnAA55     = findViewById(R.id.btnAA55)
        btnAutoProbe= findViewById(R.id.btnAutoProbe)

        // Şimdilik butonların çalıştığını net görelim (Toast)
        btnSend.setOnClickListener {
            val cmd = edtCmd.text.toString()
            if (cmd.isBlank()) {
                Toast.makeText(this, "Komut boş!", Toast.LENGTH_SHORT).show()
            } else {
                // TODO: buraya Bluetooth gönderimi eklenecek
                Toast.makeText(this, "Gönderildi: $cmd", Toast.LENGTH_SHORT).show()
            }
        }

        btnClear.setOnClickListener {
            edtCmd.text.clear()
            Toast.makeText(this, "Temizlendi", Toast.LENGTH_SHORT).show()
        }

        btnCrLf.setOnClickListener {
            edtCmd.append("\r\n")
            Toast.makeText(this, "CRLF eklendi", Toast.LENGTH_SHORT).show()
        }

        btnAA55.setOnClickListener {
            edtCmd.setText("AA55")
            Toast.makeText(this, "AA55 hazır", Toast.LENGTH_SHORT).show()
        }

        btnAutoProbe.setOnClickListener {
            // TODO: cihazdan örnek veri çekme/otoprotez burada olacak
            Toast.makeText(this, "Oto Probe (demo)", Toast.LENGTH_SHORT).show()
        }

        // Ekran üstü durum yazısı
        txtStatus.text = "Gerçek zamanlı ekran (butonlar aktif)"
    }
}
