package com.nexus.mobiladorpro

import com.nexus.mobiladorpro.core.shizuku.PrivilegedOperationCatalog
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CommandAllowlistSecurityTest {

    @Test
    fun pointerSpeedBoundsAreEnforced() {
        val spec = PrivilegedOperationCatalog.POINTER_SPEED_ADJUST
        assertTrue(spec.validateParameter("0"))
        assertTrue(spec.validateParameter("-7"))
        assertTrue(spec.validateParameter("7"))
        assertFalse(spec.validateParameter("8"))
        assertFalse(spec.validateParameter("-8"))
        assertFalse(spec.validateParameter("0; rm -rf /"))
    }

    @Test
    fun protectedPackagesCannotBeForceStopped() {
        val spec = PrivilegedOperationCatalog.SAFE_BACKGROUND_STOP
        assertFalse(spec.validateParameter("moe.shizuku.privileged.api"))
        assertFalse(spec.validateParameter("com.ggmousepro.ggmousepro"))
        assertFalse(spec.validateParameter("com.dts.freefireth"))
        assertFalse(spec.validateParameter("com.android.systemui"))
        assertTrue(spec.validateParameter("com.example.nonessentialapp"))
    }
}
