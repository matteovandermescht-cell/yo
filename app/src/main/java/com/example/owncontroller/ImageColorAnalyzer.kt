package com.example.owncontroller

import android.graphics.Bitmap
import android.graphics.Color

/**
 * Offline color-based image analysis for screenshots selected by the user.
 * This class only measures pixel colors; it does not control a game or device.
 */
data class VisualColorReport(
    val width: Int,
    val height: Int,
    val greenPixels: Long,
    val redPixels: Long,
    val totalPixels: Long
) {
    val greenFraction: Float get() = if (totalPixels == 0L) 0f else greenPixels.toFloat() / totalPixels
    val redFraction: Float get() = if (totalPixels == 0L) 0f else redPixels.toFloat() / totalPixels
}

object ImageColorAnalyzer {
    /**
     * RGB-to-HSV ranges approximate OpenCV's common green/red HSV masks.
     * Android hue is 0..360 degrees; saturation/value are 0..1.
     */
    fun analyze(bitmap: Bitmap, sampleStep: Int = 1): VisualColorReport {
        require(sampleStep >= 1) { "sampleStep must be >= 1" }

        var green = 0L
        var red = 0L
        var sampled = 0L
        val hsv = FloatArray(3)

        var y = 0
        while (y < bitmap.height) {
            var x = 0
            while (x < bitmap.width) {
                val pixel = bitmap.getPixel(x, y)
                Color.colorToHSV(pixel, hsv)
                val hue = hsv[0]
                val saturation = hsv[1]
                val value = hsv[2]

                if (saturation >= 0.20f && value >= 0.20f) {
                    if (hue in 70f..170f) green++
                    if (hue <= 20f || hue >= 340f) red++
                }
                sampled++
                x += sampleStep
            }
            y += sampleStep
        }

        return VisualColorReport(
            width = bitmap.width,
            height = bitmap.height,
            greenPixels = green,
            redPixels = red,
            totalPixels = sampled
        )
    }
}
