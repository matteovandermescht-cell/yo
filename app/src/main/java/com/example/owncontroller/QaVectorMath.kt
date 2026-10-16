package com.example.owncontroller

import kotlin.math.hypot

/**
 * Pure coordinate math for offline QA/mock-screen tests.
 * This class intentionally does not inject Android MotionEvents or control a live game.
 */
object QaVectorMath {
    data class Point(val x: Float, val y: Float)
    data class Vector(val x: Float, val y: Float) {
        fun length(): Float = hypot(x, y)

        fun normalized(): Vector {
            val magnitude = length()
            return if (magnitude == 0f) Vector(0f, 0f)
            else Vector(x / magnitude, y / magnitude)
        }

        fun scaled(amount: Float): Vector = Vector(x * amount, y * amount)
        fun reversed(): Vector = Vector(-x, -y)
    }

    data class ScreenSize(val width: Int, val height: Int) {
        init {
            require(width > 0 && height > 0) { "Screen dimensions must be positive." }
        }
    }

    fun playerAnchor(screen: ScreenSize) = Point(
        screen.width * 0.50f,
        screen.height * 0.50f
    )

    fun interactionAnchor(screen: ScreenSize) = Point(
        screen.width * 0.82f,
        screen.height * 0.82f
    )

    fun movementAnchor(screen: ScreenSize) = Point(
        screen.width * 0.17f,
        screen.height * 0.78f
    )

    /** Returns target minus player anchor, for display/logging in mock QA. */
    fun targetVector(screen: ScreenSize, target: Point): Vector {
        val anchor = playerAnchor(screen)
        return Vector(target.x - anchor.x, target.y - anchor.y)
    }

    /** Returns the opposite direction for a detected hazard in mock QA. */
    fun escapeVector(screen: ScreenSize, hazard: Point): Vector =
        targetVector(screen, hazard).reversed()

    /** Calculates a mock endpoint from an anchor and a direction; does not dispatch touch events. */
    fun mockEndpoint(anchor: Point, direction: Vector, distancePx: Float): Point {
        require(distancePx >= 0f) { "Distance must not be negative." }
        val unit = direction.normalized()
        return Point(
            (anchor.x + unit.x * distancePx).coerceAtLeast(0f),
            (anchor.y + unit.y * distancePx).coerceAtLeast(0f)
        )
    }
}
