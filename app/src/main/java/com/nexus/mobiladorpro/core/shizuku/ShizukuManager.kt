package com.nexus.mobiladorpro.core.shizuku

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import rikka.shizuku.Shizuku
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ShizukuManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        const val SHIZUKU_PACKAGE_NAME = "moe.shizuku.privileged.api"
        const val REQUEST_CODE_SHIZUKU_PERMISSION = 9001
    }

    private val _connectionState = MutableStateFlow<ShizukuConnectionState>(ShizukuConnectionState.Disconnected)
    val connectionState: StateFlow<ShizukuConnectionState> = _connectionState.asStateFlow()

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        refreshState()
    }

    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        _connectionState.value = ShizukuConnectionState.ServiceLost(
            reason = "O serviço do Shizuku foi encerrado ou a Depuração Sem Fio foi desativada.",
            timestampMs = System.currentTimeMillis()
        )
    }

    private val requestPermissionResultListener =
        Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
            if (requestCode == REQUEST_CODE_SHIZUKU_PERMISSION) {
                if (grantResult == PackageManager.PERMISSION_GRANTED) {
                    transitionToAuthorizedAndReady()
                } else {
                    val rationale = try {
                        Shizuku.shouldShowRequestPermissionRationale()
                    } catch (_: Throwable) {
                        false
                    }
                    _connectionState.value = ShizukuConnectionState.PermissionDenied(rationale)
                }
            }
        }

    fun registerListeners() {
        Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
        Shizuku.addBinderDeadListener(binderDeadListener)
        Shizuku.addRequestPermissionResultListener(requestPermissionResultListener)
        refreshState()
    }

    fun unregisterListeners() {
        Shizuku.removeBinderReceivedListener(binderReceivedListener)
        Shizuku.removeBinderDeadListener(binderDeadListener)
        Shizuku.removeRequestPermissionResultListener(requestPermissionResultListener)
    }

    fun refreshState() {
        if (!isShizukuPackageInstalled()) {
            _connectionState.value = ShizukuConnectionState.NotInstalled
            return
        }

        if (!Shizuku.pingBinder()) {
            _connectionState.value = ShizukuConnectionState.Disconnected
            return
        }

        try {
            if (Shizuku.isPreV11()) {
                _connectionState.value = ShizukuConnectionState.Disconnected
                return
            }

            val permissionStatus = Shizuku.checkSelfPermission()
            if (permissionStatus == PackageManager.PERMISSION_GRANTED) {
                transitionToAuthorizedAndReady()
            } else {
                _connectionState.value = ShizukuConnectionState.ServiceAvailable(
                    version = Shizuku.getVersion()
                )
            }
        } catch (e: Throwable) {
            _connectionState.value = ShizukuConnectionState.Disconnected
        }
    }

    fun requestAuthorization() {
        if (!Shizuku.pingBinder()) {
            refreshState()
            return
        }
        _connectionState.value = ShizukuConnectionState.Authorizing
        try {
            Shizuku.requestPermission(REQUEST_CODE_SHIZUKU_PERMISSION)
        } catch (e: Throwable) {
            _connectionState.value = ShizukuConnectionState.PermissionDenied(false)
        }
    }

    private fun transitionToAuthorizedAndReady() {
        try {
            val uid = Shizuku.getUid()
            val version = Shizuku.getVersion()
            val isRoot = (uid == 0)
            _connectionState.value = ShizukuConnectionState.Authorized(uid = uid, isRootPrivilege = isRoot)

            val supportedOps = PrivilegedOperationCatalog.ALL_OPERATIONS
                .filterValues { Build.VERSION.SDK_INT >= it.minSdk }
                .keys

            _connectionState.value = ShizukuConnectionState.OperationReady(
                uid = uid,
                serverVersion = version,
                isRootPrivilege = isRoot,
                supportedOperationsIds = supportedOps
            )
        } catch (e: Throwable) {
            _connectionState.value = ShizukuConnectionState.Disconnected
        }
    }

    private fun isShizukuPackageInstalled(): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(
                    SHIZUKU_PACKAGE_NAME,
                    PackageManager.PackageInfoFlags.of(0)
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(SHIZUKU_PACKAGE_NAME, 0)
            }
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }
}
