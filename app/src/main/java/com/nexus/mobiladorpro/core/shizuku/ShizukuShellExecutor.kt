package com.nexus.mobiladorpro.core.shizuku

import com.nexus.mobiladorpro.core.common.ErrorCode
import com.nexus.mobiladorpro.core.common.IoDispatcher
import com.nexus.mobiladorpro.core.common.NexusResult
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuRemoteProcess
import java.io.BufferedReader
import java.io.InputStreamReader
import javax.inject.Inject
import javax.inject.Singleton

data class CommandExecutionLog(
    val operationId: String,
    val commandTokens: List<String>,
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
    val verified: Boolean,
    val previousStateValue: String?,
    val timestampMs: Long
)

@Singleton
class ShizukuShellExecutor @Inject constructor(
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {
    suspend fun executeCatalogedOperation(
        operationId: String,
        parameter: String,
        timeoutMs: Long = 8_000L
    ): NexusResult<CommandExecutionLog> = withContext(ioDispatcher) {
        val spec = PrivilegedOperationCatalog.ALL_OPERATIONS[operationId]
            ?: return@withContext NexusResult.Error(
                code = ErrorCode.COMMAND_NOT_ALLOWLISTED,
                message = "Operação '$operationId' não consta na lista permitida de segurança.",
                recoverable = false
            )

        if (!spec.validateParameter(parameter)) {
            return@withContext NexusResult.Error(
                code = ErrorCode.PARAMETER_OUT_OF_BOUNDS,
                message = "Parâmetro '$parameter' inválido ou bloqueado para a operação ${spec.title}.",
                recoverable = true
            )
        }

        if (!Shizuku.pingBinder()) {
            return@withContext NexusResult.Error(
                code = ErrorCode.SHIZUKU_BINDER_LOST,
                message = "Conexão com o serviço Shizuku indisponível no momento da execução."
            )
        }

        val previousValue = spec.buildVerifyCommand?.let { verifyCmd ->
            runRawProcess(verifyCmd, 3_000L)?.first?.trim()
        }

        val applyTokens = spec.buildApplyCommand(parameter)
        val executionResult = runRawProcess(applyTokens, timeoutMs)
            ?: return@withContext NexusResult.Error(
                code = ErrorCode.EXECUTION_TIMEOUT,
                message = "Tempo limite excedido ao executar ${spec.title}."
            )

        val (stdout, stderr, exitCode) = executionResult
        if (exitCode != 0) {
            return@withContext NexusResult.Error(
                code = ErrorCode.OEM_RESTRICTION_BLOCKED,
                message = "O sistema recusou a alteração (${spec.title}). Código: $exitCode",
                technicalDetail = stderr.ifBlank { stdout }
            )
        }

        var isVerified = true
        if (spec.buildVerifyCommand != null && spec.expectedVerifyOutputMatcher != null) {
            val verifyResult = runRawProcess(spec.buildVerifyCommand.invoke(), 3_000L)
            val verifyStdout = verifyResult?.first.orEmpty()
            isVerified = spec.expectedVerifyOutputMatcher.invoke(verifyStdout, parameter)
            if (!isVerified) {
                return@withContext NexusResult.Error(
                    code = ErrorCode.VERIFICATION_MISMATCH,
                    message = "O comando foi enviado, mas o sistema operacional manteve o valor anterior ('$verifyStdout').",
                    technicalDetail = "Esperado: $parameter | Obtido: $verifyStdout"
                )
            }
        }

        val log = CommandExecutionLog(
            operationId = spec.id,
            commandTokens = applyTokens,
            exitCode = exitCode,
            stdout = stdout.trim(),
            stderr = stderr.trim(),
            verified = isVerified,
            previousStateValue = previousValue,
            timestampMs = System.currentTimeMillis()
        )

        NexusResult.Success(
            data = log,
            verificationEvidence = if (spec.buildVerifyCommand != null) {
                "Estado confirmado via leitura direta do sistema: $parameter"
            } else {
                "Comando concluído com exitCode=0"
            }
        )
    }

    suspend fun executeReadOnlyDiagnostic(
        diagnosticCommand: List<String>,
        timeoutMs: Long = 5_000L
    ): NexusResult<String> = withContext(ioDispatcher) {
        val allowedPrefixes = listOf(
            listOf("dumpsys", "gfxinfo"),
            listOf("dumpsys", "SurfaceFlinger"),
            listOf("dumpsys", "thermalservice"),
            listOf("cmd", "settings", "get"),
            listOf("cmd", "game")
        )
        val isAllowed = allowedPrefixes.any { prefix ->
            diagnosticCommand.size >= prefix.size && diagnosticCommand.subList(0, prefix.size) == prefix
        }
        if (!isAllowed) {
            return@withContext NexusResult.Error(
                code = ErrorCode.COMMAND_NOT_ALLOWLISTED,
                message = "Comando diagnóstico fora da lista permitida."
            )
        }
        if (!Shizuku.pingBinder()) {
            return@withContext NexusResult.Error(
                code = ErrorCode.SHIZUKU_BINDER_LOST,
                message = "Shizuku desconectado."
            )
        }

        val res = runRawProcess(diagnosticCommand, timeoutMs)
            ?: return@withContext NexusResult.Error(
                code = ErrorCode.EXECUTION_TIMEOUT,
                message = "Timeout na leitura diagnóstica."
            )

        if (res.third == 0) {
            NexusResult.Success(res.first)
        } else {
            NexusResult.Error(
                code = ErrorCode.UNKNOWN_FAILURE,
                message = "Falha na leitura diagnóstica (código ${res.third}).",
                technicalDetail = res.second
            )
        }
    }

    private suspend fun runRawProcess(
        tokens: List<String>,
        timeoutMs: Long
    ): Triple<String, String, Int>? = withTimeoutOrNull(timeoutMs) {
        try {
            val newProcessMethod = Shizuku::class.java.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            )
            newProcessMethod.isAccessible = true
            val process = newProcessMethod.invoke(
                null,
                tokens.toTypedArray(),
                null,
                null
            ) as ShizukuRemoteProcess

            val stdout = BufferedReader(InputStreamReader(process.inputStream)).use { it.readText() }
            val stderr = BufferedReader(InputStreamReader(process.errorStream)).use { it.readText() }
            val exitCode = process.waitFor()
            process.destroy()
            Triple(stdout, stderr, exitCode)
        } catch (e: Exception) {
            Triple("", e.message ?: "Erro na criação do processo remoto Shizuku", -1)
        }
    }
}
