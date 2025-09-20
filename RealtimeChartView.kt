package com.deep3d.app.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import com.deep3d.upgrade.realtime.Event
import kotlin.math.max
import kotlin.math.min

class RealtimeChartView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
): View(context, attrs) {

    private val maxPoints = 800
    private val data = ArrayList<Float>()
    private val base = ArrayList<Float>()
    private val markers = ArrayList<Pair<Int, Int>>() // index, +1/-1

    private val pLine = Paint().apply { isAntiAlias = true; strokeWidth = 3f; style = Paint.Style.STROKE }
    private val pBase = Paint().apply { isAntiAlias = true; strokeWidth = 2f; style = Paint.Style.STROKE }
    private val pAxis = Paint().apply { isAntiAlias = true; strokeWidth = 1f }
    private val pMarkPos = Paint().apply { isAntiAlias = true; strokeWidth = 6f }
    private val pMarkNeg = Paint().apply { isAntiAlias = true; strokeWidth = 6f }

    fun add(c: Float, baseline: Float) {
        data.add(c); base.add(baseline)
        if (data.size > maxPoints) { data.removeAt(0); base.removeAt(0) }
        invalidate()
    }

    fun mark(ev: Event) {
        markers.add(data.size to if (ev is Event.Positive) +1 else -1)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (data.isEmpty()) return

        val w = width.toFloat(); val h = height.toFloat()
        val n = data.size
        var minV = Float.POSITIVE_INFINITY; var maxV = Float.NEGATIVE_INFINITY
        for (i in 0 until n) {
            minV = min(minV, min(data[i], base[i]))
            maxV = max(maxV, max(data[i], base[i]))
        }
        if (minV == maxV) { minV -= 1f; maxV += 1f }
        val scaleY = h / (maxV - minV)
        val stepX = w / max(1, n-1).toFloat()

        // axis line (zero-ish)
        pAxis.color = 0xFF888888.toInt()
        canvas.drawLine(0f, h/2, w, h/2, pAxis)

        // baseline
        pBase.color = 0xFF00AAFF.toInt()
        var px = 0f; var py = (h - (base[0]-minV)*scaleY)
        for (i in 1 until n) {
            val x = i*stepX
            val y = (h - (base[i]-minV)*scaleY)
            canvas.drawLine(px, py, x, y, pBase); px = x; py = y
        }

        // data
        pLine.color = 0xFFFF8800.toInt()
        px = 0f; py = (h - (data[0]-minV)*scaleY)
        for (i in 1 until n) {
            val x = i*stepX
            val y = (h - (data[i]-minV)*scaleY)
            canvas.drawLine(px, py, x, y, pLine); px = x; py = y
        }

        // markers
        pMarkPos.color = 0xFFFF0000.toInt()
        pMarkNeg.color = 0xFF0066FF.toInt()
        for ((idx, kind) in markers) {
            if (idx < 0 || idx >= n) continue
            val x = idx*stepX
            val y = (h - (data[idx]-minV)*scaleY)
            val paint = if (kind > 0) pMarkPos else pMarkNeg
            canvas.drawCircle(x, y, 8f, paint)
        }
    }
}
