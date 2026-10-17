package com.nexus.mobiladorpro.core.shizuku

enum class RiskLevel {
    LOW,
    MODERATE,
    OEM_DEPENDENT
}

enum class ExecutionAvailability {
    AUTOMATIC_WITHOUT_SHIZUKU,
    REQUIRES_SHIZUKU_AUTHORIZATION,
    ANDROID_SETTINGS_MANUAL_ONLY,
    OEM_DEPENDENT_SHIZUKU,
    UNSUPPORTED
}

data class PrivilegedOperationSpec(
    val id: String,
    val title: String,
    val description: String,
    val minSdk: Int,
    val riskLevel: RiskLevel,
    val isReversible: Boolean,
    val availability: ExecutionAvailability,
    val buildApplyCommand: (param: String) -> List<String>,
    val buildVerifyCommand: (() -> List<String>)?,
    val expectedVerifyOutputMatcher: ((output: String, appliedParam: String) -> Boolean)?,
    val buildRollbackCommand: ((previousValue: String) -> List<String>)?,
    val validateParameter: (param: String) -> Boolean
)

object PrivilegedOperationCatalog {

    private val PACKAGE_NAME_REGEX = Regex("^[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z0-9_]+)+$")

    val PROTECTED_PACKAGES_ALLOWLIST = setOf(
        "android",
        "com.android.systemui",
        "com.android.settings",
        "moe.shizuku.privileged.api",
        "com.nexus.mobiladorpro",
        "com.nexus.mobiladorpro.debug",
        "com.ggmousepro.ggmousepro",
        "com.panda.mouse",
        "com.miui.securitycenter",
        "com.miui.powerkeeper",
        "com.xiaomi.joyose",
        "com.dts.freefireth",
        "com.dts.freefiremax"
    )

    val POINTER_SPEED_ADJUST = PrivilegedOperationSpec(
        id = "OP_POINTER_SPEED",
        title = "Velocidade Nativa do Cursor (Pointer Speed)",
        description = "Ajusta o parâmetro pointer_speed do InputReader do Android (-7 a +7) sem alterar a sensibilidade interna do jogo.",
        minSdk = 31,
        riskLevel = RiskLevel.LOW,
        isReversible = true,
        availability = ExecutionAvailability.REQUIRES_SHIZUKU_AUTHORIZATION,
        buildApplyCommand = { speed ->
            listOf("cmd", "settings", "put", "system", "pointer_speed", speed)
        },
        buildVerifyCommand = {
            listOf("cmd", "settings", "get", "system", "pointer_speed")
        },
        expectedVerifyOutputMatcher = { output, param ->
            output.trim() == param.trim()
        },
        buildRollbackCommand = { previous ->
            val safePrev = previous.toIntOrNull()?.coerceIn(-7, 7)?.toString() ?: "0"
            listOf("cmd", "settings", "put", "system", "pointer_speed", safePrev)
        },
        validateParameter = { param ->
            val v = param.toIntOrNull()
            v != null && v in -7..7
        }
    )

    val ANIMATION_SCALE_REDUCTION = PrivilegedOperationSpec(
        id = "OP_ANIMATION_SCALE",
        title = "Redução de Escala de Animação de Janela",
        description = "Ajusta window_animation_scale para reduzir latência visual de transição da interface do sistema.",
        minSdk = 31,
        riskLevel = RiskLevel.LOW,
        isReversible = true,
        availability = ExecutionAvailability.REQUIRES_SHIZUKU_AUTHORIZATION,
        buildApplyCommand = { scale ->
            listOf("cmd", "settings", "put", "global", "window_animation_scale", scale)
        },
        buildVerifyCommand = {
            listOf("cmd", "settings", "get", "global", "window_animation_scale")
        },
        expectedVerifyOutputMatcher = { output, param ->
            output.trim().toFloatOrNull() == param.trim().toFloatOrNull()
        },
        buildRollbackCommand = { previous ->
            val safe = previous.toFloatOrNull()?.takeIf { it in 0.0f..2.0f }?.toString() ?: "1.0"
            listOf("cmd", "settings", "put", "global", "window_animation_scale", safe)
        },
        validateParameter = { param ->
            param in setOf("0.0", "0.5", "1.0")
        }
    )

    val FIXED_PERFORMANCE_MODE = PrivilegedOperationSpec(
        id = "OP_FIXED_PERFORMANCE",
        title = "Modo de Desempenho Sustentado (PowerManager)",
        description = "Solicita ao PowerManagerService o modo de desempenho fixo para evitar oscilação abrupta de clock.",
        minSdk = 31,
        riskLevel = RiskLevel.MODERATE,
        isReversible = true,
        availability = ExecutionAvailability.REQUIRES_SHIZUKU_AUTHORIZATION,
        buildApplyCommand = { enabled ->
            listOf("cmd", "power", "set-fixed-performance-mode-enabled", enabled)
        },
        buildVerifyCommand = null,
        expectedVerifyOutputMatcher = null,
        buildRollbackCommand = { _ ->
            listOf("cmd", "power", "set-fixed-performance-mode-enabled", "false")
        },
        validateParameter = { param ->
            param == "true" || param == "false"
        }
    )

