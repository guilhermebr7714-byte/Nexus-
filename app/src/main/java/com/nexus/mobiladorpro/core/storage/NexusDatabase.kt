package com.nexus.mobiladorpro.core.storage

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Database(
    entities = [
        MobiladorProfileEntity::class,
        TrainingSessionEntity::class,
        OperationLogEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class NexusDatabase : RoomDatabase() {
    abstract fun profileDao(): MobiladorProfileDao
    abstract fun trainingDao(): TrainingSessionDao
    abstract fun operationLogDao(): OperationLogDao
}

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): NexusDatabase {
        return Room.databaseBuilder(
            context,
            NexusDatabase::class.java,
            "nexus_mobilador_pro.db"
        ).fallbackToDestructiveMigration().build()
    }

    @Provides
    fun provideProfileDao(db: NexusDatabase): MobiladorProfileDao = db.profileDao()

    @Provides
    fun provideTrainingDao(db: NexusDatabase): TrainingSessionDao = db.trainingDao()

    @Provides
    fun provideOperationLogDao(db: NexusDatabase): OperationLogDao = db.operationLogDao()
}
