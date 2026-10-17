package com.nexus.mobiladorpro

import com.nexus.mobiladorpro.feature.capalab.CapaAttemptRecord
import com.nexus.mobiladorpro.feature.capalab.CapaRecommendationEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TouchAndCapaMetricsTest {

    @Test
    fun capaOvershootGeneratesGradualReductionHypothesis() {
        val attempts = (1..10).map {
            CapaAttemptRecord(
                targetSpawnTimeMs = 1000L,
                movementStartTimeMs = 1220L,
                shotReleaseTimeMs = 1400L,
                startX = 400f,
                startY = 500f,
                headTargetX = 400f,
                headTargetY = 200f,
                headRadiusPx = 25f,
                endX = 400f,
                endY = 140f // Passou 60px acima da cabeça (overshoot = 35px além do raio)
            )
        }
        val eval = CapaRecommendationEngine.evaluateSession(attempts)
        assertEquals(0, eval.headHitsCount)
        assertTrue(eval.meanVerticalOvershootPx > 20.0)
        assertTrue(eval.explainableHypotheses.any { it.contains("redução", ignoreCase = true) })
    }
}
