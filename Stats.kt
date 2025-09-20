package com.deep3d.upgrade.util

object Stats {
    fun medianSorted(a: List<Double>): Double {
        val n = a.size
        return if (n % 2 == 1) a[n/2] else 0.5 * (a[n/2 - 1] + a[n/2])
    }
    fun median(a: List<Double>): Double {
        if (a.isEmpty()) return 0.0
        val b = a.sorted()
        return medianSorted(b)
    }
    fun mad(values: List<Double>, center: Double): Double {
        if (values.isEmpty()) return 0.0
        val dev = values.map { kotlin.math.abs(it - center) }.sorted()
        return medianSorted(dev)
    }
}
