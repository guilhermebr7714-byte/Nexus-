package com.nexus.mobiladorpro.feature.touchlab

import android.view.MotionEvent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import com.nexus.mobiladorpro.core.system.SystemDiagnosticsProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

data class TouchLabUiState(
    val displayRefreshRateHz: Float = 120f,
    val currentPoints: List<TouchSamplePoint> = emptyList(),
    val currentTelemetry: TouchSessionTelemetry? = null
)

@HiltViewModel
class TouchLabViewModel @Inject constructor(
    diagnosticsProvider: SystemDiagnosticsProvider
) : ViewModel() {
    private val _uiState = MutableStateFlow(TouchLabUiState(displayRefreshRateHz = diagnosticsProvider.collectDiagnostics().currentRefreshRateHz))
    val uiState: StateFlow<TouchLabUiState> = _uiState.asStateFlow()
    private val workingPoints = mutableListOf<TouchSamplePoint>()

    fun onMotionEventCaptured(event: MotionEvent) {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                workingPoints.clear()
                workingPoints.add(TouchSamplePoint(event.x, event.y, event.eventTime, false))
            }
            MotionEvent.ACTION_MOVE, MotionEvent.ACTION_UP -> {
                for (h in 0 until event.historySize) {
                    workingPoints.add(TouchSamplePoint(event.getHistoricalX(h), event.getHistoricalY(h), event.getHistoricalEventTime(h), true))
                }
                workingPoints.add(TouchSamplePoint(event.x, event.y, event.eventTime, false))
                val tel = TouchMetricsCalculator.analyzeTrajectory(workingPoints, emptyList(), _uiState.value.displayRefreshRateHz)
                _uiState.update { it.copy(currentPoints = workingPoints.toList(), currentTelemetry = tel) }
            }
        }
    }

    fun clearSession() {
        workingPoints.clear()
        _uiState.update { it.copy(currentPoints = emptyList(), currentTelemetry = null) }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun TouchLabScreen(viewModel: TouchLabViewModel) {
    val state by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0B0E14))
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("TOUCH RESPONSE LAB", style = MaterialTheme.typography.titleLarge, color = Color(0xFF00E5FF), fontWeight = FontWeight.Bold)
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(280.dp)
                .background(Color(0xFF121820), RoundedCornerShape(12.dp))
                .border(1.dp, Color(0xFF00E5FF), RoundedCornerShape(12.dp))
                .pointerInteropFilter {
                    viewModel.onMotionEventCaptured(it)
                    true
                }
        ) {
            val pts = state.currentPoints
            if (pts.size >= 2) {
                val path = Path().apply {
                    moveTo(pts.first().x, pts.first().y)
                    for (i in 1 until pts.size) lineTo(pts[i].x, pts[i].y)
                }
                drawPath(path, Color(0xFF00E5FF), style = Stroke(width = 4.5f))
                pts.forEach { p ->
                    drawCircle(if (p.isHistoricalBatch) Color(0xFF00E676) else Color.Yellow, radius = 3.5f, center = Offset(p.x, p.y))
                }
            }
        }
        OutlinedButton(onClick = { viewModel.clearSession() }) { Text("Limpar Superfície") }
        state.currentTelemetry?.let { tel ->
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF141923)), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("• Taxa observada: ${tel.observedTouchReportRateHz} Hz | Mediana dt: ${tel.medianIntervalMs} ms | P95: ${tel.p95IntervalMs} ms", color = Color.White)
                    Text("• Desvio de linearidade: ${tel.linearityDeviationPx} px | Velocidade: ${tel.meanVelocityPxPerSec} px/s", color = Color(0xFF80CBC4))
                    Text(tel.diagnosticInterpretation, color = Color(0xFFFFCC80))
                }
            }
        }
    }
}
