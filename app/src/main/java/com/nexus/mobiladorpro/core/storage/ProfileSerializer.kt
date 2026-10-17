package com.nexus.mobiladorpro.core.storage

import com.nexus.mobiladorpro.core.common.ErrorCode
import com.nexus.mobiladorpro.core.common.NexusResult
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.security.MessageDigest

@Serializable
data class VersionedProfileExportEnvelope(
    val exportFormatVersion: Int = 1,
    val exportedAtMs: Long,
    val sha256Checksum: String,
    val profile: MobiladorProfileEntity
)

object ProfileSerializer {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun exportProfileToJson(profile: MobiladorProfileEntity): String {
        val normalizedProfile = profile.copy(id = 0)
        val rawPayload = json.encodeToString(normalizedProfile)
        val checksum = sha256(rawPayload)
        val envelope = VersionedProfileExportEnvelope(
            exportFormatVersion = 1,
            exportedAtMs = System.currentTimeMillis(),
            sha256Checksum = checksum,
            profile = normalizedProfile
        )
        return json.encodeToString(envelope)
    }

    fun importProfileFromJson(rawJson: String): NexusResult<MobiladorProfileEntity> {
        return try {
            val envelope = json.decodeFromString<VersionedProfileExportEnvelope>(rawJson)
            if (envelope.exportFormatVersion !in 1..2) {
                return NexusResult.Error(
                    code = ErrorCode.STORAGE_CORRUPTED,
                    message = "Versão de esquema incompatível (${envelope.exportFormatVersion})."
                )
            }
            val expectedHash = sha256(json.encodeToString(envelope.profile))
            if (expectedHash != envelope.sha256Checksum) {
                return NexusResult.Error(
                    code = ErrorCode.STORAGE_CORRUPTED,
                    message = "Assinatura de integridade SHA-256 inválida. O arquivo de perfil foi corrompido ou adulterado."
                )
            }
            val p = envelope.profile
            if (p.androidPointerSpeed !in -7..7 ||
                p.sensGeneral !in 0..200 ||
                p.nominalMouseDpi !in 100..36000
            ) {
                return NexusResult.Error(
                    code = ErrorCode.PARAMETER_OUT_OF_BOUNDS,
                    message = "O perfil importado contém valores de sensibilidade ou DPI fora dos limites seguros."
                )
            }
            NexusResult.Success(p.copy(updatedAtMs = System.currentTimeMillis()))
        } catch (e: Exception) {
            NexusResult.Error(
                code = ErrorCode.STORAGE_CORRUPTED,
                message = "Falha ao ler JSON do perfil: formato inválido ou corrompido.",
                technicalDetail = e.message
            )
        }
    }

    private fun sha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
