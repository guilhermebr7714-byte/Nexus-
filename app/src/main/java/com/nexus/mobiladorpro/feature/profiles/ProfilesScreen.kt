package com.nexus.mobiladorpro.feature.profiles

import android.os.Build
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexus.mobiladorpro.core.common.NexusResult
import com.nexus.mobiladorpro.core.storage.MobiladorProfileDao
import com.nexus.mobiladorpro.core.storage.MobiladorProfileEntity
import com.nexus.mobiladorpro.core.storage.ProfileSerializer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProfilesViewModel @Inject constructor(
    private val profileDao: MobiladorProfileDao
) : ViewModel() {
    val profiles: StateFlow<List<MobiladorProfileEntity>> = profileDao.observeAllProfiles()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun createDefaultPocoX7ProProfile(name: String, dpi: Int, sensGeneral: Int, keybinds: String) {
        viewModelScope.launch {
            profileDao.upsertProfile(
                MobiladorProfileEntity(
                    profileName = name.ifBlank { "POCO X7 Pro Competitive" },
                    presetType = "COMPETITIVE_LOW_LATENCY",
                    deviceModel = Build.MODEL,
                    androidVersion = Build.VERSION.RELEASE,
                    resolutionPx = "1220x2712",
                    targetRefreshRateHz = 120,
                    mouseModel = "Gaming Mouse USB",
                    nominalMouseDpi = dpi.coerceIn(100, 36000),
                    nominalPollingRateHz = 1000,
                    keyboardModel = "Mechanical Keyboard",
                    connectionType = "USB_HUB_PD",
                    mapperName = "GG Mouse Pro 2",
                    androidPointerSpeed = 0,
                    sensGeneral = sensGeneral.coerceIn(0, 200),
                    sensRedDot = 92,
                    sens2x = 86,
                    sens4x = 80,
                    sensSniper = 50,
                    sensFreeLook = 100,
                    hudKeybindingsJson = keybinds,
                    updatedAtMs = System.currentTimeMillis()
                )
            )
        }
    }

    fun importJsonProfile(rawJson: String, onMessage: (String) -> Unit) {
        viewModelScope.launch {
            when (val res = ProfileSerializer.importProfileFromJson(rawJson)) {
                is NexusResult.Success -> {
                    profileDao.upsertProfile(res.data)
                    onMessage("✔ Perfil '${res.data.profileName}' importado com integridade SHA-256 confirmada.")
                }
                is NexusResult.Error -> {
                    onMessage("✖ ${res.message}")
                }
            }
        }
    }
}

@Composable
fun ProfilesScreen(viewModel: ProfilesViewModel) {
    val profiles by viewModel.profiles.collectAsState()
    var profileName by remember { mutableStateOf("POCO X7 Pro - GG Mouse Pro 2") }
    var dpiText by remember { mutableStateOf("800") }
    var sensGeneralText by remember { mutableStateOf("96") }
    var keybindsText by remember { mutableStateOf("Move=WASD | Pulo=SPACE | Agachar=C | Gelo=Q | Arma1=1 | Arma2=2 | Disparo=Mouse1 | Mira=Mouse2") }
    var jsonTransferBox by remember { mutableStateOf("") }
    var statusMessage by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0B0E14))
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "HUD, MAPEAMENTO & PERFIS INTELIGENTES",
            style = MaterialTheme.typography.titleLarge,
            color = Color(0xFF00E5FF),
            fontWeight = FontWeight.Bold
        )

        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF141923)), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("CRIAR / SALVAR PERFIL DE MOBILADOR", color = Color(0xFF00E676), fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = profileName,
                    onValueChange = { profileName = it },
                    label = { Text("Nome do Perfil") },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = dpiText,
                        onValueChange = { dpiText = it },
                        label = { Text("DPI do Mouse") },
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = sensGeneralText,
                        onValueChange = { sensGeneralText = it },
                        label = { Text("Sensi Geral (0-200)") },
                        modifier = Modifier.weight(1f)
                    )
                }
                OutlinedTextField(
                    value = keybindsText,
                    onValueChange = { keybindsText = it },
                    label = { Text("Mapeamento de Teclas e HUD (GG Mouse Pro 2)") },
                    modifier = Modifier.fillMaxWidth()
                )
                Button(
                    onClick = {
                        viewModel.createDefaultPocoX7ProProfile(
                            name = profileName,
                            dpi = dpiText.toIntOrNull() ?: 800,
                            sensGeneral = sensGeneralText.toIntOrNull() ?: 96,
                            keybinds = keybindsText
                        )
                        statusMessage = "✔ Perfil salvo no banco Room local."
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Salvar Perfil no Dispositivo")
                }
            }
        }

        if (statusMessage.isNotBlank()) {
            Text(statusMessage, color = Color(0xFF00E5FF))
        }

        profiles.forEach { p ->
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF141923)), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("${p.profileName} [${p.presetType}]", color = Color.White, fontWeight = FontWeight.Bold)
                    Text("Aparelho: ${p.deviceModel} | DPI: ${p.nominalMouseDpi} | Geral: ${p.sensGeneral} | Mapeador: ${p.mapperName}", color = Color(0xFF80CBC4), style = MaterialTheme.typography.bodySmall)
                    Text("Teclas: ${p.hudKeybindingsJson}", color = Color(0xFFB0BEC5), style = MaterialTheme.typography.labelSmall)
                    OutlinedButton(onClick = {
                        jsonTransferBox = ProfileSerializer.exportProfileToJson(p)
                        statusMessage = "✔ JSON com assinatura SHA-256 gerado abaixo."
                    }) {
                        Text("Exportar JSON Versionado (SHA-256)")
                    }
                }
            }
        }

        OutlinedTextField(
            value = jsonTransferBox,
            onValueChange = { jsonTransferBox = it },
            label = { Text("JSON de Exportação / Importação com Checksum SHA-256") },
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedButton(
            onClick = {
                viewModel.importJsonProfile(jsonTransferBox) { statusMessage = it }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Validar Checksum e Importar Perfil JSON")
        }
    }
}
