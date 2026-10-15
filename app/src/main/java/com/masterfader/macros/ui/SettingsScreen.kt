package com.masterfader.macros.ui

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.masterfader.macros.automation.MasterFaderService
import com.masterfader.macros.core.DriverSettings
import com.masterfader.macros.core.FaderGeometry
import com.masterfader.macros.core.FaderGestureMode
import com.masterfader.macros.data.ConfigRepository

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(repo: ConfigRepository, driver: DriverSettings, onBack: () -> Unit) {
    val context = LocalContext.current
    var pickingApp by remember { mutableStateOf(false) }
    var importing by remember { mutableStateOf(false) }
    fun set(t: (DriverSettings) -> DriverSettings) = repo.updateDriver(t)
    fun fader(t: (FaderGeometry) -> FaderGeometry) = repo.updateDriver { it.copy(fader = t(it.fader)) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") } },
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Section("Master Fader app")
            Text(driver.targetPackage.ifBlank { "Not chosen yet" })
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { pickingApp = true }) { Text("Choose app") }
                OutlinedButton(onClick = { MasterFaderService.openAccessibilitySettings(context) }) { Text("Accessibility") }
            }

            Section("Button labels in Master Fader")
            Hint("Macros find buttons by these words. Change them if your Master Fader version uses different wording.")
            TextSetting("Snapshots menu", driver.snapshotsLabel) { v -> set { it.copy(snapshotsLabel = v) } }
            TextSetting("Shows menu", driver.showsLabel) { v -> set { it.copy(showsLabel = v) } }
            TextSetting("Recall button", driver.recallLabel) { v -> set { it.copy(recallLabel = v) } }
            TextSetting("Confirm buttons (comma separated)", driver.confirmLabels.joinToString(", ")) { v -> set { it.copy(confirmLabels = v.csv()) } }
            TextSetting("Close panel buttons (comma separated)", driver.closePanelLabels.joinToString(", ")) { v -> set { it.copy(closePanelLabels = v.csv()) } }
            TextSetting("Mute button", driver.muteLabel) { v -> set { it.copy(muteLabel = v) } }
            NumberSetting("Pause after each action (ms)", driver.settleMs.toFloat()) { v -> set { it.copy(settleMs = v.toLong().coerceIn(0, 10_000)) } }

            Section("Fader layout (fallback)")
            Hint(
                "Used only if Master Fader doesn't let us set faders directly. Positions are fractions of the screen " +
                    "(0 = left/top, 1 = right/bottom) on the Mixer view. \"Capture screen\" below shows the exact numbers.",
            )
            NumberSetting("Leftmost strip centre X", driver.fader.firstStripX) { v -> fader { it.copy(firstStripX = v) } }
            NumberSetting("Strip spacing", driver.fader.stripSpacing) { v -> fader { it.copy(stripSpacing = v) } }
            NumberSetting("Channel number of leftmost strip", driver.fader.firstVisibleChannel.toFloat()) { v -> fader { it.copy(firstVisibleChannel = v.toInt().coerceAtLeast(1)) } }
            NumberSetting("Strips per page", driver.fader.stripsPerPage.toFloat()) { v -> fader { it.copy(stripsPerPage = v.toInt().coerceAtLeast(1)) } }
            NumberSetting("Fader top Y (100%)", driver.fader.topY) { v -> fader { it.copy(topY = v) } }
            NumberSetting("Fader bottom Y (0%)", driver.fader.bottomY) { v -> fader { it.copy(bottomY = v) } }
            NumberSetting("Next page X", driver.fader.nextPageX) { v -> fader { it.copy(nextPageX = v) } }
            NumberSetting("Next page Y", driver.fader.nextPageY) { v -> fader { it.copy(nextPageY = v) } }
            NumberSetting("Previous page X", driver.fader.prevPageX) { v -> fader { it.copy(prevPageX = v) } }
            NumberSetting("Previous page Y", driver.fader.prevPageY) { v -> fader { it.copy(prevPageY = v) } }
            Text("How faders move when dragged")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(driver.fader.gestureMode == FaderGestureMode.RELATIVE, { fader { it.copy(gestureMode = FaderGestureMode.RELATIVE) } }, { Text("Follow the drag") })
                FilterChip(driver.fader.gestureMode == FaderGestureMode.JUMP, { fader { it.copy(gestureMode = FaderGestureMode.JUMP) } }, { Text("Jump to touch") })
            }

            Section("Diagnostics")
            Hint("Opens Master Fader, records what's on screen and lets you share it, so the labels and layout can be tuned.")
            Button(onClick = {
                val started = MasterFaderService.capture { file ->
                    if (file != null) share(context, file)
                }
                if (!started) Toast.makeText(context, "Turn on the accessibility service first", Toast.LENGTH_LONG).show()
            }) { Text("Capture Master Fader screen") }

            Section("Backup")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    context.startActivity(
                        Intent.createChooser(
                            Intent(Intent.ACTION_SEND).setType("application/json").putExtra(Intent.EXTRA_TEXT, repo.exportJson()),
                            "Export macros",
                        ),
                    )
                }) { Text("Export") }
                OutlinedButton(onClick = { importing = true }) { Text("Import") }
            }
        }
    }

    if (pickingApp) AppPicker(context, onDismiss = { pickingApp = false }) { pkg ->
        set { it.copy(targetPackage = pkg) }
        pickingApp = false
    }
    if (importing) ImportDialog(onDismiss = { importing = false }) { text ->
        runCatching { repo.importJson(text) }
            .onSuccess { importing = false; Toast.makeText(context, "Imported", Toast.LENGTH_SHORT).show() }
            .onFailure { Toast.makeText(context, "That isn't a valid export: ${it.message}", Toast.LENGTH_LONG).show() }
    }
}

