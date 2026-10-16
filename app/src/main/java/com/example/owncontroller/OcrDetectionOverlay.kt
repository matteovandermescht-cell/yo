package com.example.owncontroller

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.View

/** Draws OCR-detected visible text boxes over the shared screen; does not infer hidden identities. */
class OcrDetectionOverlay(context: Context) : View(context) {
    private val boxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(40, 220, 120)
        style = Paint.Style.STROKE
        strokeWidth = 3f * resources.displayMetrics.density
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 13f * resources.displayMetrics.density
        style = Paint.Style.FILL
    }
    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(220, 15, 70, 45)
        style = Paint.Style.FILL
    }
    private var frameWidth = 1
    private var frameHeight = 1
    private var detections: List<Detection> = emptyList()

    fun update(encoded: String, sourceWidth: Int, sourceHeight: Int) {
        frameWidth = sourceWidth.coerceAtLeast(1)
        frameHeight = sourceHeight.coerceAtLeast(1)
        detections = encoded.lineSequence().mapNotNull { line ->
            val parts = line.split("|", limit = 2)
            val coords = parts.firstOrNull()?.split(",") ?: return@mapNotNull null
            if (coords.size != 4 || parts.size != 2) return@mapNotNull null
            val values = coords.mapNotNull { it.toFloatOrNull() }
            if (values.size != 4) return@mapNotNull null
            Detection(values[0], values[1], values[2], values[3], parts[1].take(90))
        }.take(40).toList()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val sx = width.toFloat() / frameWidth
        val sy = height.toFloat() / frameHeight
        detections.forEach { item ->
            val rect = RectF(item.left * sx, item.top * sy, item.right * sx, item.bottom * sy)
            canvas.drawRect(rect, boxPaint)
            val label = item.text
            val textWidth = labelPaint.measureText(label)
            val labelTop = (rect.top - labelPaint.textSize - 8f).coerceAtLeast(0f)
            canvas.drawRect(rect.left, labelTop, (rect.left + textWidth + 12f).coerceAtMost(width.toFloat()), labelTop + labelPaint.textSize + 8f, backgroundPaint)
            canvas.drawText(label, rect.left + 6f, labelTop + labelPaint.textSize + 1f, labelPaint)
        }
    }

    private data class Detection(val left: Float, val top: Float, val right: Float, val bottom: Float, val text: String)
}
