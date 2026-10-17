package com.nexus.mobiladorpro.core.shizuku

sealed interface ShizukuConnectionState {
    data object Disconnected : ShizukuConnectionState
    data object NotInstalled : ShizukuConnectionState
    data class ServiceAvailable(val version: Int) : ShizukuConnectionState
    data object Authorizing : ShizukuConnectionState
    data class PermissionDenied(val shouldShowRationale: Boolean) : ShizukuConnectionState
    data class Authorized(val uid: Int, val isRootPrivilege: Boolean) : ShizukuConnectionState
    data class OperationReady(
        val uid: Int,
        val serverVersion: Int,
        val isRootPrivilege: Boolean,
        val supportedOperationsIds: Set<String>
    ) : ShizukuConnectionState
    data class ServiceLost(val reason: String, val timestampMs: Long) : ShizukuConnectionState
}
