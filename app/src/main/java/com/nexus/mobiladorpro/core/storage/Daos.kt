package com.nexus.mobiladorpro.core.storage

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MobiladorProfileDao {
    @Query("SELECT * FROM mobilador_profiles ORDER BY updatedAtMs DESC")
    fun observeAllProfiles(): Flow<List<MobiladorProfileEntity>>

    @Query("SELECT COUNT(*) FROM mobilador_profiles")
    suspend fun countProfiles(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProfile(profile: MobiladorProfileEntity): Long

    @Delete
    suspend fun deleteProfile(profile: MobiladorProfileEntity)
}

@Dao
interface TrainingSessionDao {
    @Query("SELECT * FROM training_sessions ORDER BY timestampMs DESC LIMIT 100")
    fun observeRecentSessions(): Flow<List<TrainingSessionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: TrainingSessionEntity): Long

    @Query("DELETE FROM training_sessions")
    suspend fun clearAllSessions()
}

@Dao
interface OperationLogDao {
    @Query("SELECT * FROM privileged_operation_logs ORDER BY timestampMs DESC LIMIT 100")
    fun observeLogs(): Flow<List<OperationLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: OperationLogEntity)

    @Query("SELECT * FROM privileged_operation_logs WHERE operationId = :opId AND verified = 1 ORDER BY timestampMs DESC LIMIT 1")
    suspend fun getLastVerifiedExecution(opId: String): OperationLogEntity?
}
