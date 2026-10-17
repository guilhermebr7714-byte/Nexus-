package com.nexus.mobiladorpro

import com.nexus.mobiladorpro.core.common.NexusResult
import com.nexus.mobiladorpro.core.storage.MobiladorProfileEntity
import com.nexus.mobiladorpro.core.storage.ProfileSerializer
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileSerializationTest {

    @Test
    fun exportAndImportValidatesSha256Integrity() {
        val sample = MobiladorProfileEntity(
            id = 1,
            profileName = "POCO X7 Pro Test",
            presetType = "COMPETITIVE_STABLE",
            deviceModel = "POCO X7 Pro",
            androidVersion = "15",
            resolutionPx = "1220x2712",
            targetRefreshRateHz = 120,
            mouseModel = "G203",
            nominalMouseDpi = 800,
            nominalPollingRateHz = 1000,
            keyboardModel = "Mechanical",
            connectionType = "USB_HUB_PD",
            mapperName = "GG Mouse Pro 2",
            androidPointerSpeed = 0,
            sensGeneral = 95,
            sensRedDot = 90,
            sens2x = 85,
            sens4x = 80,
            sensSniper = 50,
            sensFreeLook = 100,
            hudKeybindingsJson = "WASD",
            updatedAtMs = 1000L
        )
        val json = ProfileSerializer.exportProfileToJson(sample)
        val imported = ProfileSerializer.importProfileFromJson(json)
        assertTrue(imported is NexusResult.Success)

        val corrupted = json.replace(""sensGeneral": 95", ""sensGeneral": 199")
        val corruptedResult = ProfileSerializer.importProfileFromJson(corrupted)
        assertTrue(corruptedResult is NexusResult.Error)
    }
}
