package com.example.owncontroller

/**
 * Safe QA-only visual trigger pipeline.
 *
 * The host QA build supplies measurements from its authorized test capture or
 * in-game instrumentation. This class does not capture other apps, inject
 * touches, or perform gameplay actions. Consumers should log events or assert
 * expected states in an instrumented test.
 */
data class HealthMeasurement(
    val fillFraction: Float,
    val confidence: Float,
    val timestampMs: Long
)

data class FrameMeasurement(
    /** Normalized mean absolute pixel difference, expected in 0f..1f. */
    val normalizedDifference: Float,
    val timestampMs: Long
)

enum class QaTriggerType {
    HEALTH_CRITICAL,
    HEALTH_RECOVERED,
    VISUAL_TIMEOUT
}

data class QaTriggerEvent(
    val type: QaTriggerType,
    val value: Float,
    val timestampMs: Long,
    val detail: String
)

fun interface QaTriggerCallback {
    fun onTriggerReceived(event: QaTriggerEvent)
}

data class QaThresholds(
    val criticalHealthBelow: Float = 0.25f,
    val recoveredHealthAbove: Float = 0.35f,
    val minimumConfidence: Float = 0.90f,
    val confirmSamples: Int = 3,
    val maxMeasurementAgeMs: Long = 250L,
    val unchangedPixelDifferenceAtMost: Float = 0.002f,
    val visualTimeoutMs: Long = 5_000L
) {
    init {
        require(criticalHealthBelow in 0f..1f)
        require(recoveredHealthAbove in 0f..1f)
        require(criticalHealthBelow < recoveredHealthAbove)
        require(minimumConfidence in 0f..1f)
        require(confirmSamples >= 1)
        require(maxMeasurementAgeMs > 0L)
        require(unchangedPixelDifferenceAtMost in 0f..1f)
        require(visualTimeoutMs > 0L)
    }
}

/**
 * Debounces visual measurements and emits events only on confirmed state
 * transitions. Use a monotonic clock for timestampMs (for example elapsedRealtime).
 */
class QaTriggerPipeline(
    private val thresholds: QaThresholds = QaThresholds(),
    private val callback: QaTriggerCallback
) {
    private enum class HealthState { UNKNOWN, NORMAL, CANDIDATE_CRITICAL, CRITICAL, CANDIDATE_RECOVERY }

    private var healthState = HealthState.UNKNOWN
    private var healthCandidateSamples = 0
    private var lastHealthTimestampMs = Long.MIN_VALUE

    private var lastFrameTimestampMs = Long.MIN_VALUE
    private var unchangedSinceMs: Long? = null
    private var timeoutReported = false

    @Synchronized
    fun onHealthMeasurement(measurement: HealthMeasurement, nowMs: Long) {
        if (measurement.timestampMs <= lastHealthTimestampMs) return
        lastHealthTimestampMs = measurement.timestampMs

        val age = nowMs - measurement.timestampMs
        if (age < 0L || age > thresholds.maxMeasurementAgeMs) return
        if (!measurement.fillFraction.isFinite() ||
            measurement.fillFraction !in 0f..1f ||
            !measurement.confidence.isFinite() ||
            measurement.confidence < thresholds.minimumConfidence
        ) return

        when (healthState) {
            HealthState.UNKNOWN, HealthState.NORMAL -> {
                if (measurement.fillFraction < thresholds.criticalHealthBelow) {
                    healthState = HealthState.CANDIDATE_CRITICAL
                    healthCandidateSamples = 1
                    confirmCriticalIfReady(measurement)
                } else {
                    healthState = HealthState.NORMAL
                    healthCandidateSamples = 0
                }
            }
            HealthState.CANDIDATE_CRITICAL -> {
                if (measurement.fillFraction < thresholds.criticalHealthBelow) {
                    healthCandidateSamples++
                    confirmCriticalIfReady(measurement)
                } else {
                    healthState = HealthState.NORMAL
                    healthCandidateSamples = 0
                }
            }
            HealthState.CRITICAL, HealthState.CANDIDATE_RECOVERY -> {
                if (measurement.fillFraction > thresholds.recoveredHealthAbove) {
                    if (healthState != HealthState.CANDIDATE_RECOVERY) {
                        healthState = HealthState.CANDIDATE_RECOVERY
                        healthCandidateSamples = 1
                    } else {
                        healthCandidateSamples++
                    }
                    if (healthCandidateSamples >= thresholds.confirmSamples) {
                        healthState = HealthState.NORMAL
                        healthCandidateSamples = 0
                        callback.onTriggerReceived(
                            QaTriggerEvent(
                                QaTriggerType.HEALTH_RECOVERED,
                                measurement.fillFraction,
                                measurement.timestampMs,
                                "Health recovery confirmed by consecutive valid samples"
                            )
                        )
                    }
                } else {
                    healthState = HealthState.CRITICAL
                    healthCandidateSamples = 0
                }
            }
        }
    }

    private fun confirmCriticalIfReady(measurement: HealthMeasurement) {
        if (healthCandidateSamples < thresholds.confirmSamples) return
        healthState = HealthState.CRITICAL
        healthCandidateSamples = 0
        callback.onTriggerReceived(
            QaTriggerEvent(
                QaTriggerType.HEALTH_CRITICAL,
                measurement.fillFraction,
                measurement.timestampMs,
                "Critical health confirmed by consecutive valid samples"
            )
        )
    }

    /**
     * Feed one measurement per newly captured frame. A small difference is
     * treated as unchanged; meaningful motion resets the timer.
     */
    @Synchronized
    fun onFrameMeasurement(measurement: FrameMeasurement) {
        if (measurement.timestampMs <= lastFrameTimestampMs) return
        lastFrameTimestampMs = measurement.timestampMs
        if (!measurement.normalizedDifference.isFinite() ||
            measurement.normalizedDifference !in 0f..1f
        ) return

        if (measurement.normalizedDifference <= thresholds.unchangedPixelDifferenceAtMost) {
            if (unchangedSinceMs == null) unchangedSinceMs = measurement.timestampMs
            val elapsed = measurement.timestampMs - (unchangedSinceMs ?: measurement.timestampMs)
            if (!timeoutReported && elapsed >= thresholds.visualTimeoutMs) {
                timeoutReported = true
                callback.onTriggerReceived(
                    QaTriggerEvent(
                        QaTriggerType.VISUAL_TIMEOUT,
                        measurement.normalizedDifference,
                        measurement.timestampMs,
                        "No meaningful visual change for ${elapsed}ms"
                    )
                )
            }
        } else {
            unchangedSinceMs = null
            timeoutReported = false
        }
    }

    /** Reset between test cases so state cannot leak between runs. */
    @Synchronized
    fun reset() {
        healthState = HealthState.UNKNOWN
        healthCandidateSamples = 0
        lastHealthTimestampMs = Long.MIN_VALUE
        lastFrameTimestampMs = Long.MIN_VALUE
        unchangedSinceMs = null
        timeoutReported = false
    }
}
