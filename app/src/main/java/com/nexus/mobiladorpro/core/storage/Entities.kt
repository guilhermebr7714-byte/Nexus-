package com.nexus.mobiladorpro.core.storage

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "mobilador_profiles")
data class MobiladorProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val schemaVersion: Int = 1,
    val profileName: String,
    val presetType: String,
    val deviceModel: String,
    val androidVersion: String,
    val resolutionPx: String,
    val targetRefreshRateHz: Int,
    val mouseModel: String,
    val nominalMouseDpi: Int,
    val nominalPollingRateHz: Int,
    val keyboardModel: String,
    val connectionType: String,
    val mapperName: String,
    val androidPointerSpeed: Int,
    val sensGeneral: Int,
    val sensRedDot: Int,
    val sens2x: Int,
    val sens4x: Int,
    val sensSniper: Int,
    val sensFreeLook: Int,
    val hudKeybindingsJson: String,
    val updatedAtMs: Long
)

@Entity(tableName = "training_sessions")
data class TrainingSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val modeName: String,
    val inputMethod: String,
    val attemptsCount: Int,
    val hitsCount: Int,
    val accuracyPercent: Double,
    val avgReactionTimeMs: Double,
    val avgMotorExecutionTimeMs: Double,
    val avgOvershootPx: Double,
    val avgUndershootPx: Double,
    val verticalStdDevPx: Double,
    val horizontalStdDevPx: Double,
    val recommendationSummary: String,
    val timestampMs: Long
)

@Entity(tableName = "privileged_operation_logs")
data class OperationLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val operationId: String,
    val commandExecuted: String,
    val exitCode: Int,
    val verified: Boolean,
    val previousValue: String?,
    val details: String,
    val timestampMs: Long
)
