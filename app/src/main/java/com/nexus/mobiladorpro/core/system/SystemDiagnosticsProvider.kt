package com.nexus.mobiladorpro.core.system

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.display.DisplayManager
import android.hardware.input.InputManager
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.os.StatFs
import android.view.Display
import android.view.InputDevice
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

data class ConnectedInputPeripheral(
    val deviceId: Int,
    val name: String,
    val vendorId: Int,
    val productId: Int,
    val isMouse: Boolean,
    val isKeyboard: Boolean,
    val isExternal: Boolean,
    val descriptor: String
)

data class DeviceDiagnosticReport(
    val manufacturer: String,
    val brand: String,
    val model: String,
    val androidRelease: String,
    val sdkInt: Int,
    val totalRamMb: Long,
    val availableRamMb: Long,
    val lowMemoryPressure: Boolean,
    val ramUsagePercent: Int,
    val freeStorageGb: Double,
    val batteryTempCelsius: Float,
    val thermalStatusCode: Int,
    val thermalStatusLabel: String,
    val isPowerSaveMode: Boolean,
    val currentRefreshRateHz: Float,
    val supportedRefreshRatesHz: List<Float>,
    val screenResolutionPx: String,
    val densityDpi: Int,
    val connectedPeripherals: List<ConnectedInputPeripheral>,
    val installedMappers: List<String>,
    val installedFreeFirePackages: List<String>,
    val oemProfileInfo: OemProfileInfo,
    val identifiedBottlenecks: List<String>,
    val timestampMs: Long
)

