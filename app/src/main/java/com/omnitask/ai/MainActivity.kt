package com.omnitask.ai

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.omnitask.ai.ui.AppRoot
import com.omnitask.ai.ui.theme.OmniTaskTheme

class MainActivity : ComponentActivity() {

    private val permLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            OmniTaskTheme {
                AppRoot()
            }
        }
        askCorePermissions()
    }

    /**
     * Contacts power "send Moomin a WhatsApp message" style tasks, and photo
     * access powers show_photo / delete_photo / set_wallpaper. Asking on launch
     * means the AI never has to answer "I can't search your contacts".
     */
    private fun askCorePermissions() {
        val want = ArrayList<String>()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            want.add(Manifest.permission.READ_CONTACTS)
        }

        val photoPerm = if (Build.VERSION.SDK_INT >= 33) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        if (ContextCompat.checkSelfPermission(this, photoPerm) != PackageManager.PERMISSION_GRANTED) {
            want.add(photoPerm)
        }

        if (want.isNotEmpty()) {
            try {
                permLauncher.launch(want.toTypedArray())
            } catch (e: Exception) {
                // if the system refuses, the Settings screen still offers them
            }
        }
    }
}
