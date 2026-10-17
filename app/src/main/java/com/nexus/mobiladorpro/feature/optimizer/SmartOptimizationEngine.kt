package com.nexus.mobiladorpro.feature.optimizer

import com.nexus.mobiladorpro.core.common.ErrorCode
import com.nexus.mobiladorpro.core.common.NexusResult
import com.nexus.mobiladorpro.core.shizuku.ExecutionAvailability
import com.nexus.mobiladorpro.core.shizuku.PrivilegedOperationCatalog
import com.nexus.mobiladorpro.core.shizuku.PrivilegedOperationSpec
import com.nexus.mobiladorpro.core.shizuku.ShizukuConnectionState
import com.nexus.mobiladorpro.core.shizuku.ShizukuManager
import com.nexus.mobiladorpro.core.shizuku.ShizukuShellExecutor
import com.nexus.mobiladorpro.core.storage.OperationLogDao
import com.nexus.mobiladorpro.core.storage.OperationLogEntity
import com.nexus.mobiladorpro.core.system.DeviceDiagnosticReport
import com.nexus.mobiladorpro.core.system.SystemDiagnosticsProvider
import javax.inject.Inject
import javax.inject.Singleton

enum class OptimizationMode(val title: String, val summary: String) {
    SAFE("Seguro", "Apenas recomendações e ajustes de baixo risco e totalmente reversíveis."),
    COMPETITIVE("Competitivo", "Ajustes voltados à estabilidade de quadros, resposta visual e compilação AOT do Free Fire."),
    CUSTOM("Personalizado", "Seleção individual de cada operação com exibição explícita de efeitos e riscos.")
}

enum class ThermalAndPowerProfile(val title: String, val guidance: String) {
    BALANCED("Uso Equilibrado", "Prioriza manter a temperatura controlada para evitar throttling no fim da partida."),
    COMPETITIVE_PEAK("Competitivo Sustentado", "Solicita Fixed Performance Mode e taxa máxima quando a temperatura permite.")
}

data class RamHistorySample(
    val timestampMs: Long,
    val availableRamMb: Long,
    val usagePercent: Int,
    val label: String
)

data class EvidenceScoreBreakdown(
    val overallReadinessScore: Int,
    val thermalHealthScore: Int,
    val memoryHeadroomScore: Int,
    val displayCadenceScore: Int,
    val inputRigReadinessScore: Int,
    val calculationExplanation: String
)

data class EvidenceBasedRecommendation(
    val operationSpec: PrivilegedOperationSpec,
    val availabilityLabel: String,
    val recommendedParameter: String,
    val problemIdentified: String,
    val availableEvidence: String,
    val proposedChange: String,
    val expectedBenefit: String,
    val riskAssessment: String,
    val isSelectedByDefault: Boolean,
    val canExecuteNow: Boolean,
    val blockingReason: String?
)

data class BenchmarkComparisonReport(
    val baselineReport: DeviceDiagnosticReport,
    val postOptimizationReport: DeviceDiagnosticReport,
    val scoreBefore: EvidenceScoreBreakdown,
    val scoreAfter: EvidenceScoreBreakdown,
    val appliedOperationsCount: Int,
    val verifiedOperationsCount: Int,
    val ramDeltaMb: Long,
    val refreshRateBeforeHz: Float,
    val refreshRateAfterHz: Float,
    val summaryObservations: List<String>,
    val confoundingFactorsWarning: String
)

