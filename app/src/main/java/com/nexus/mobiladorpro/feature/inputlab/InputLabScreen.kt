package com.nexus.mobiladorpro.feature.inputlab

import android.view.KeyEvent
import android.view.MotionEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import com.nexus.mobiladorpro.core.system.ConnectedInputPeripheral
import com.nexus.mobiladorpro.core.system.SystemDiagnosticsProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

data class InputLabUiState(
    val detectedPeripherals: List<ConnectedInputPeripheral> = emptyList(),
    val mouseCadence: MouseCadenceReport = PeripheralAnalyzer.computeMouseCadence(emptyList(), 0, 0f),
    val keyboardReport: KeyboardRolloverReport = PeripheralAnalyzer.evaluateKeyboardMatrix(emptySet(), 0, 0)
)

@HiltViewModel
class InputLabViewModel @Inject constructor(
    diagnosticsProvider: SystemDiagnosticsProvider
) : ViewModel() {
    private val _uiState = MutableStateFlow(InputLabUiState(detectedPeripherals = diagnosticsProvider.collectDiagnostics().connectedPeripherals))
    val uiState: StateFlow<InputLabUiState> = _uiState.asStateFlow()
    private val mouseTimestampsNs = ArrayDeque<Long>(250)
    private val activeKeys = mutableSetOf<Int>()
    private var maxKeys = 0
    private var repeats = 0

    fun onMouseEvent(event: MotionEvent) {
        for (h in 0 until event.historySize) {
            if (mouseTimestampsNs.size >= 240) mouseTimestampsNs.removeFirst()
            mouseTimestampsNs.addLast(event.getHistoricalEventTimeNanos(h))
        }
        if (mouseTimestampsNs.size >= 240) mouseTimestampsNs.removeFirst()
        mouseTimestampsNs.addLast(event.eventTimeNanos)
        val scrollV = if (event.actionMasked == MotionEvent.ACTION_SCROLL) event.getAxisValue(MotionEvent.AXIS_VSCROLL) else 0f
        _uiState.update { it.copy(mouseCadence = PeripheralAnalyzer.computeMouseCadence(mouseTimestampsNs.toList(), event.buttonState, scrollV)) }
    }

    fun onKeyEventCaptured(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            if (event.repeatCount > 0) repeats++ else {
                activeKeys.add(event.keyCode)
                if (activeKeys.size > maxKeys) maxKeys = activeKeys.size
            }
        } else if (event.action == KeyEvent.ACTION_UP) {
            activeKeys.remove(event.keyCode)
        }
        _uiState.update { it.copy(keyboardReport = PeripheralAnalyzer.evaluateKeyboardMatrix(activeKeys.toSet(), maxKeys, repeats)) }
        return true
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun InputLabScreen(viewModel: InputLabViewModel) {
    val state by viewModel.uiState.collectAsState()
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0B0E14))
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent { viewModel.onKeyEventCaptured(it.nativeKeyEvent) },
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("MOUSE & KEYBOARD LAB", style = MaterialTheme.typography.titleLarge, color = Color(0xFF00E5FF), fontWeight = FontWeight.Bold)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .background(Color(0xFF121820), RoundedCornerShape(12.dp))
                .border(1.dp, Color(0xFF00E5FF), RoundedCornerShape(12.dp))
                .pointerInteropFilter {
                    viewModel.onMouseEvent(it)
                    true
                },
            contentAlignment = Alignment.Center
        ) {
            val mc = state.mouseCadence
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("MOVA O MOUSE E CLIQUE AQUI", color = Color(0xFF00E5FF), fontWeight = FontWeight.Bold)
                Text(mc.connectionStabilityVerdict, color = Color.White)
            }
        }
        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF141923)), modifier = Modifier.fillMaxWidth()) {
            val kb = state.keyboardReport
            Column(modifier = Modifier.padding(14.dp)) {
                Text("N-KEY ROLLOVER (TECLADO)", color = Color(0xFF00E676), fontWeight = FontWeight.Bold)
                Text("Teclas ativas: ${kb.currentlyPressedLabels.joinToString(" + ").ifEmpty { "Nenhuma" }}", color = Color.White)
                Text("Máximo simultâneo: ${kb.maxSimultaneousKeysRecorded} teclas", color = Color(0xFF80CBC4))
            }
        }
    }
}
