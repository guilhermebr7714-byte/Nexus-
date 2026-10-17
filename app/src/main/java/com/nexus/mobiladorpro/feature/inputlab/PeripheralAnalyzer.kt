package com.nexus.mobiladorpro.feature.inputlab

import android.view.KeyEvent
import kotlin.math.roundToInt
import kotlin.math.sqrt

data class MouseCadenceReport(
    val sampleCount: Int,
    val medianP50IntervalMs: Double,
    val p95IntervalMs: Double,
    val stdDevIntervalMs: Double,
    val observedPollingRateHz: Double,
    val packetDropSpikesCount: Int,
    val pressedButtonsMask: Int,
    val lastScrollDeltaV: Float,
    val connectionStabilityVerdict: String
)

data class KeyboardRolloverReport(
    val currentlyPressedKeyCodes: Set<Int>,
    val currentlyPressedLabels: List<String>,
    val maxSimultaneousKeysRecorded: Int,
    val keyRepeatEventsIgnored: Int
)

object PeripheralAnalyzer {
    fun computeMouseCadence(
        eventTimestampsNs: List<Long>,
        buttonsMask: Int,
        scrollV: Float
    ): MouseCadenceReport {
        if (eventTimestampsNs.size < 4) {
            return MouseCadenceReport(eventTimestampsNs.size, 0.0, 0.0, 0.0, 0.0, 0, buttonsMask, scrollV, "Mova o mouse na área de teste.")
        }
        val deltasMs = mutableListOf<Double>()
        for (i in 1 until eventTimestampsNs.size) {
            val dtMs = (eventTimestampsNs[i] - eventTimestampsNs[i - 1]) / 1_000_000.0
            if (dtMs in 0.2..200.0) deltasMs.add(dtMs)
        }
        if (deltasMs.isEmpty()) return MouseCadenceReport(0, 0.0, 0.0, 0.0, 0.0, 0, buttonsMask, scrollV, "Sem intervalos válidos.")
        val sorted = deltasMs.sorted()
        val p50 = (sorted[sorted.size / 2] * 100.0).roundToInt() / 100.0
        val p95 = (sorted[((sorted.size - 1) * 0.95).roundToInt().coerceIn(0, sorted.size - 1)] * 100.0).roundToInt() / 100.0
        val mean = sorted.average()
        val stdDev = (sqrt(sorted.map { (it - mean) * (it - mean) }.average()) * 100.0).roundToInt() / 100.0
        val hz = if (p50 > 0.0) ((1000.0 / p50).coerceAtMost(1000.0) * 10.0).roundToInt() / 10.0 else 0.0
        val drops = deltasMs.count { it > p50 * 2.5 }
        return MouseCadenceReport(eventTimestampsNs.size, p50, p95, stdDev, hz, drops, buttonsMask, scrollV, "Taxa observada: $hz Hz (P50=${p50}ms, P95=${p95}ms).")
    }

    fun evaluateKeyboardMatrix(activeKeys: Set<Int>, maxSimultaneous: Int, repeatCount: Int): KeyboardRolloverReport {
        val labels = activeKeys.map { KeyEvent.keyCodeToString(it).removePrefix("KEYCODE_") }
        return KeyboardRolloverReport(activeKeys, labels, maxSimultaneous, repeatCount)
    }
}
