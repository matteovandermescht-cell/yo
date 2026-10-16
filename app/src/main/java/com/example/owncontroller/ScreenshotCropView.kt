package com.example.owncontroller

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import kotlin.math.max
import kotlin.math.min

/** Lets the user drag a rectangle around an object in a screenshot. */
class ScreenshotCropView(context: Context, private val bitmap: Bitmap) : View(context) {
    private val imagePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val selectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xCC00AA55.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }
    private var startX = 0f
    private var startY = 0f
    private var endX = 0f
    private var endY = 0f
    private var selecting = false
    private var imageRect = RectF()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val scale = min(width.toFloat() / bitmap.width, height.toFloat() / bitmap.height)
        val drawWidth = bitmap.width * scale
        val drawHeight = bitmap.height * scale
        val left = (width - drawWidth) / 2f
        val top = (height - drawHeight) / 2f
        imageRect = RectF(left, top, left + drawWidth, top + drawHeight)
        canvas.drawBitmap(bitmap, null, imageRect, imagePaint)
        if (selecting || (endX != startX && endY != startY)) {
            canvas.drawRect(selectionRect(), selectionPaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                startX = event.x.coerceIn(imageRect.left, imageRect.right)
                startY = event.y.coerceIn(imageRect.top, imageRect.bottom)
                endX = startX
                endY = startY
                selecting = true
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                endX = event.x.coerceIn(imageRect.left, imageRect.right)
                endY = event.y.coerceIn(imageRect.top, imageRect.bottom)
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                endX = event.x.coerceIn(imageRect.left, imageRect.right)
                endY = event.y.coerceIn(imageRect.top, imageRect.bottom)
                selecting = false
                invalidate()
                return true
            }
        }
        return true
    }

    fun cropSelection(): Bitmap? {
        if (imageRect.width() <= 0f || imageRect.height() <= 0f) return null
        val rect = selectionRect()
        if (rect.width() < 8f || rect.height() < 8f) return null
        val left = (((rect.left - imageRect.left) / imageRect.width()) * bitmap.width).toInt().coerceIn(0, bitmap.width - 1)
        val top = (((rect.top - imageRect.top) / imageRect.height()) * bitmap.height).toInt().coerceIn(0, bitmap.height - 1)
        val right = (((rect.right - imageRect.left) / imageRect.width()) * bitmap.width).toInt().coerceIn(left + 1, bitmap.width)
        val bottom = (((rect.bottom - imageRect.top) / imageRect.height()) * bitmap.height).toInt().coerceIn(top + 1, bitmap.height)
        return Bitmap.createBitmap(bitmap, left, top, max(1, right - left), max(1, bottom - top))
    }

    private fun selectionRect() = RectF(min(startX, endX), min(startY, endY), max(startX, endX), max(startY, endY))
}
