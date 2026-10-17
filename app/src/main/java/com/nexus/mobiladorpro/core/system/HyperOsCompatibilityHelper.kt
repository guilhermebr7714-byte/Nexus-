package com.nexus.mobiladorpro.core.system

import android.os.Build

data class OemProfileInfo(
    val isXiaomiOrPoco: Boolean,
    val isPocoX7ProReference: Boolean,
    val isHyperOsDetected: Boolean,
    val hyperOsVersionString: String?,
    val knownOemLimitations: List<String>,
    val recommendedShizukuNotes: List<String>
)

object HyperOsCompatibilityHelper {

    fun inspectDeviceOem(): OemProfileInfo {
        val manufacturer = Build.MANUFACTURER.lowercase()
        val brand = Build.BRAND.lowercase()
        val model = Build.MODEL.uppercase()
        val device = Build.DEVICE.lowercase()

        val isXiaomiFamily = manufacturer.contains("xiaomi") ||
            brand.contains("poco") ||
            brand.contains("redmi") ||
            brand.contains("xiaomi")

        val isPocoX7Pro = model.contains("POCO X7 PRO") ||
            model.contains("2412DPC0AG") ||
            device == "rodin"

        val hyperOsProp = readSystemPropertySafe("ro.mi.os.version.name")
            ?: readSystemPropertySafe("ro.miui.ui.version.name")
        val isHyperOs = !hyperOsProp.isNullOrBlank() &&
            (hyperOsProp.startsWith("OS", ignoreCase = true) || Build.DISPLAY.contains("OS", ignoreCase = true))

        val limitations = buildList {
            if (isXiaomiFamily) {
                add("O serviço 'PowerKeeper' e o 'Joyose' do HyperOS podem reduzir a taxa de atualização de 120 Hz para 60 Hz caso a temperatura da bateria ultrapasse ~43 °C.")
                add("Nas Opções do Desenvolvedor do HyperOS, é obrigatório ativar 'Depuração USB (Configurações de segurança)' para permitir que o Shizuku e o mapeador simulem eventos ou alterem configurações seguras.")
                add("O gerenciamento de bateria MIUI/HyperOS encerra o Shizuku e o GG Mouse Pro 2 em segundo plano se o aplicativo não estiver definido como 'Sem restrições'.")
            } else {
                add("Políticas de economia de energia do fabricante podem limitar a taxa de atualização em segundo plano.")
            }
        }

        val notes = buildList {
            if (isPocoX7Pro) {
                add("Perfil de Referência POCO X7 Pro detectado: Tela AMOLED com suporte nominal até 120 Hz. Recomendado usar adaptador USB-C OTG com alimentação (PD Pass-Through) para evitar queda de tensão nas portas USB ao usar mouse de 1000 Hz.")
            }
            if (isXiaomiFamily) {
                add("Para evitar desconexão do GG Mouse Pro 2 no HyperOS: ative 'Depuração USB (Configurações de segurança)' nas Opções do Desenvolvedor.")
            }
        }

        return OemProfileInfo(
            isXiaomiOrPoco = isXiaomiFamily,
            isPocoX7ProReference = isPocoX7Pro,
            isHyperOsDetected = isHyperOs,
            hyperOsVersionString = hyperOsProp,
            knownOemLimitations = limitations,
            recommendedShizukuNotes = notes
        )
    }

    private fun readSystemPropertySafe(key: String): String? {
        return try {
            val systemProperties = Class.forName("android.os.SystemProperties")
            val getMethod = systemProperties.getMethod("get", String::class.java, String::class.java)
            val value = getMethod.invoke(null, key, "") as? String
            value?.takeIf { it.isNotBlank() }
        } catch (_: Throwable) {
            null
        }
    }
}
