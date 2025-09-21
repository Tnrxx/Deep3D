package com.deep3d.app

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class RealtimeActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_realtime)
        title = "Gerçek Zamanlı"
    }
}
