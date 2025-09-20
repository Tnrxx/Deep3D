package com.deep3d.upgrade.grid

data class Cell(var c: Double? = null, var depthM: Double? = null)

class GridStore(val cols: Int, val rows: Int) {
    private val data = Array(rows) { Array(cols) { Cell() } }
    fun put(row: Int, col: Int, c: Double?, depthM: Double? = null) {
        if (row in 0 until rows && col in 0 until cols) {
            data[row][col].c = c; data[row][col].depthM = depthM
        }
    }
    fun get(row: Int, col: Int): Cell = data[row][col]
    fun filledCount(): Int = data.sumOf { r -> r.count { it.c != null } }
}
