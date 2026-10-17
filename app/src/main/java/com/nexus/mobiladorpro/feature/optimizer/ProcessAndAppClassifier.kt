package com.nexus.mobiladorpro.feature.optimizer

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.nexus.mobiladorpro.core.shizuku.PrivilegedOperationCatalog
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

enum class AppClassificationCategory(val label: String, val canBeStoppedSafely: Boolean) {
    ESSENTIAL_SYSTEM("Essencial do Sistema", false),
    GAME_SESSION_RELATED("Sessão de Jogo (Jogo / Mapeador / Shizuku)", false),
    DO_NOT_INTERRUPT("Protegido (Acessibilidade / Entrada / OEM)", false),
    BACKGROUND_APP("Segundo Plano (Candidato à Otimização)", true),
    USER_SELECTED("Selecionado pelo Usuário", true)
}

data class ClassifiedInstalledApp(
    val packageName: String,
    val appName: String,
    val category: AppClassificationCategory,
    val isRunningOrCached: Boolean,
    val uid: Int
)

@Singleton
class ProcessAndAppClassifier @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val gameSessionPackages = setOf(
        "com.dts.freefireth",
        "com.dts.freefiremax",
        "com.ggmousepro.ggmousepro",
        "com.panda.mouse",
        "moe.shizuku.privileged.api",
        context.packageName
    )

    private val doNotInterruptPrefixes = listOf(
        "com.android.inputmethod",
        "com.google.android.inputmethod",
        "com.miui.securitycenter",
        "com.miui.powerkeeper",
        "com.xiaomi.joyose",
        "com.android.systemui",
        "com.android.settings"
    )

    fun classifyInstalledApplications(userSelectedPackages: Set<String>): List<ClassifiedInstalledApp> {
        val pm = context.packageManager
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager

        val runningPackages = activityManager.runningAppProcesses
            ?.flatMap { it.pkgList?.toList() ?: emptyList() }
            ?.toSet()
            ?: emptySet()

        val installedApps = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.getInstalledApplications(0)
        }

        return installedApps.mapNotNull { appInfo ->
            val pkg = appInfo.packageName
            val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0 ||
                (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
            val isStopped = (appInfo.flags and ApplicationInfo.FLAG_STOPPED) != 0

            val category = when {
                pkg in gameSessionPackages -> AppClassificationCategory.GAME_SESSION_RELATED
                pkg in PrivilegedOperationCatalog.PROTECTED_PACKAGES_ALLOWLIST ||
                    doNotInterruptPrefixes.any { pkg.startsWith(it) } -> AppClassificationCategory.DO_NOT_INTERRUPT
                isSystem -> AppClassificationCategory.ESSENTIAL_SYSTEM
                pkg in userSelectedPackages -> AppClassificationCategory.USER_SELECTED
                else -> AppClassificationCategory.BACKGROUND_APP
            }

            if (category == AppClassificationCategory.ESSENTIAL_SYSTEM && !pkg.startsWith("com.android.chrome")) {
                return@mapNotNull null
            }

            val label = try {
                pm.getApplicationLabel(appInfo).toString()
            } catch (_: Throwable) {
                pkg
            }

            ClassifiedInstalledApp(
                packageName = pkg,
                appName = label,
                category = category,
                isRunningOrCached = !isStopped || pkg in runningPackages,
                uid = appInfo.uid
            )
        }.sortedBy { it.appName.lowercase() }
    }

    fun createAppDetailsSettingsIntent(packageName: String): Intent {
        return Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }
}
