package com.nexus.mobiladorpro.feature.premium

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.entitlementDataStore by preferencesDataStore(name = "nexus_entitlements")

enum class ProFeature {
    UNLIMITED_PROFILES,
    ADVANCED_TRAJECTORY_ANALYSIS,
    EXPORT_IMPORT_PROFILES,
    CUSTOM_OPTIMIZATION_RULES,
    EXTENDED_CAPA_DRILLS,
    SESSION_COMPARISON_BENCHMARK
}

data class EntitlementStatus(
    val isProActive: Boolean,
    val verificationSource: String,
    val maxProfilesAllowed: Int
) {
    fun canAccess(feature: ProFeature): Boolean = isProActive
}

@Singleton
class EntitlementManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val keyProVerified = booleanPreferencesKey("pro_verified_token")
    private val keyVerificationSource = stringPreferencesKey("pro_verification_source")

    val entitlementStatus: Flow<EntitlementStatus> = context.entitlementDataStore.data.map { prefs ->
        val isPro = prefs[keyProVerified] ?: false
        val source = prefs[keyVerificationSource] ?: "FREE_TIER"
        EntitlementStatus(
            isProActive = isPro,
            verificationSource = source,
            maxProfilesAllowed = if (isPro) Int.MAX_VALUE else 1
        )
    }

    suspend fun updateVerifiedLicense(isPro: Boolean, source: String) {
        context.entitlementDataStore.edit { prefs ->
            prefs[keyProVerified] = isPro
            prefs[keyVerificationSource] = source
        }
    }
}
