package com.deep3d.upgrade.realtime

import com.deep3d.upgrade.util.Stats

sealed class Event(val delta: Double) {
    class Positive(d: Double): Event(d) // yoğun/dolu
    class Negative(d: Double): Event(d) // boşluk/oyuk
}

class RealtimeAnalyzer(var N: Int = 101, var k: Double = 3.0) {
    private val win = ArrayDeque<Double>()
    var baseline = 0.0; var sigma = 1.0

    fun reset() { win.clear(); baseline = 0.0; sigma = 1.0 }

    fun push(c: Double): Event? {
        win += c; if (win.size > N) win.removeFirst()
        if (win.size >= N/2) {
            val b = Stats.median(win.toList())
            val s = 1.4826 * Stats.mad(win.toList(), b)
            // drift'e karşı yavaş uyum
            baseline = 0.98*baseline + 0.02*b
            sigma    = 0.98*sigma    + 0.02*s
        }
        val d = c - baseline
        return when {
            d >  k * sigma -> Event.Positive(d)
            d < -k * sigma -> Event.Negative(d)
            else -> null
        }
    }
}
