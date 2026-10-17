package com.nexus.mobiladorpro.feature.capalab

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sqrt

data class CapaAttemptRecord(
    val targetSpawnTimeMs: Long,
    val movementStartTimeMs: Long,
    val shotReleaseTimeMs: Long,
    val startX: Float,
    val startY: Float,
    val headTargetX: Float,
    val headTargetY: Float,
    val headRadiusPx: Float,
    val endX: Float,
    val endY: Float
)

data class CapaTrainingEvaluation(
    val totalAttempts: Int,
    val headHitsCount: Int,
    val accuracyPercent: Double,
    val meanDistanceToCenterPx: Double,
    val meanVisualReactionTimeMs: Double,
    val meanMotorExecutionTimeMs: Double,
    val meanTotalTaskTimeMs: Double,
    val meanVerticalOvershootPx: Double,
    val meanVerticalUndershootPx: Double,
    val verticalConsistencyStdDevPx: Double,
    val horizontalSpreadStdDevPx: Double,
    val explainableHypotheses: List<String>
)

object CapaRecommendationEngine {
    fun evaluateSession(attempts: List<CapaAttemptRecord>): CapaTrainingEvaluation {
        if (attempts.isEmpty()) {
            return CapaTrainingEvaluation(0, 0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, listOf("Complete as tentativas."))
        }
        var hits = 0
        val dists = mutableListOf<Double>()
        val reacts = mutableListOf<Double>()
        val motors = mutableListOf<Double>()
        val overs = mutableListOf<Double>()
        val unders = mutableListOf<Double>()
        val vErrs = mutableListOf<Double>()
        val hErrs = mutableListOf<Double>()

        for (att in attempts) {
            val dx = (att.endX - att.headTargetX).toDouble()
            val dyFromHead = (att.headTargetY - att.endY).toDouble()
            val dist = hypot(dx, dyFromHead)
            dists.add(dist)
            if (dist <= att.headRadiusPx) hits++
            if (dyFromHead > att.headRadiusPx) overs.add(dyFromHead - att.headRadiusPx)
            else if (dyFromHead < -att.headRadiusPx) unders.add(abs(dyFromHead) - att.headRadiusPx)
            vErrs.add(dyFromHead)
            hErrs.add(dx)
            reacts.add((att.movementStartTimeMs - att.targetSpawnTimeMs).coerceAtLeast(10L).toDouble())
            motors.add((att.shotReleaseTimeMs - att.movementStartTimeMs).coerceAtLeast(10L).toDouble())
        }

        val count = attempts.size
        val acc = ((hits.toDouble() / count) * 1000.0).roundToInt() / 10.0
        val avgOver = if (overs.isNotEmpty()) (overs.average() * 10.0).roundToInt() / 10.0 else 0.0
        val avgUnder = if (unders.isNotEmpty()) (unders.average() * 10.0).roundToInt() / 10.0 else 0.0
        val vMean = vErrs.average()
        val vStd = (sqrt(vErrs.map { (it - vMean) * (it - vMean) }.average()) * 10.0).roundToInt() / 10.0
        val hMean = hErrs.average()
        val hStd = (sqrt(hErrs.map { (it - hMean) * (it - hMean) }.average()) * 10.0).roundToInt() / 10.0

        val hypotheses = buildList {
            if (overs.size > count * 0.38 && avgOver > 12.0) {
                add("Overshoot vertical (${avgOver}px acima da cabeça): Sugere-se testar redução de -3 a -6 pontos na Sensibilidade Geral/Red Dot.")
            }
            if (unders.size > count * 0.38 && avgUnder > 12.0) {
                add("Undershoot vertical (${avgUnder}px abaixo da cabeça): Mira parando no peito. Sugere-se aumento de +4 a +7 pontos na sensibilidade.")
            }
            if (isEmpty()) {
                add("Calibração consistente ($acc% de acerto). Mantenha sua configuração atual.")
            }
        }

        return CapaTrainingEvaluation(
            totalAttempts = count,
            headHitsCount = hits,
            accuracyPercent = acc,
            meanDistanceToCenterPx = (dists.average() * 10.0).roundToInt() / 10.0,
            meanVisualReactionTimeMs = (reacts.average() * 10.0).roundToInt() / 10.0,
            meanMotorExecutionTimeMs = (motors.average() * 10.0).roundToInt() / 10.0,
            meanTotalTaskTimeMs = ((reacts.average() + motors.average()) * 10.0).roundToInt() / 10.0,
            meanVerticalOvershootPx = avgOver,
            meanVerticalUndershootPx = avgUnder,
            verticalConsistencyStdDevPx = vStd,
            horizontalSpreadStdDevPx = hStd,
            explainableHypotheses = hypotheses
        )
    }
}
