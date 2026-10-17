package com.nexus.mobiladorpro.feature.capalab

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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import com.nexus.mobiladorpro.core.storage.TrainingSessionDao
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import kotlin.random.Random

data class ActiveCapaTarget(
    val bodyCenterX: Float,
    val bodyCenterY: Float,
    val headCenterX: Float,
    val headCenterY: Float,
    val headRadiusPx: Float,
    val spawnTimestampMs: Long
)

data class CapaLabUiState(
    val activeTarget: ActiveCapaTarget? = null,
    val dragStartX: Float? = null,
    val dragStartY: Float? = null,
    val dragCurrentX: Float? = null,
    val dragCurrentY: Float? = null,
    val completedAttempts: List<CapaAttemptRecord> = emptyList(),
    val evaluation: CapaTrainingEvaluation? = null
)

@HiltViewModel
class CapaLabViewModel @Inject constructor(
    private val trainingSessionDao: TrainingSessionDao
) : ViewModel() {
    private val _uiState = MutableStateFlow(CapaLabUiState())
    val uiState: StateFlow<CapaLabUiState> = _uiState.asStateFlow()
    private var canvasW = 800f
    private var canvasH = 600f
    private var moveStartMs = 0L

    fun updateCanvasBounds(w: Float, h: Float) {
        if (w > 100f && h > 100f) {
            canvasW = w
            canvasH = h
            if (_uiState.value.activeTarget == null && _uiState.value.evaluation == null) spawnTarget()
        }
    }

    fun restartDrill() {
        _uiState.update { it.copy(completedAttempts = emptyList(), evaluation = null) }
        spawnTarget()
    }

    fun onArenaMotionEvent(event: MotionEvent) {
        val target = _uiState.value.activeTarget ?: return
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                moveStartMs = System.currentTimeMillis()
                _uiState.update { it.copy(dragStartX = event.x, dragStartY = event.y, dragCurrentX = event.x, dragCurrentY = event.y) }
            }
            MotionEvent.ACTION_MOVE -> {
                _uiState.update { it.copy(dragCurrentX = event.x, dragCurrentY = event.y) }
            }
            MotionEvent.ACTION_UP -> {
                val rec = CapaAttemptRecord(
                    target.spawnTimestampMs, moveStartMs, System.currentTimeMillis(),
                    _uiState.value.dragStartX ?: event.x, _uiState.value.dragStartY ?: event.y,
                    target.headCenterX, target.headCenterY, target.headRadiusPx, event.x, event.y
                )
                val list = _uiState.value.completedAttempts + rec
                if (list.size >= 10) {
                    _uiState.update { it.copy(activeTarget = null, completedAttempts = list, evaluation = CapaRecommendationEngine.evaluateSession(list)) }
                } else {
                    _uiState.update { it.copy(completedAttempts = list) }
                    spawnTarget()
                }
            }
        }
    }

    private fun spawnTarget() {
        val hx = 100f + Random.nextFloat() * (canvasW - 200f).coerceAtLeast(100f)
        val hy = 90f + Random.nextFloat() * (canvasH * 0.35f).coerceAtLeast(80f)
        _uiState.update {
            it.copy(
                activeTarget = ActiveCapaTarget(hx, hy + 110f, hx, hy, 26f, System.currentTimeMillis()),
                dragStartX = null, dragStartY = null, dragCurrentX = null, dragCurrentY = null
            )
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun CapaLabScreen(viewModel: CapaLabViewModel) {
    val state by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0B0E14))
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("NEXUS CAPA TRAINING LAB", style = MaterialTheme.typography.titleLarge, color = Color(0xFF00E5FF), fontWeight = FontWeight.Bold)
        Text("Tentativas: ${state.completedAttempts.size} / 10", color = Color(0xFF00E676))
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(320.dp)
                .background(Color(0xFF121820), RoundedCornerShape(12.dp))
                .border(1.dp, Color(0xFF00E5FF), RoundedCornerShape(12.dp))
                .onSizeChanged { viewModel.updateCanvasBounds(it.width.toFloat(), it.height.toFloat()) }
                .pointerInteropFilter {
                    viewModel.onArenaMotionEvent(it)
                    true
                }
        ) {
            state.activeTarget?.let { t ->
                drawRect(Color(0xFF37474F), Offset(t.bodyCenterX - 30f, t.bodyCenterY - 42f), Size(60f, 84f))
                drawCircle(Color(0xFFFF1744), radius = t.headRadiusPx, center = Offset(t.headCenterX, t.headCenterY))
            }
            val sx = state.dragStartX
            val sy = state.dragStartY
            val cx = state.dragCurrentX
            val cy = state.dragCurrentY
            if (sx != null && sy != null && cx != null && cy != null) {
                drawLine(Color(0xFF00E5FF), Offset(sx, sy), Offset(cx, cy), strokeWidth = 4f)
            }
        }
        Button(onClick = { viewModel.restartDrill() }, modifier = Modifier.fillMaxWidth()) {
            Text("Reiniciar Série de 10 Puxadas")
        }
        state.evaluation?.let { eval ->
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF141923)), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Precisão de Capa: ${eval.accuracyPercent}% (${eval.headHitsCount}/${eval.totalAttempts})", color = Color(0xFF00E676), fontWeight = FontWeight.Bold)
                    Text("Reação Visual: ${eval.meanVisualReactionTimeMs} ms | Execução Motora: ${eval.meanMotorExecutionTimeMs} ms", color = Color.White)
                    Text("Overshoot: ${eval.meanVerticalOvershootPx} px | Undershoot: ${eval.meanVerticalUndershootPx} px", color = Color.White)
                    eval.explainableHypotheses.forEach { h ->
                        Text("→ $h", color = Color(0xFFFFCC80))
                    }
                }
            }
        }
    }
}
