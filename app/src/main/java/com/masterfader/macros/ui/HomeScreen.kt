package com.masterfader.macros.ui

import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.masterfader.macros.automation.MasterFaderService
import com.masterfader.macros.core.AppConfig
import com.masterfader.macros.core.Macro
import com.masterfader.macros.data.ConfigRepository

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    repo: ConfigRepository,
    config: AppConfig,
    onEdit: (String) -> Unit,
    onNew: () -> Unit,
    onSettings: () -> Unit,
) {
    val context = LocalContext.current
    val connected by MasterFaderService.connected.collectAsStateWithLifecycle()
    val status by MasterFaderService.status.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Fader Macros") },
                actions = { IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, "Settings") } },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = onNew, icon = { Icon(Icons.Default.Add, null) }, text = { Text("New macro") })
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (!connected) {
                Banner("Turn on \"Fader Macros automation\" in Accessibility so macros can operate Master Fader.", "Open settings") {
                    MasterFaderService.openAccessibilitySettings(context)
                }
            } else if (config.driver.targetPackage.isBlank()) {
                Banner("Pick the Master Fader app in Settings.", "Settings", onSettings)
            }
            if (status.isNotBlank()) {
                Text(status, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
            }
            LazyVerticalGrid(
                columns = GridCells.Adaptive(160.dp),
                contentPadding = PaddingValues(12.dp, 12.dp, 12.dp, 96.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(config.macros, key = { it.id }) { macro ->
                    MacroButton(
                        macro = macro,
                        onRun = {
                            if (!MasterFaderService.run(macro)) {
                                Toast.makeText(context, "Turn on the accessibility service first", Toast.LENGTH_LONG).show()
                                MasterFaderService.openAccessibilitySettings(context)
                            }
                        },
                        onEdit = { onEdit(macro.id) },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MacroButton(macro: Macro, onRun: () -> Unit, onEdit: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(macro.color), contentColor = Color.White),
        modifier = Modifier.fillMaxWidth().height(140.dp).combinedClickable(onClick = onRun, onLongClick = onEdit),
    ) {
        Box(Modifier.fillMaxSize().padding(12.dp)) {
            Text(
                macro.name,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.Center),
            )
            Text(
                "${macro.steps.size} step${if (macro.steps.size == 1) "" else "s"}",
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.align(Alignment.BottomStart),
            )
            IconButton(onClick = onEdit, modifier = Modifier.align(Alignment.TopEnd)) {
                Icon(Icons.Default.Edit, "Edit ${macro.name}", tint = Color.White)
            }
        }
    }
}

@Composable
private fun Banner(text: String, action: String, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(12.dp)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(text, Modifier.weight(1f))
            Button(onClick = onClick) { Text(action) }
        }
    }
}
