package com.deep3d.upgrade.grid

object Filters {
    fun median3(a: Double, b: Double, c: Double): Double {
        val arr = doubleArrayOf(a,b,c).sorted()
        return arr[1]
    }
    fun medianList(xs: List<Double>): Double {
        if (xs.isEmpty()) return 0.0
        val s = xs.sorted()
        val n = s.size
        return if (n%2==1) s[n/2] else 0.5*(s[n/2-1]+s[n/2])
    }
}
