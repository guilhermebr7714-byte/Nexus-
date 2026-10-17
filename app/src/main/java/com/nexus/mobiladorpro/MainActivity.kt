package com.nexus.mobiladorpro

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.nexus.mobiladorpro.core.shizuku.ShizukuManager
import com.nexus.mobiladorpro.core.ui.navigation.NexusRootApp
import com.nexus.mobiladorpro.core.ui.theme.NexusMobiladorTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var shizukuManager: ShizukuManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        shizukuManager.registerListeners()
        setContent {
            NexusMobiladorTheme {
                NexusRootApp()
            }
        }
    }

    override fun onDestroy() {
        shizukuManager.unregisterListeners()
        super.onDestroy()
    }
}
