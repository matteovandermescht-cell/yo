package com.example.owncontroller

import android.graphics.Bitmap
import android.graphics.Color

/**
 * Offline QA detector for a yellow menu-start signal in a supplied screenshot.
 *
 * This class does not capture the screen, inject touch events, or control a game.
 * Call observe() with screenshots supplied by a test harness or selected by the user.
 */
enum class QaMatchState {
    STATE_WAITING_FOR_MATCH,
    STATE_MATCH_ACTIVE
}

data class MenuSignalReport(
    val yellowPixels: Long,
    val sampledPixels: Long,
    val yellowFraction: Float,
    val stableFrames: Int,
    val state: QaMatchState,
    val transitionedToMatch: Boolean
)

class MenuStartSignalDetector(
    private val requiredStableFrames: Int = 3,
    private val minimumYellowFraction: Float = 0.12f
) {
    private var stableFrames = 0
    private var state = QaMatchState.STATE_WAITING_FOR_MATCH

    init {
        require(requiredStableFrames >= 1) { "requiredStableFrames must be >= 1" }
        require(minimumYellowFraction in 0f..1f) { "minimumYellowFraction must be in 0..1" }
    }

    /**
     * Checks only the normalized ROI x=80..95%, y=80..95% of the supplied bitmap.
     * A transition is reported once, after the yellow signal persists across frames.
     */
    fun observe(bitmap: Bitmap, sampleStep: Int = 2): MenuSignalReport {
        require(sampleStep >= 1) { "sampleStep must be >= 1" }

        if (state == QaMatchState.STATE_MATCH_ACTIVE) {
            return MenuSignalReport(0, 0, 0f, stableFrames, state, false)
        }

        val left = (bitmap.width * 0.80f).toInt().coerceIn(0, bitmap.width)
        val right = (bitmap.width * 0.95f).toInt().coerceIn(left, bitmap.width)
        val top = (bitmap.height * 0.80f).toInt().coerceIn(0, bitmap.height)
        val bottom = (bitmap.height * 0.95f).toInt().coerceIn(top, bitmap.height)

        var yellow = 0L
        var sampled = 0L
        val hsv = FloatArray(3)
        var y = top
        while (y < bottom) {
            var x = left
            while (x < right) {
                Color.colorToHSV(bitmap.getPixel(x, y), hsv)
                val hue = hsv[0]
                val saturation = hsv[1]
                val value = hsv[2]
                if (hue in 38f..68f && saturation >= 0.45f && value >= 0.45f) {
                    yellow++
                }
                sampled++
                x += sampleStep
            }
            y += sampleStep
        }

        val fraction = if (sampled == 0L) 0f else yellow.toFloat() / sampled
        if (fraction >= minimumYellowFraction) {
            stableFrames++
        } else {
            stableFrames = 0
        }

        val transitioned = stableFrames >= requiredStableFrames
        if (transitioned) state = QaMatchState.STATE_MATCH_ACTIVE

        return MenuSignalReport(
            yellowPixels = yellow,
            sampledPixels = sampled,
            yellowFraction = fraction,
            stableFrames = stableFrames,
            state = state,
            transitionedToMatch = transitioned
        )
    }

    fun reset() {
        stableFrames = 0
        state = QaMatchState.STATE_WAITING_FOR_MATCH
    }
}
