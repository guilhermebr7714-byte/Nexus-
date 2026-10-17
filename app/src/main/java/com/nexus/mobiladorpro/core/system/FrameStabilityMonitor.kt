package com.nexus.mobiladorpro.core.system

import com.nexus.mobiladorpro.core.common.NexusResult
import com.nexus.mobiladorpro.core.shizuku.ShizukuShellExecutor
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt
import kotlin.math.sqrt

data class FrameStabilitySnapshot(
    val targetPackage: String,
    val dataSourceDescription: String,
    val sampleCount: Int,
    val observedAverageFps: Double,
    val medianFrameTimeMs: Double,
    val p95FrameTimeMs: Double,
    val jankFrameCount: Int,
    val jankPercentage: Double,
    val frameTimeStdDevMs: Double,
    val timestampMs: Long
)

@Singleton
class FrameStabilityMonitor @Inject constructor(
    private val shellExecutor: ShizukuShellExecutor
) {
    suspend fun capturePackageFrameStats(packageName: String): NexusResult<FrameStabilitySnapshot> {
        val allowedPackages = setOf("com.dts.freefireth", "com.dts.freefiremax", "com.nexus.mobiladorpro")
        val safePkg = if (packageName in allowedPackages) packageName else "com.dts.freefireth"

        val result = shellExecutor.executeReadOnlyDiagnostic(
            listOf("dumpsys", "gfxinfo", safePkg, "framestats")
        )

        return when (result) {
            is NexusResult.Error -> result
            is NexusResult.Success -> {
                val parsedDurationsMs = parseGfxInfoProfileData(result.data)
                if (parsedDurationsMs.isEmpty()) {
                    NexusResult.Success(
                        FrameStabilitySnapshot(
                            targetPackage = safePkg,
                            dataSourceDescription = "dumpsys gfxinfo (Sem amostras recentes no buffer — abra uma partida e repita a leitura)",
                            sampleCount = 0,
                            observedAverageFps = 0.0,
                            medianFrameTimeMs = 0.0,
                            p95FrameTimeMs = 0.0,
                            jankFrameCount = 0,
                            jankPercentage = 0.0,
                            frameTimeStdDevMs = 0.0,
                            timestampMs = System.currentTimeMillis()
                        )
                    )
                } else {
                    NexusResult.Success(
                        computeMetricsFromFrameTimes(
                            safePkg,
                            "dumpsys gfxinfo framestats (Medição Real via Shizuku)",
                            parsedDurationsMs
                        )
                    )
                }
            }
        }
    }

    fun computeMetricsFromFrameTimes(
        targetName: String,
        sourceDesc: String,
        frameTimesMs: List<Double>
    ): FrameStabilitySnapshot {
        if (frameTimesMs.isEmpty()) {
            return FrameStabilitySnapshot(targetName, sourceDesc, 0, 0.0, 0.0, 0.0, 0, 0.0, 0.0, System.currentTimeMillis())
        }
        val sorted = frameTimesMs.sorted()
        val count = sorted.size
        val meanMs = sorted.average()
        val avgFps = if (meanMs > 0.0) (1000.0 / meanMs).coerceAtMost(240.0) else 0.0
        val medianMs = sorted[count / 2]
        val p95Index = ((count - 1) * 0.95).roundToInt().coerceIn(0, count - 1)
        val p95Ms = sorted[p95Index]

        val jankCount = sorted.count { it > 16.8 }
        val jankPct = (jankCount.toDouble() / count) * 100.0

        val variance = sorted.map { (it - meanMs) * (it - meanMs) }.average()
        val stdDev = sqrt(variance)

        return FrameStabilitySnapshot(
            targetPackage = targetName,
            dataSourceDescription = sourceDesc,
            sampleCount = count,
            observedAverageFps = (avgFps * 10.0).roundToInt() / 10.0,
            medianFrameTimeMs = (medianMs * 100.0).roundToInt() / 100.0,
            p95FrameTimeMs = (p95Ms * 100.0).roundToInt() / 100.0,
            jankFrameCount = jankCount,
            jankPercentage = (jankPct * 10.0).roundToInt() / 10.0,
            frameTimeStdDevMs = (stdDev * 100.0).roundToInt() / 100.0,
            timestampMs = System.currentTimeMillis()
        )
    }

    private fun parseGfxInfoProfileData(rawOutput: String): List<Double> {
        val durationsMs = mutableListOf<Double>()
        var insideProfileData = false
        for (line in rawOutput.lineSequence()) {
            val trimmed = line.trim()
            if (trimmed == "---PROFILEDATA---") {
                insideProfileData = !insideProfileData
                continue
            }
            if (insideProfileData && trimmed.startsWith("0,")) {
                val cols = trimmed.split(",")
                if (cols.size > 13) {
                    val intendedVsyncNs = cols[1].toLongOrNull() ?: continue
                    val frameCompletedNs = cols[13].toLongOrNull() ?: continue
                    if (frameCompletedNs > intendedVsyncNs && intendedVsyncNs > 0L) {
                        val durationMs = (frameCompletedNs - intendedVsyncNs) / 1_000_000.0
                        if (durationMs in 1.0..500.0) {
                            durationsMs.add(durationMs)
                        }
                    }
                }
            }
        }
        return durationsMs
    }
}
