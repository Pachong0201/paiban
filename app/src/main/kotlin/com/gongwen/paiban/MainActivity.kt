package com.gongwen.paiban

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.gongwen.paiban.ui.HomeScreen
import com.gongwen.paiban.ui.theme.PaibanTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PaibanTheme {
                HomeScreen()
            }
        }
    }
}
