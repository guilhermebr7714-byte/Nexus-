package com.nexus.mobiladorpro.feature.optimizer

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexus.mobiladorpro.core.common.NexusResult
import com.nexus.mobiladorpro.core.shizuku.PrivilegedOperationCatalog
import com.nexus.mobiladorpro.core.shizuku.ShizukuShellExecutor
import com.nexus.mobiladorpro.core.storage.OperationLogDao
import com.nexus.mobiladorpro.core.storage.OperationLogEntity
import com.nexus.mobiladorpro.core.system.DeviceDiagnosticReport
import com.nexus.mobiladorpro.core.system.SystemDiagnosticsProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class OptimizerUiState(
    val selectedMode: OptimizationMode = OptimizationMode.COMPETITIVE,
    val thermalProfile: ThermalAndPowerProfile = ThermalAndPowerProfile.COMPETITIVE_PEAK,
    val diagnosticReport: DeviceDiagnosticReport? = null,
    val readinessScore: EvidenceScoreBreakdown? = null,
    val recommendations: List<EvidenceBasedRecommendation> = emptyList(),
    val selectedOperationIds: Set<String> = emptySet(),
    val classifiedApps: List<ClassifiedInstalledApp> = emptyList(),
    val userSelectedPackagesToStop: Set<String> = emptySet(),
    val ramSessionHistory: List<RamHistorySample> = emptyList(),
    val isExecuting: Boolean = false,
    val executionMessages: List<String> = emptyList(),
    val lastBenchmark: BenchmarkComparisonReport? = null
)

@HiltViewModel
class OptimizerViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val diagnosticsProvider: SystemDiagnosticsProvider,
    private val optimizationEngine: SmartOptimizationEngine,
    private val appClassifier: ProcessAndAppClassifier,
    private val shellExecutor: ShizukuShellExecutor,
    operationLogDao: OperationLogDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(OptimizerUiState())
    val uiState: StateFlow<OptimizerUiState> = _uiState.asStateFlow()

    val operationLogs: StateFlow<List<OperationLogEntity>> = operationLogDao.observeLogs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        refreshAll()
    }

    fun selectMode(mode: OptimizationMode) = refreshAll(mode, _uiState.value.thermalProfile)
    fun selectThermalProfile(profile: ThermalAndPowerProfile) = refreshAll(_uiState.value.selectedMode, profile)

    fun refreshAll(
        mode: OptimizationMode = _uiState.value.selectedMode,
        thermalProfile: ThermalAndPowerProfile = _uiState.value.thermalProfile
    ) {
        viewModelScope.launch {
            val report = diagnosticsProvider.collectDiagnostics()
            val score = optimizationEngine.computeEvidenceBasedReadiness(report)
            val recs = optimizationEngine.generateRecommendations(report, mode, thermalProfile)
            val defaults = recs.filter { it.isSelectedByDefault }.map { it.operationSpec.id }.toSet()
            val apps = appClassifier.classifyInstalledApplications(_uiState.value.userSelectedPackagesToStop)
            val ramSample = RamHistorySample(report.timestampMs, report.availableRamMb, report.ramUsagePercent, "Diagnóstico")

            _uiState.update { state ->
                state.copy(
                    selectedMode = mode,
                    thermalProfile = thermalProfile,
                    diagnosticReport = report,
                    readinessScore = score,
                    recommendations = recs,
                    selectedOperationIds = defaults,
                    classifiedApps = apps,
                    ramSessionHistory = (state.ramSessionHistory + ramSample).takeLast(12)
                )
            }
        }
    }

    fun toggleOperationSelection(operationId: String) {
        _uiState.update { state ->
            val updated = state.selectedOperationIds.toMutableSet()
            if (!updated.add(operationId)) updated.remove(operationId)
            state.copy(selectedOperationIds = updated)
        }
    }

    fun openSystemAppSettings(packageName: String) {
        appContext.startActivity(appClassifier.createAppDetailsSettingsIntent(packageName))
    }

    fun stopSingleBackgroundAppViaShizuku(packageName: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isExecuting = true) }
            val result = shellExecutor.executeCatalogedOperation(
                PrivilegedOperationCatalog.SAFE_BACKGROUND_STOP.id,
                packageName
            )
            val msg = when (result) {
                is NexusResult.Success -> "✔ Processo '$packageName' encerrado."
                is NexusResult.Error -> "✖ Bloqueado (${result.code}): ${result.message}"
            }
            _uiState.update { it.copy(isExecuting = false, executionMessages = listOf(msg) + it.executionMessages) }
        }
    }

    fun executeSelectedOptimizations() {
        val state = _uiState.value
        val selectedRecs = state.recommendations.filter { it.operationSpec.id in state.selectedOperationIds }
        if (selectedRecs.isEmpty()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isExecuting = true, executionMessages = emptyList()) }
            val (results, benchmark) = optimizationEngine.applySelectedAndBenchmark(selectedRecs)
            val messages = results.map { res ->
                when (res) {
                    is NexusResult.Success -> "✔ ${res.data}"
                    is NexusResult.Error -> "✖ [${res.code}] ${res.message}"
                }
            }
            _uiState.update {
                it.copy(
                    isExecuting = false,
                    executionMessages = messages,
                    lastBenchmark = benchmark,
                    diagnosticReport = benchmark.postOptimizationReport,
                    readinessScore = benchmark.scoreAfter
                )
            }
        }
    }

    fun rollbackSingleOperation(operationId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isExecuting = true) }
            val res = optimizationEngine.rollbackOperation(operationId)
            val msg = when (res) {
                is NexusResult.Success -> "↺ ${res.data}"
                is NexusResult.Error -> "✖ ${res.message}"
            }
            _uiState.update { it.copy(isExecuting = false, executionMessages = listOf(msg) + it.executionMessages) }
        }
    }
}