private fun String.csv() = split(',').map { it.trim() }.filter { it.isNotEmpty() }

private fun share(context: Context, file: java.io.File) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_STREAM, uri)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(Intent.createChooser(send, "Share screen capture").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

@Composable
private fun Section(title: String) {
    HorizontalDivider()
    Text(title, style = MaterialTheme.typography.titleMedium)
}

@Composable
private fun Hint(text: String) = Text(text, style = MaterialTheme.typography.bodySmall)

/** Text field that commits when the value changes; keeps its own text so typing isn't interrupted. */
@Composable
private fun TextSetting(label: String, value: String, onCommit: (String) -> Unit) {
    var text by remember { mutableStateOf(value) }
    OutlinedTextField(
        text,
        { text = it; if (it.isNotBlank()) onCommit(it.trim()) },
        label = { Text(label) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun NumberSetting(label: String, value: Float, onCommit: (Float) -> Unit) {
    var text by remember { mutableStateOf(if (value == value.toInt().toFloat() && value > 1f) value.toInt().toString() else value.toString()) }
    OutlinedTextField(
        text,
        { text = it; it.toFloatOrNull()?.let(onCommit) },
        label = { Text(label) },
        singleLine = true,
        isError = text.toFloatOrNull() == null,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun AppPicker(context: Context, onDismiss: () -> Unit, onPick: (String) -> Unit) {
    val apps = remember {
        val pm = context.packageManager
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        pm.queryIntentActivities(launcher, PackageManager.MATCH_ALL)
            .map { it.loadLabel(pm).toString() to it.activityInfo.packageName }
            .distinctBy { it.second }
            .filter { it.second != context.packageName }
            .sortedWith(compareBy({ !it.first.contains("fader", ignoreCase = true) }, { it.first.lowercase() }))
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Which app is Master Fader?") },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        text = {
            LazyColumn(Modifier.heightIn(max = 420.dp)) {
                items(apps) { (label, pkg) ->
                    Column(Modifier.fillMaxWidth().clickable { onPick(pkg) }.padding(vertical = 10.dp)) {
                        Text(label)
                        Text(pkg, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
    )
}

@Composable
private fun ImportDialog(onDismiss: () -> Unit, onImport: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Paste an export") },
        text = {
            Column {
                Text("This replaces all macros and settings.", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(text, { text = it }, modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp))
            }
        },
        confirmButton = { TextButton(onClick = { onImport(text) }) { Text("Import") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
