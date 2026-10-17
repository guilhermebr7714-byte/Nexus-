package com.nexus.mobiladorpro.feature.optimizer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun OptimizerScreen(viewModel: OptimizerViewModel) {
    val state by viewModel.uiState.collectAsState()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0B0E14))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = "NEXUS PERFORMANCE ENGINE",
                style = MaterialTheme.typography.titleLarge,
                color = Color(0xFF00E5FF),
                fontWeight = FontWeight.Bold
            )
            state.readinessScore?.let { score ->
                Text(
                    text = "Índice Baseado em Evidências: ${score.overallReadinessScore}/100 (${score.calculationExplanation})",
                    color = Color(0xFF00E676),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OptimizationMode.entries.forEach { mode ->
                    FilterChip(
                        selected = state.selectedMode == mode,
                        onClick = { viewModel.selectMode(mode) },
                        label = { Text(mode.title) }
                    )
                }
            }
        }

        items(state.recommendations, key = { it.operationSpec.id }) { rec ->
            val isChecked = rec.operationSpec.id in state.selectedOperationIds
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF141923)), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = isChecked,
                            onCheckedChange = { viewModel.toggleOperationSelection(rec.operationSpec.id) }
                        )
                        Text(rec.operationSpec.title, color = Color.White, fontWeight = FontWeight.Bold)
                    }
                    Text("• Problema: ${rec.problemIdentified}", color = Color(0xFFB0BEC5), style = MaterialTheme.typography.bodySmall)
                    Text("• Alteração: ${rec.proposedChange}", color = Color(0xFF80CBC4), style = MaterialTheme.typography.bodySmall)
                    if (rec.operationSpec.isReversible) {
                        OutlinedButton(onClick = { viewModel.rollbackSingleOperation(rec.operationSpec.id) }) {
                            Text("Restaurar Padrão")
                        }
                    }
                }
            }
        }

        item {
            Button(
                onClick = { viewModel.executeSelectedOptimizations() },
                enabled = !state.isExecuting && state.selectedOperationIds.isNotEmpty(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("APLICAR AJUSTES E VERIFICAR ESTADO (${state.selectedOperationIds.size})")
            }
        }

        if (state.executionMessages.isNotEmpty()) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF102027)), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        state.executionMessages.forEach { msg ->
                            Text(msg, color = Color.White, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}
