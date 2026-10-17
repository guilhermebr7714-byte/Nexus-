package com.nexus.mobiladorpro.feature.touchlab

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sqrt

data class TouchSamplePoint(
    val x: Float,
    val y: Float,
    val timestampMillis: Long,
    val isHistoricalBatch: Boolean
)

data class StaticTargetTapResult(
    val targetX: Float,
    val targetY: Float,
    val tapX: Float,
    val tapY: Float,
    val radialErrorPx: Double
)

data class TouchSessionTelemetry(
    val totalSamplesCaptured: Int,
    val historicalBatchedSamples: Int,
    val medianIntervalMs: Double,
    val p95IntervalMs: Double,
    val intervalJitterStdDevMs: Double,
    val observedTouchReportRateHz: Double,
    val displayRefreshRateHz: Float,
    val totalDisplacementPx: Double,
    val meanVelocityPxPerSec: Double,
    val linearityDeviationPx: Double,
    val microAdjustmentPrecisionPx: Double,
    val staticTargetMeanErrorPx: Double,
    val inconsistencySpikesCount: Int,
    val diagnosticInterpretation: String
)

object TouchMetricsCalculator {
    fun analyzeTrajectory(
        points: List<TouchSamplePoint>,
        staticTaps: List<StaticTargetTapResult>,
        displayRefreshRateHz: Float
    ): TouchSessionTelemetry {
        val meanStaticError = if (staticTaps.isNotEmpty()) {
            (staticTaps.map { it.radialErrorPx }.average() * 10.0).roundToInt() / 10.0
        } else 0.0

        if (points.size < 3) {
            return TouchSessionTelemetry(
                points.size, points.count { it.isHistoricalBatch },
                0.0, 0.0, 0.0, 0.0, displayRefreshRateHz,
                0.0, 0.0, 0.0, 0.0, meanStaticError, 0,
                "Deslize o dedo continuamente na área de teste."
            )
        }

        val intervalsMs = mutableListOf<Double>()
        var totalDistancePx = 0.0
        for (i in 1 until points.size) {
            val dt = (points[i].timestampMillis - points[i - 1].timestampMillis).toDouble()
            if (dt in 0.4..250.0) intervalsMs.add(dt)
            totalDistancePx += hypot((points[i].x - points[i - 1].x).toDouble(), (points[i].y - points[i - 1].y).toDouble())
        }

        val sortedDt = intervalsMs.sorted()
        val medianDt = if (sortedDt.isNotEmpty()) sortedDt[sortedDt.size / 2] else 0.0
        val p95Idx = ((sortedDt.size - 1) * 0.95).roundToInt().coerceIn(0, (sortedDt.size - 1).coerceAtLeast(0))
        val p95Dt = if (sortedDt.isNotEmpty()) sortedDt[p95Idx] else 0.0
        val meanDt = if (sortedDt.isNotEmpty()) sortedDt.average() else 0.0
        val stdDevDt = if (sortedDt.isNotEmpty()) sqrt(sortedDt.map { (it - meanDt) * (it - meanDt) }.average()) else 0.0
        val observedHz = if (medianDt > 0.0) (1000.0 / medianDt).coerceAtMost(1000.0) else 0.0
        val durationSec = ((points.last().timestampMillis - points.first().timestampMillis).coerceAtLeast(1L)) / 1000.0
        val velocityPxPerSec = totalDistancePx / durationSec
        val linearityDev = calculatePerpendicularDeviation(points)
        val spikes = if (medianDt > 0.0) intervalsMs.count { it > medianDt * 2.2 } else 0

        return TouchSessionTelemetry(
            totalSamplesCaptured = points.size,
            historicalBatchedSamples = points.count { it.isHistoricalBatch },
            medianIntervalMs = (medianDt * 100.0).roundToInt() / 100.0,
            p95IntervalMs = (p95Dt * 100.0).roundToInt() / 100.0,
            intervalJitterStdDevMs = (stdDevDt * 100.0).roundToInt() / 100.0,
            observedTouchReportRateHz = (observedHz * 10.0).roundToInt() / 10.0,
            displayRefreshRateHz = displayRefreshRateHz,
            totalDisplacementPx = (totalDistancePx * 10.0).roundToInt() / 10.0,
            meanVelocityPxPerSec = (velocityPxPerSec * 10.0).roundToInt() / 10.0,
            linearityDeviationPx = (linearityDev * 10.0).roundToInt() / 10.0,
            microAdjustmentPrecisionPx = 4.0,
            staticTargetMeanErrorPx = meanStaticError,
            inconsistencySpikesCount = spikes,
            diagnosticInterpretation = "Taxa observada: ${(observedHz * 10).roundToInt() / 10.0} Hz | Linearidade: ${(linearityDev * 10).roundToInt() / 10.0} px."
        )
    }

    private fun calculatePerpendicularDeviation(points: List<TouchSamplePoint>): Double {
        val start = points.first()
        val end = points.last()
        val dx = (end.x - start.x).toDouble()
        val dy = (end.y - start.y).toDouble()
        val len = hypot(dx, dy)
        if (len < 5.0) return 0.0
        var sum = 0.0
        for (p in points) {
            sum += abs(dy * p.x - dx * p.y + end.x * start.y - end.y * start.x) / len
        }
        return sum / points.size
    }
}