    val GAME_MODE_PERFORMANCE = PrivilegedOperationSpec(
        id = "OP_GAME_MODE_API",
        title = "Perfil GameManager do Android (Modo Performance)",
        description = "Define o modo de execução do pacote do jogo via serviço nativo GameManager (modo 2 = Performance, modo 1 = Standard).",
        minSdk = 31,
        riskLevel = RiskLevel.OEM_DEPENDENT,
        isReversible = true,
        availability = ExecutionAvailability.OEM_DEPENDENT_SHIZUKU,
        buildApplyCommand = { param ->
            val parts = param.split(":")
            val mode = parts.getOrNull(0) ?: "2"
            val pkg = parts.getOrNull(1) ?: "com.dts.freefireth"
            listOf("cmd", "game", "mode", mode, pkg)
        },
        buildVerifyCommand = null,
        expectedVerifyOutputMatcher = null,
        buildRollbackCommand = { previousPkg ->
            val pkg = if (PACKAGE_NAME_REGEX.matches(previousPkg)) previousPkg else "com.dts.freefireth"
            listOf("cmd", "game", "mode", "1", pkg)
        },
        validateParameter = { param ->
            val parts = param.split(":")
            parts.size == 2 &&
                parts[0] in setOf("1", "2", "3") &&
                PACKAGE_NAME_REGEX.matches(parts[1])
        }
    )

    val HYPEROS_REFRESH_RATE_HINT = PrivilegedOperationSpec(
        id = "OP_HYPEROS_REFRESH_RATE",
        title = "Preferência de Taxa de Atualização (HyperOS / MIUI)",
        description = "Configura user_refresh_rate em dispositivos compatíveis (POCO X7 Pro). Sujeito ao limite térmico do PowerKeeper.",
        minSdk = 31,
        riskLevel = RiskLevel.OEM_DEPENDENT,
        isReversible = true,
        availability = ExecutionAvailability.OEM_DEPENDENT_SHIZUKU,
        buildApplyCommand = { hz ->
            listOf("cmd", "settings", "put", "secure", "user_refresh_rate", hz)
        },
        buildVerifyCommand = {
            listOf("cmd", "settings", "get", "secure", "user_refresh_rate")
        },
        expectedVerifyOutputMatcher = { output, param ->
            output.trim() == param.trim()
        },
        buildRollbackCommand = { previousHz ->
            val safeHz = previousHz.toIntOrNull()?.takeIf { it in setOf(60, 90, 120, 144) }?.toString() ?: "120"
            listOf("cmd", "settings", "put", "secure", "user_refresh_rate", safeHz)
        },
        validateParameter = { param ->
            param in setOf("60", "90", "120", "144")
        }
    )

    val ART_DEXOPT_SPEED_PROFILE = PrivilegedOperationSpec(
        id = "OP_ART_SPEED_PROFILE",
        title = "Otimização de Compilação ART (speed-profile)",
        description = "Executa compilação AOT guiada por perfil no pacote do jogo para reduzir compilação JIT durante a partida.",
        minSdk = 31,
        riskLevel = RiskLevel.LOW,
        isReversible = false,
        availability = ExecutionAvailability.REQUIRES_SHIZUKU_AUTHORIZATION,
        buildApplyCommand = { pkg ->
            listOf("cmd", "package", "compile", "-m", "speed-profile", "-f", pkg)
        },
        buildVerifyCommand = null,
        expectedVerifyOutputMatcher = null,
        buildRollbackCommand = null,
        validateParameter = { pkg ->
            pkg in setOf("com.dts.freefireth", "com.dts.freefiremax")
        }
    )

    val SAFE_BACKGROUND_STOP = PrivilegedOperationSpec(
        id = "OP_SAFE_FORCE_STOP",
        title = "Encerramento Seletivo de Aplicativo não Essencial",
        description = "Encerra exclusivamente um pacote selecionado pelo usuário que não esteja na lista de proteção de sistema e periféricos.",
        minSdk = 31,
        riskLevel = RiskLevel.LOW,
        isReversible = false,
        availability = ExecutionAvailability.REQUIRES_SHIZUKU_AUTHORIZATION,
        buildApplyCommand = { pkg ->
            listOf("am", "force-stop", pkg)
        },
        buildVerifyCommand = null,
        expectedVerifyOutputMatcher = null,
        buildRollbackCommand = null,
        validateParameter = { pkg ->
            PACKAGE_NAME_REGEX.matches(pkg) && pkg !in PROTECTED_PACKAGES_ALLOWLIST
        }
    )

    val ALL_OPERATIONS: Map<String, PrivilegedOperationSpec> = listOf(
        POINTER_SPEED_ADJUST,
        ANIMATION_SCALE_REDUCTION,
        FIXED_PERFORMANCE_MODE,
        GAME_MODE_PERFORMANCE,
        HYPEROS_REFRESH_RATE_HINT,
        ART_DEXOPT_SPEED_PROFILE,
        SAFE_BACKGROUND_STOP
    ).associateBy { it.id }
}