@Singleton
class SmartOptimizationEngine @Inject constructor(
    private val diagnosticsProvider: SystemDiagnosticsProvider,
    private val shizukuManager: ShizukuManager,
    private val shellExecutor: ShizukuShellExecutor,
    private val operationLogDao: OperationLogDao
) {
    fun computeEvidenceBasedReadiness(report: DeviceDiagnosticReport): EvidenceScoreBreakdown {
        val thermalScore = when {
            report.batteryTempCelsius <= 37.5f && report.thermalStatusCode == 0 -> 25
            report.batteryTempCelsius <= 40.5f && report.thermalStatusCode <= 1 -> 18
            report.batteryTempCelsius <= 43.0f -> 10
            else -> 3
        }
        val memoryScore = when {
            !report.lowMemoryPressure && report.ramUsagePercent <= 68 -> 25
            !report.lowMemoryPressure && report.ramUsagePercent <= 78 -> 19
            !report.lowMemoryPressure && report.ramUsagePercent <= 86 -> 12
            else -> 5
        }
        val maxHz = report.supportedRefreshRatesHz.maxOrNull() ?: 60f
        val displayScore = if (report.isPowerSaveMode) 8 else if (report.currentRefreshRateHz >= maxHz - 1f) 25 else 15

        val hasMouse = report.connectedPeripherals.any { it.isMouse }
        val hasKeyboard = report.connectedPeripherals.any { it.isKeyboard }
        val hasMapper = report.installedMappers.isNotEmpty()
        val inputScore = (if (hasMouse) 10 else 0) + (if (hasKeyboard) 10 else 0) + (if (hasMapper) 5 else 0)

        val total = thermalScore + memoryScore + displayScore + inputScore
        return EvidenceScoreBreakdown(
            overallReadinessScore = total,
            thermalHealthScore = thermalScore,
            memoryHeadroomScore = memoryScore,
            displayCadenceScore = displayScore,
            inputRigReadinessScore = inputScore,
            calculationExplanation = "Térmico ($thermalScore/25), Memória ($memoryScore/25), Display ($displayScore/25), Periféricos ($inputScore/25)."
        )
    }

    fun generateRecommendations(
        report: DeviceDiagnosticReport,
        mode: OptimizationMode,
        thermalProfile: ThermalAndPowerProfile
    ): List<EvidenceBasedRecommendation> {
        val isShizukuReady = shizukuManager.connectionState.value is ShizukuConnectionState.OperationReady
        val targetGamePkg = "com.dts.freefireth"
        val list = mutableListOf<EvidenceBasedRecommendation>()

        val animSpec = PrivilegedOperationCatalog.ANIMATION_SCALE_REDUCTION
        list.add(
            EvidenceBasedRecommendation(
                operationSpec = animSpec,
                availabilityLabel = formatAvailability(animSpec.availability),
                recommendedParameter = "0.5",
                problemIdentified = "Atraso visual em transições de janela e sobreposição do mapeador.",
                availableEvidence = "Escala padrão de 1.0x adiciona ~250ms.",
                proposedChange = "Definir window_animation_scale = 0.5.",
                expectedBenefit = "Transição rápida para o overlay do GG Mouse Pro 2.",
                riskAssessment = "Risco Baixo | Reversível (1.0x).",
                isSelectedByDefault = true,
                canExecuteNow = isShizukuReady,
                blockingReason = if (!isShizukuReady) "Exige Shizuku ativo e autorizado." else null
            )
        )

        val pointerSpec = PrivilegedOperationCatalog.POINTER_SPEED_ADJUST
        list.add(
            EvidenceBasedRecommendation(
                operationSpec = pointerSpec,
                availabilityLabel = formatAvailability(pointerSpec.availability),
                recommendedParameter = "0",
                problemIdentified = "Desvio de linearidade do cursor quando fora do ponto neutro.",
                availableEvidence = "Mouse externo detectado: ${report.connectedPeripherals.any { it.isMouse }}.",
                proposedChange = "Fixar pointer_speed = 0 em Settings.System.",
                expectedBenefit = "Resposta linear 1:1 no Android antes do mapeador.",
                riskAssessment = "Risco Baixo | Reversível.",
                isSelectedByDefault = mode != OptimizationMode.SAFE,
                canExecuteNow = isShizukuReady,
                blockingReason = if (!isShizukuReady) "Exige Shizuku ativo e autorizado." else null
            )
        )

        val dexoptSpec = PrivilegedOperationCatalog.ART_DEXOPT_SPEED_PROFILE
        list.add(
            EvidenceBasedRecommendation(
                operationSpec = dexoptSpec,
                availabilityLabel = formatAvailability(dexoptSpec.availability),
                recommendedParameter = targetGamePkg,
                problemIdentified = "Compilação JIT durante a partida gera oscilações de quadro.",
                availableEvidence = "Pacote alvo: $targetGamePkg.",
                proposedChange = "Executar 'cmd package compile -m speed-profile -f $targetGamePkg'.",
                expectedBenefit = "Reduz stuttering no carregamento de recursos.",
                riskAssessment = "Risco Baixo | Nativo do Android.",
                isSelectedByDefault = true,
                canExecuteNow = isShizukuReady,
                blockingReason = if (!isShizukuReady) "Exige Shizuku ativo e autorizado." else null
            )
        )

        return list
    }

    suspend fun applySelectedAndBenchmark(
        selectedRecommendations: List<EvidenceBasedRecommendation>
    ): Pair<List<NexusResult<String>>, BenchmarkComparisonReport> {
        val baseline = diagnosticsProvider.collectDiagnostics()
        val scoreBefore = computeEvidenceBasedReadiness(baseline)
        val results = mutableListOf<NexusResult<String>>()
        var appliedCount = 0
        var verifiedCount = 0

        for (rec in selectedRecommendations) {
            val execResult = shellExecutor.executeCatalogedOperation(
                operationId = rec.operationSpec.id,
                parameter = rec.recommendedParameter
            )
            when (execResult) {
                is NexusResult.Success -> {
                    appliedCount++
                    if (execResult.data.verified) verifiedCount++
                    operationLogDao.insertLog(
                        OperationLogEntity(
                            operationId = rec.operationSpec.id,
                            commandExecuted = execResult.data.commandTokens.joinToString(" "),
                            exitCode = execResult.data.exitCode,
                            verified = execResult.data.verified,
                            previousValue = execResult.data.previousStateValue,
                            details = execResult.verificationEvidence ?: "Concluído",
                            timestampMs = execResult.data.timestampMs
                        )
                    )
                    results.add(NexusResult.Success("${rec.operationSpec.title} -> '${rec.recommendedParameter}'", execResult.verificationEvidence))
                }
                is NexusResult.Error -> results.add(execResult)
            }
        }

        val postReport = diagnosticsProvider.collectDiagnostics()
        val scoreAfter = computeEvidenceBasedReadiness(postReport)
        val ramDelta = postReport.availableRamMb - baseline.availableRamMb

        val benchmark = BenchmarkComparisonReport(
            baselineReport = baseline,
            postOptimizationReport = postReport,
            scoreBefore = scoreBefore,
            scoreAfter = scoreAfter,
            appliedOperationsCount = appliedCount,
            verifiedOperationsCount = verifiedCount,
            ramDeltaMb = ramDelta,
            refreshRateBeforeHz = baseline.currentRefreshRateHz,
            refreshRateAfterHz = postReport.currentRefreshRateHz,
            summaryObservations = listOf(
                "Operações confirmadas: $verifiedCount/${selectedRecommendations.size}",
                "RAM Livre: ${baseline.availableRamMb} MB -> ${postReport.availableRamMb} MB",
                "Taxa de atualização: ${baseline.currentRefreshRateHz.toInt()} Hz -> ${postReport.currentRefreshRateHz.toInt()} Hz"
            ),
            confoundingFactorsWarning = "Oscilações naturais de RAM fazem parte do gerenciamento do kernel."
        )
        return results to benchmark
    }

    suspend fun rollbackOperation(operationId: String): NexusResult<String> {
        val spec = PrivilegedOperationCatalog.ALL_OPERATIONS[operationId]
            ?: return NexusResult.Error(ErrorCode.COMMAND_NOT_ALLOWLISTED, "Operação não encontrada.")
        val rollbackBuilder = spec.buildRollbackCommand
            ?: return NexusResult.Error(ErrorCode.UNKNOWN_FAILURE, "Operação não reversível.")
        val lastLog = operationLogDao.getLastVerifiedExecution(operationId)
        val restoreValue = lastLog?.previousValue ?: "0"
        val finalParam = rollbackBuilder(restoreValue).lastOrNull() ?: restoreValue
        return when (val res = shellExecutor.executeCatalogedOperation(operationId, finalParam)) {
            is NexusResult.Success -> NexusResult.Success("Restaurado: ${spec.title} -> '$finalParam'")
            is NexusResult.Error -> res
        }
    }

    private fun formatAvailability(availability: ExecutionAvailability): String = when (availability) {
        ExecutionAvailability.AUTOMATIC_WITHOUT_SHIZUKU -> "Aplicável automaticamente"
        ExecutionAvailability.REQUIRES_SHIZUKU_AUTHORIZATION -> "Requer autorização Shizuku"
        ExecutionAvailability.ANDROID_SETTINGS_MANUAL_ONLY -> "Configurável no Android"
        ExecutionAvailability.OEM_DEPENDENT_SHIZUKU -> "Dependente do fabricante (Shizuku)"
        ExecutionAvailability.UNSUPPORTED -> "Não suportado"
    }
}
