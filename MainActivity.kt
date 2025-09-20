package com.deep3d.app

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import com.deep3d.app.grid.GridActivity
import com.deep3d.app.realtime.RealtimeActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val et = findViewById<EditText>(R.id.etDeviceName)
        findViewById<Button>(R.id.btnRealtime).setOnClickListener {
            startActivity(Intent(this, RealtimeActivity::class.java).apply {
                putExtra("deviceName", et.text.toString())
            })
        }
        findViewById<Button>(R.id.btnGrid).setOnClickListener {
            startActivity(Intent(this, GridActivity::class.java).apply {
                putExtra("deviceName", et.text.toString())
            })
        }
    }
}
