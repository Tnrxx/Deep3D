package com.deep3d.app

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class RealtimeActivity : AppCompatActivity() {

    private lateinit var tvInfo: TextView
    private lateinit var etCmd: EditText
    private lateinit var btnSend: Button
    private lateinit var btnClear: Button
    private lateinit var btnCmdCRLF: Button
    private lateinit var btnCmds: Button
    private lateinit var btnCmdAA55: Button
    private lateinit var btnAutoProbe: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_realtime)

        // XML'deki view'ları bağla
        tvInfo = findViewById(R.id.tvInfo)
        etCmd = findViewById(R.id.etCmd)
        btnSend = findViewById(R.id.btnSend)
        btnClear = findViewById(R.id.btnClear)
        btnCmdCRLF = findViewById(R.id.btnCmdCRLF)
        btnCmds = findViewById(R.id.btnCmds)
        btnCmdAA55 = findViewById(R.id.btnCmdAA55)
        btnAutoProbe = findViewById(R.id.btnAutoProbe)

        // (Opsiyonel) MainActivity'den geldiyse cihaz adresini başlığa yaz
        val addr = intent.getStringExtra("deviceAddress")
        if (!addr.isNullOrBlank()) {
            tvInfo.append("\nGerçek zamanlı ekran (cihaz: $addr)")
        }

        // Basit aksiyonlar (derleme için yeterli; istersen sonra BT gönderimini ekleriz)
        btnSend.setOnClickListener {
            val txt = etCmd.text?.toString().orEmpty()
            tvInfo.append("\nGönder tuşu: \"$txt\"")
        }

        btnClear.setOnClickListener {
            tvInfo.text = ""
        }

        btnCmdCRLF.setOnClickListener {
            etCmd.setText("\r\n")
            tvInfo.append("\nHızlı: CRLF")
        }

        btnCmds.setOnClickListener {
            etCmd.setText("AA AA 55 55")
            tvInfo.append("\nHızlı: Komutlar")
        }

        btnCmdAA55.setOnClickListener {
            etCmd.setText("AA55")
            tvInfo.append("\nHızlı: AA55")
        }

        btnAutoProbe.setOnClickListener {
            etCmd.setText("PROBE")
            tvInfo.append("\nHızlı: Oto Probe")
        }
    }
}
