package com.hiwar.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.hiwar.app.ui.HiwarAppNav
import com.hiwar.app.ui.theme.HiwarTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HiwarTheme {
                HiwarAppNav()
            }
        }
    }
}
