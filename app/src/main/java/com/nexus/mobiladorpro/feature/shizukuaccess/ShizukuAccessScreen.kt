package com.nexus.mobiladorpro.feature.shizukuaccess

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
import com.nexus.mobiladorpro.core.shizuku.PrivilegedOperationCatalog
import com.nexus.mobiladorpro.core.shizuku.ShizukuConnectionState
import com.nexus.mobiladorpro.core.shizuku.ShizukuManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class ShizukuAccessViewModel @Inject constructor(
    val shizukuManager: ShizukuManager
) : ViewModel()

@Composable
fun ShizukuAccessScreen(viewModel: ShizukuAccessViewModel) {
    val state by viewModel.shizukuManager.connectionState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0B0E14))
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "NEXUS SYSTEM ACCESS (SHIZUKU ENGINE)",
            style = MaterialTheme.typography.titleLarge,
            color = Color(0xFF00E5FF),
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Máquina de Estados Determinística: DISCONNECTED → SERVICE_AVAILABLE → AUTHORIZED → OPERATION_READY.",
            color = Color(0xFFB0BEC5),
            style = MaterialTheme.typography.bodySmall
        )

        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF141923)), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("ESTADO ATUAL DO SERVIÇO PRIVILEGIADO", color = Color(0xFF00E676), fontWeight = FontWeight.Bold)
                val statusText = when (val s = state) {
                    is ShizukuConnectionState.NotInstalled -> "NÃO INSTALADO — Instale o aplicativo oficial Shizuku."
                    is ShizukuConnectionState.Disconnected -> "DESCONECTADO — Inicie o serviço Shizuku por Depuração Sem Fio ou ADB."
                    is ShizukuConnectionState.ServiceAvailable -> "SERVIÇO DISPONÍVEL (v${s.version}) — Aguardando autorização do usuário."
                    is ShizukuConnectionState.Authorizing -> "SOLICITANDO AUTORIZAÇÃO..."
                    is ShizukuConnectionState.PermissionDenied -> "AUTORIZAÇÃO NEGADA — Conceda permissão na janela do Shizuku."
                    is ShizukuConnectionState.Authorized -> "AUTORIZADO (UID ${s.uid})"
                    is ShizukuConnectionState.OperationReady -> "OPERATION_READY — Pronto (UID ${s.uid} | Servidor v${s.serverVersion} | ${s.supportedOperationsIds.size} operações habilitadas)."
                    is ShizukuConnectionState.ServiceLost -> "CONEXÃO PERDIDA: ${s.reason}"
                }
                Text(statusText, color = Color.White)

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { viewModel.shizukuManager.refreshState() }) {
                        Text("Consultar Conexão")
                    }
                    Button(onClick = { viewModel.shizukuManager.requestAuthorization() }) {
                        Text("Solicitar Autorização")
                    }
                }
            }
        }

        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF141923)), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("CATÁLOGO DE OPERAÇÕES PERMITIDAS (ALLOWLIST)", color = Color(0xFF00E5FF), fontWeight = FontWeight.Bold)
                PrivilegedOperationCatalog.ALL_OPERATIONS.values.forEach { op ->
                    Text("• [${op.id}] ${op.title} — Reversível: ${if (op.isReversible) "Sim" else "Não"}", color = Color.White, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
