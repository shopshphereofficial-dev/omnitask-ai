package com.omnitask.ai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.omnitask.ai.ui.AppRoot
import com.omnitask.ai.ui.theme.OmniTaskTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            OmniTaskTheme {
                AppRoot()
            }
        }
    }
}