@Singleton
class SystemDiagnosticsProvider @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun collectDiagnostics(): DeviceDiagnosticReport {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)

        val totalRamMb = memInfo.totalMem / (1024 * 1024)
        val availRamMb = memInfo.availMem / (1024 * 1024)
        val usedPercent = (((memInfo.totalMem - memInfo.availMem).toDouble() / memInfo.totalMem) * 100.0).roundToInt()

        val statFs = StatFs(Environment.getDataDirectory().path)
        val freeStorageGb = (statFs.availableBytes.toDouble() / (1024.0 * 1024.0 * 1024.0))

        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val thermalStatus = powerManager.currentThermalStatus
        val thermalLabel = mapThermalStatus(thermalStatus)
        val isPowerSave = powerManager.isPowerSaveMode

        val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val tempTenths = batteryIntent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
        val batteryTempC = tempTenths / 10.0f

        val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        val defaultDisplay = displayManager.getDisplay(Display.DEFAULT_DISPLAY)
        val currentHz = defaultDisplay?.refreshRate ?: 60f
        val supportedHz = defaultDisplay?.supportedModes
            ?.map { (it.refreshRate * 10f).roundToInt() / 10f }
            ?.distinct()
            ?.sortedDescending()
            ?: listOf(60f)

        val metrics = context.resources.displayMetrics
        val resolution = "${metrics.widthPixels}x${metrics.heightPixels}"
        val densityDpi = metrics.densityDpi

        val peripherals = detectInputPeripherals()
        val mappers = detectInstalledPackages(
            mapOf(
                "com.ggmousepro.ggmousepro" to "GG Mouse Pro 2",
                "com.panda.mouse" to "Panda Mouse Pro"
            )
        )
        val ffPackages = detectInstalledPackages(
            mapOf(
                "com.dts.freefireth" to "Free Fire (Padrão)",
                "com.dts.freefiremax" to "Free Fire MAX"
            )
        )

        val oemInfo = HyperOsCompatibilityHelper.inspectDeviceOem()

        val bottlenecks = buildList {
            if (isPowerSave) {
                add("Economia de Bateria ativa: limita o clock da CPU e reduz a taxa de atualização da tela.")
            }
            if (batteryTempC >= 41.0f || thermalStatus >= PowerManager.THERMAL_STATUS_MODERATE) {
                add("Temperatura elevada (${batteryTempC} °C / $thermalLabel): risco iminente de thermal throttling e queda de FPS.")
            }
            if (memInfo.lowMemory || usedPercent >= 85) {
                add("Pressão de memória RAM elevada ($usedPercent% em uso, ${availRamMb} MB livres).")
            }
            val maxHz = supportedHz.maxOrNull() ?: 60f
            if (maxHz > 60f && currentHz < maxHz - 1f) {
                add("Painel suporta até ${maxHz.toInt()} Hz, mas está operando em ${currentHz.toInt()} Hz no momento.")
            }
            if (peripherals.none { it.isMouse } || peripherals.none { it.isKeyboard }) {
                add("Combo completo de Teclado + Mouse externo não detectado no barramento de entrada atual.")
            }
        }

        return DeviceDiagnosticReport(
            manufacturer = Build.MANUFACTURER,
            brand = Build.BRAND,
            model = Build.MODEL,
            androidRelease = Build.VERSION.RELEASE,
            sdkInt = Build.VERSION.SDK_INT,
            totalRamMb = totalRamMb,
            availableRamMb = availRamMb,
            lowMemoryPressure = memInfo.lowMemory,
            ramUsagePercent = usedPercent,
            freeStorageGb = (freeStorageGb * 100.0).roundToInt() / 100.0,
            batteryTempCelsius = batteryTempC,
            thermalStatusCode = thermalStatus,
            thermalStatusLabel = thermalLabel,
            isPowerSaveMode = isPowerSave,
            currentRefreshRateHz = currentHz,
            supportedRefreshRatesHz = supportedHz,
            screenResolutionPx = resolution,
            densityDpi = densityDpi,
            connectedPeripherals = peripherals,
            installedMappers = mappers,
            installedFreeFirePackages = ffPackages,
            oemProfileInfo = oemInfo,
            identifiedBottlenecks = bottlenecks,
            timestampMs = System.currentTimeMillis()
        )
    }

    private fun detectInputPeripherals(): List<ConnectedInputPeripheral> {
        val inputManager = context.getSystemService(Context.INPUT_SERVICE) as InputManager
        val list = mutableListOf<ConnectedInputPeripheral>()
        for (id in inputManager.inputDeviceIds) {
            val dev = inputManager.getInputDevice(id) ?: continue
            val sources = dev.sources
            val isMouse = (sources and InputDevice.SOURCE_MOUSE) == InputDevice.SOURCE_MOUSE ||
                (sources and InputDevice.SOURCE_MOUSE_RELATIVE) == InputDevice.SOURCE_MOUSE_RELATIVE
            val isKeyboard = (sources and InputDevice.SOURCE_KEYBOARD) == InputDevice.SOURCE_KEYBOARD &&
                dev.keyboardType == InputDevice.KEYBOARD_TYPE_ALPHABETIC

            if (isMouse || isKeyboard) {
                list.add(
                    ConnectedInputPeripheral(
                        deviceId = dev.id,
                        name = dev.name,
                        vendorId = dev.vendorId,
                        productId = dev.productId,
                        isMouse = isMouse,
                        isKeyboard = isKeyboard,
                        isExternal = dev.isExternal,
                        descriptor = dev.descriptor
                    )
                )
            }
        }
        return list
    }

    private fun detectInstalledPackages(targets: Map<String, String>): List<String> {
        val found = mutableListOf<String>()
        val pm = context.packageManager
        for ((pkg, label) in targets) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    pm.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(0))
                } else {
                    @Suppress("DEPRECATION")
                    pm.getPackageInfo(pkg, 0)
                }
                found.add("$label ($pkg)")
            } catch (_: PackageManager.NameNotFoundException) {
            }
        }
        return found
    }

    private fun mapThermalStatus(status: Int): String = when (status) {
        PowerManager.THERMAL_STATUS_NONE -> "Normal (Sem restrição térmica)"
        PowerManager.THERMAL_STATUS_LIGHT -> "Leve (Aquecimento inicial)"
        PowerManager.THERMAL_STATUS_MODERATE -> "Moderado (Atenção)"
        PowerManager.THERMAL_STATUS_SEVERE -> "Severo (Redução de clock ativa)"
        PowerManager.THERMAL_STATUS_CRITICAL -> "Crítico (Throttling agressivo)"
        PowerManager.THERMAL_STATUS_EMERGENCY -> "Emergência"
        PowerManager.THERMAL_STATUS_SHUTDOWN -> "Desligamento de proteção"
        else -> "Desconhecido"
    }
}
