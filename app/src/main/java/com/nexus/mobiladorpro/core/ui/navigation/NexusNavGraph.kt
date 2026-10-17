package com.nexus.mobiladorpro.core.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.nexus.mobiladorpro.feature.capalab.CapaLabScreen
import com.nexus.mobiladorpro.feature.dashboard.DashboardScreen
import com.nexus.mobiladorpro.feature.inputlab.InputLabScreen
import com.nexus.mobiladorpro.feature.optimizer.OptimizerScreen
import com.nexus.mobiladorpro.feature.performance.PerformanceScreen
import com.nexus.mobiladorpro.feature.profiles.ProfilesScreen
import com.nexus.mobiladorpro.feature.shizukuaccess.ShizukuAccessScreen
import com.nexus.mobiladorpro.feature.touchlab.TouchLabScreen

enum class NexusDestination(val title: String) {
    DASHBOARD("Dashboard"),
    SHIZUKU("Shizuku"),
    OPTIMIZER("Otimização"),
    TOUCH_LAB("Touch Lab"),
    INPUT_LAB("Mouse & Teclado"),
    CAPA_LAB("Capa Lab"),
    FPS_STABILITY("FPS & Stability"),
    HUD_PROFILES("HUD & Perfis")
}

@Composable
fun NexusRootApp() {
    var currentDest by rememberSaveable { mutableStateOf(NexusDestination.DASHBOARD) }

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF10151E))
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                NexusDestination.entries.forEach { dest ->
                    FilterChip(
                        selected = currentDest == dest,
                        onClick = { currentDest = dest },
                        label = {
                            Text(
                                text = dest.title,
                                fontWeight = if (currentDest == dest) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFF00E5FF),
                            selectedLabelColor = Color.Black
                        )
                    )
                }
            }
        },
        containerColor = Color(0xFF0B0E14)
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            when (currentDest) {
                NexusDestination.DASHBOARD -> DashboardScreen(
                    onNavigate = { currentDest = it },
                    viewModel = hiltViewModel()
                )
                NexusDestination.SHIZUKU -> ShizukuAccessScreen(viewModel = hiltViewModel())
                NexusDestination.OPTIMIZER -> OptimizerScreen(viewModel = hiltViewModel())
                NexusDestination.TOUCH_LAB -> TouchLabScreen(viewModel = hiltViewModel())
                NexusDestination.INPUT_LAB -> InputLabScreen(viewModel = hiltViewModel())
                NexusDestination.CAPA_LAB -> CapaLabScreen(viewModel = hiltViewModel())
                NexusDestination.FPS_STABILITY -> PerformanceScreen(viewModel = hiltViewModel())
                NexusDestination.HUD_PROFILES -> ProfilesScreen(viewModel = hiltViewModel())
            }
        }
    }
}
