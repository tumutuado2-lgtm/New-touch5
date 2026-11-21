package com.newtouch.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.hypot

/**
 * Área de teste: desenha o rastro dos toques REAIS recebidos pelo sistema
 * e conta quantas vezes o dedo foi pressionado/levantado.
 */
class TrailView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val d = resources.displayMetrics.density
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF1E6BFF.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 4 * d
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF0B3FD1.toInt() }
    private val txt = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF0F1B33.toInt()
        textSize = 14 * d
    }
    private val path = Path()
    private var downs = 0
    private var ups = 0
    private var moves = 0
    private var pressed = false
    private var lastX = 0f
    private var lastY = 0f
    private var dist = 0f

    fun clear() {
        path.reset()
        downs = 0; ups = 0; moves = 0; dist = 0f; pressed = false
        invalidate()
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downs++
                pressed = true
                path.moveTo(e.x, e.y)
                lastX = e.x; lastY = e.y
            }
            MotionEvent.ACTION_MOVE -> {
                moves++
                dist += hypot(e.x - lastX, e.y - lastY)
                path.lineTo(e.x, e.y)
                lastX = e.x; lastY = e.y
                if (moves % 2000 == 0) {
                    path.reset()
                    path.moveTo(e.x, e.y)
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                ups++
                pressed = false
            }
        }
        invalidate()
        return true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawPath(path, line)
        if (pressed) canvas.drawCircle(lastX, lastY, 14 * d, dot)
        val x = 16 * d
        var y = 26 * d
        val step = 20 * d
        canvas.drawText("Pressionado: " + if (pressed) "sim" else "não", x, y, txt); y += step
        canvas.drawText("Toques (pressionar): $downs", x, y, txt); y += step
        canvas.drawText("Levantadas: $ups", x, y, txt); y += step
        canvas.drawText("Movimentos: $moves", x, y, txt); y += step
        canvas.drawText("Distância: ${(dist / d).toInt()} dp", x, y, txt)
    }
}
