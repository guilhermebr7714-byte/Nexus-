package com.nexus.mobiladorpro.feature.performance

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexus.mobiladorpro.core.common.NexusResult
import com.nexus.mobiladorpro.core.system.FrameStabilityMonitor
import com.nexus.mobiladorpro.core.system.FrameStabilitySnapshot
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PerformanceViewModel @Inject constructor(
    private val frameMonitor: FrameStabilityMonitor
) : ViewModel() {
    private val _snapshot = MutableStateFlow<FrameStabilitySnapshot?>(null)
    val snapshot: StateFlow<FrameStabilitySnapshot?> = _snapshot.asStateFlow()

    private val _errorMsg = MutableStateFlow<String?>(null)
    val errorMsg: StateFlow<String?> = _errorMsg.asStateFlow()

    fun sampleFreeFireFrameStats() {
        viewModelScope.launch {
            when (val res = frameMonitor.capturePackageFrameStats("com.dts.freefireth")) {
                is NexusResult.Success -> {
                    _snapshot.value = res.data
                    _errorMsg.value = null
                }
                is NexusResult.Error -> {
                    _errorMsg.value = "${res.code}: ${res.message}"
                }
            }
        }
    }
}

@Composable
fun PerformanceScreen(viewModel: PerformanceViewModel) {
    val snap by viewModel.snapshot.collectAsState()
    val err by viewModel.errorMsg.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0B0E14))
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "FPS & FRAME STABILITY MONITOR",
            style = MaterialTheme.typography.titleLarge,
            color = Color(0xFF00E5FF),
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Coleta estatísticas reais de tempo de quadro via 'dumpsys gfxinfo framestats' quando o Shizuku está autorizado. Não inventa FPS com base nos Hz nominais da tela.",
            color = Color(0xFFB0BEC5),
            style = MaterialTheme.typography.bodySmall
        )

        Button(onClick = { viewModel.sampleFreeFireFrameStats() }, modifier = Modifier.fillMaxWidth()) {
            Text("CAPTURAR FRAMESTATS DO FREE FIRE VIA SHIZUKU")
        }

        err?.let {
            Text("⚠ $it", color = Color(0xFFFF5252), style = MaterialTheme.typography.bodySmall)
        }

        snap?.let { s ->
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF141923)), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("FONTE: ${s.dataSourceDescription}", color = Color(0xFF00E5FF), fontWeight = FontWeight.Bold)
                    Text("• Quadros analisados no buffer: ${s.sampleCount}", color = Color.White)
                    Text("• FPS Médio Observado: ${s.observedAverageFps} FPS", color = Color(0xFF00E676))
                    Text("• Tempo de Quadro (Mediana / P95): ${s.medianFrameTimeMs} ms / ${s.p95FrameTimeMs} ms", color = Color.White)
                    Text("• Quadros com atraso (Jank > 16.8ms): ${s.jankFrameCount} (${s.jankPercentage}%)", color = Color(0xFFFFCC80))
                    Text("• Desvio Padrão (Frame Time Jitter): ±${s.frameTimeStdDevMs} ms", color = Color.White)
                }
            }
        }
    }
}
