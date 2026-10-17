package com.nexus.mobiladorpro.feature.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import com.nexus.mobiladorpro.core.shizuku.ShizukuConnectionState
import com.nexus.mobiladorpro.core.shizuku.ShizukuManager
import com.nexus.mobiladorpro.core.system.DeviceDiagnosticReport
import com.nexus.mobiladorpro.core.system.SystemDiagnosticsProvider
import com.nexus.mobiladorpro.core.ui.navigation.NexusDestination
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val diagnosticsProvider: SystemDiagnosticsProvider,
    val shizukuManager: ShizukuManager
) : ViewModel() {
    private val _report = MutableStateFlow(diagnosticsProvider.collectDiagnostics())
    val report: StateFlow<DeviceDiagnosticReport> = _report.asStateFlow()

    fun refreshDiagnostics() {
        shizukuManager.refreshState()
        _report.value = diagnosticsProvider.collectDiagnostics()
    }
}

@Composable
fun DashboardScreen(
    onNavigate: (NexusDestination) -> Unit,
    viewModel: DashboardViewModel
) {
    val rep by viewModel.report.collectAsState()
    val shizukuState by viewModel.shizukuManager.connectionState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0B0E14))
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "NEXUS MOBILADOR PRO — CENTRAL DE TELEMETRIA",
            style = MaterialTheme.typography.titleLarge,
            color = Color(0xFF00E5FF),
            fontWeight = FontWeight.Bold
        )

        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF141923)), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("DISPOSITIVO & AMBIENTE OPERACIONAL", color = Color(0xFF00E5FF), fontWeight = FontWeight.Bold)
                Text("• Aparelho: ${rep.manufacturer} ${rep.model} (Android ${rep.androidRelease} / API ${rep.sdkInt})", color = Color.White)
                Text("• HyperOS detectado: ${if (rep.oemProfileInfo.isHyperOsDetected) "Sim (${rep.oemProfileInfo.hyperOsVersionString})" else "Não"} | Referência POCO X7 Pro: ${if (rep.oemProfileInfo.isPocoX7ProReference) "Sim" else "Perfil Compatível"}", color = Color(0xFF80CBC4))
                Text("• Memória RAM: ${rep.availableRamMb} MB livres de ${rep.totalRamMb} MB (${rep.ramUsagePercent}% em uso)", color = Color.White)
                Text("• Temperatura Bateria: ${rep.batteryTempCelsius} °C | Estado Térmico: ${rep.thermalStatusLabel}", color = Color(0xFFFFCC80))
                Text("• Display: ${rep.screenResolutionPx} (${rep.densityDpi} dpi) @ ${rep.currentRefreshRateHz.toInt()} Hz", color = Color.White)
                Text("• Estado do Shizuku: ${shizukuState::class.simpleName}", color = Color(0xFF00E676))
            }
        }

        if (rep.identifiedBottlenecks.isNotEmpty()) {
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF26161B)), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("GARGALOS E ALERTAS IDENTIFICADOS", color = Color(0xFFFF5252), fontWeight = FontWeight.Bold)
                    rep.identifiedBottlenecks.forEach { b ->
                        Text("• $b", color = Color.White, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = { onNavigate(NexusDestination.TOUCH_LAB) }, modifier = Modifier.weight(1f)) {
                Text("Abrir Touch Lab")
            }
            OutlinedButton(onClick = { onNavigate(NexusDestination.CAPA_LAB) }, modifier = Modifier.weight(1f)) {
                Text("Abrir Capa Lab")
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = { onNavigate(NexusDestination.INPUT_LAB) }, modifier = Modifier.weight(1f)) {
                Text("Mouse & Teclado")
            }
            OutlinedButton(onClick = { onNavigate(NexusDestination.SHIZUKU) }, modifier = Modifier.weight(1f)) {
                Text("Central Shizuku")
            }
        }

        Button(onClick = { viewModel.refreshDiagnostics() }, modifier = Modifier.fillMaxWidth()) {
            Text("EXECUTAR NOVO DIAGNÓSTICO COMPLETO")
        }
    }
}
