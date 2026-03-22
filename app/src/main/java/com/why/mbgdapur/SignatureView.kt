package com.why.mbgdapur

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

class SignatureView(context: Context, attrs: AttributeSet) : View(context, attrs) {

    private var path = Path()
    private val paint = Paint().apply {
        isAntiAlias = true
        color = Color.BLACK
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
        strokeWidth = 12f
    }

    private var onSignedListener: (() -> Unit)? = null
    private var isSigned = false

    override fun onDraw(canvas: Canvas) {
        canvas.drawPath(path, paint)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                // KUNCI TOTAL SCROLL: Mencegah layar geser saat tanda tangan
                parent.requestDisallowInterceptTouchEvent(true)
                path.moveTo(x, y)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                path.lineTo(x, y)
                if (!isSigned) {
                    isSigned = true
                    onSignedListener?.invoke()
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                // LEPAS KUNCI SCROLL
                parent.requestDisallowInterceptTouchEvent(false)
            }
            else -> return false
        }
        invalidate()
        return true
    }

    fun clear() {
        path.reset()
        isSigned = false
        invalidate()
    }

    fun setOnSignedListener(listener: () -> Unit) {
        onSignedListener = listener
    }
}