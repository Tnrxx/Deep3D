package com.deep3d.upgrade.realtime

import android.os.SystemClock
import com.deep3d.upgrade.bt.IBtSource
import com.deep3d.upgrade.util.Stats
import kotlin.concurrent.thread

class RealtimeController(
    private val bt: IBtSource,
    private val onChart: (c: Double, baseline: Double) -> Unit,
    private val onEvent: (Event) -> Unit,
    private val onState: (String) -> Unit
) {
    var running = false
    var moving = false
    private val analyzer = RealtimeAnalyzer()

    fun setParams(windowN: Int = 101, k: Double = 3.0) {
        analyzer.N = windowN; analyzer.k = k
    }
    fun setMoving(isMoving: Boolean) { moving = isMoving }

    fun calibrate(seconds: Int = 6) {
        onState("Kalibrasyon...")
        val buf = mutableListOf<Double>()
        val t0 = SystemClock.elapsedRealtime()
        while (SystemClock.elapsedRealtime() - t0 < seconds * 1000) {
            buf += bt.readC()
        }
        val base = Stats.median(buf)
        val mad  = Stats.mad(buf, base)
        analyzer.baseline = base
        analyzer.sigma    = 1.4826 * mad
        onState("Hazır (base=%.2f σ=%.2f)".format(analyzer.baseline, analyzer.sigma))
    }

    fun start() {
        if (running) return
        running = true
        thread(start=true, name="RealtimeLoop") {
            onState("Çalışıyor")
            while (running) {
                val c = bt.readC()
                val ev = if (moving) analyzer.push(c) else null
                onChart(c, analyzer.baseline)
                if (ev != null) onEvent(ev)
            }
            onState("Durduruldu")
        }
    }
    fun stop() { running = false }
}
