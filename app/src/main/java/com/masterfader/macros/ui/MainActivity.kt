package com.masterfader.macros.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.masterfader.macros.MacroApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repo = (application as MacroApp).repository
        setContent {
            AppTheme {
                val config by repo.config.collectAsStateWithLifecycle()
                // "home", "settings", or "edit:<id>" ("edit:" alone = new macro)
                var screen by rememberSaveable { mutableStateOf("home") }
                BackHandler(enabled = screen != "home") { screen = "home" }
                when {
                    screen == "settings" -> SettingsScreen(repo, config.driver, onBack = { screen = "home" })
                    screen.startsWith("edit:") -> EditorScreen(
                        repo = repo,
                        existing = repo.macro(screen.removePrefix("edit:")),
                        onDone = { screen = "home" },
                    )
                    else -> HomeScreen(
                        repo = repo,
                        config = config,
                        onEdit = { screen = "edit:$it" },
                        onNew = { screen = "edit:" },
                        onSettings = { screen = "settings" },
                    )
                }
            }
        }
    }
}

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme(), content = content)
}
