package com.masterfader.macros.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.masterfader.macros.automation.MasterFaderService
import com.masterfader.macros.core.ChannelSelection
import com.masterfader.macros.core.Macro
import com.masterfader.macros.core.Step
import com.masterfader.macros.data.ConfigRepository
import java.util.UUID

private val palette = listOf(0xFF3D5AFE, 0xFF6D4C41, 0xFF2E7D32, 0xFFC62828, 0xFF6A1B9A, 0xFFEF6C00, 0xFF00838F, 0xFF37474F)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(repo: ConfigRepository, existing: Macro?, onDone: () -> Unit) {
    val context = LocalContext.current
    val id = remember { existing?.id ?: UUID.randomUUID().toString() }
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var color by remember { mutableStateOf(existing?.color ?: palette.first()) }
    val steps = remember { mutableStateListOf<Step>().apply { addAll(existing?.steps ?: emptyList()) } }
    // null = closed; index -1 = adding a new step
    var editing by remember { mutableStateOf<Pair<Int, Step?>?>(null) }

    fun current() = Macro(id, name.ifBlank { "Untitled" }, color, steps.toList())

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (existing == null) "New macro" else "Edit macro") },
                navigationIcon = { IconButton(onClick = onDone) { Icon(Icons.Default.ArrowBack, "Back") } },
                actions = {
                    if (existing != null) IconButton(onClick = { repo.deleteMacro(id); onDone() }) { Icon(Icons.Default.Delete, "Delete macro") }
                    TextButton(onClick = { repo.upsertMacro(current()); onDone() }) { Text("Save") }
                },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                OutlinedTextField(name, { name = it }, label = { Text("Button name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 4.dp)) {
                    palette.forEach { c ->
                        Spacer(
                            Modifier.size(36.dp).background(Color(c), CircleShape)
                                .then(if (c == color) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier)
                                .clickable { color = c },
                        )
                    }
                }
            }
            item { Text("Steps (run top to bottom, after opening Master Fader)", style = MaterialTheme.typography.titleSmall) }
            itemsIndexed(steps) { i, step ->
                Card(Modifier.fillMaxWidth().clickable { editing = i to step }) {
                    Row(Modifier.padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("${i + 1}. ${step.describe()}", Modifier.weight(1f))
                        IconButton(onClick = { if (i > 0) steps.add(i - 1, steps.removeAt(i)) }) { Icon(Icons.Default.KeyboardArrowUp, "Move up") }
                        IconButton(onClick = { if (i < steps.lastIndex) steps.add(i + 1, steps.removeAt(i)) }) { Icon(Icons.Default.KeyboardArrowDown, "Move down") }
                        IconButton(onClick = { steps.removeAt(i) }) { Icon(Icons.Default.Delete, "Remove step") }
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { editing = -1 to null }) { Text("Add step") }
                    OutlinedButton(onClick = {
                        if (!MasterFaderService.run(current())) Toast.makeText(context, "Turn on the accessibility service first", Toast.LENGTH_LONG).show()
                    }) { Text("Test run") }
                    OutlinedButton(onClick = {
                        repo.upsertMacro(current())
                        Shortcuts.pin(context, current())
                    }) { Text("Add to home screen") }
                }
            }
        }
    }

    editing?.let { (index, step) ->
        StepDialog(
            initial = step,
            onDismiss = { editing = null },
            onSave = { s ->
                if (index < 0) steps.add(s) else steps[index] = s
                editing = null
            },
        )
    }
}

private enum class StepKind(val label: String) {
    Snapshot("Recall snapshot"), Show("Open show"), Volume("Set channel volume"), Mute("Mute / unmute"),
    TapText("Tap a button by its text"), TapPoint("Tap a screen point"), Swipe("Swipe"), Wait("Wait"),
}

private fun kindOf(step: Step?): StepKind = when (step) {
    is Step.OpenShow -> StepKind.Show
    is Step.SetVolume -> StepKind.Volume
    is Step.SetMute -> StepKind.Mute
    is Step.TapText -> StepKind.TapText
    is Step.TapPoint -> StepKind.TapPoint
    is Step.Swipe -> StepKind.Swipe
    is Step.Wait -> StepKind.Wait
    else -> StepKind.Snapshot
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StepDialog(initial: Step?, onDismiss: () -> Unit, onSave: (Step) -> Unit) {
    var kind by remember { mutableStateOf(kindOf(initial)) }
    var text by remember {
        mutableStateOf(
            when (initial) {
                is Step.RecallSnapshot -> initial.snapshotName
                is Step.OpenShow -> initial.showName
                is Step.TapText -> initial.text
                else -> ""
            },
        )
    }
    var channels by remember {
        mutableStateOf(
            when (initial) {
                is Step.SetVolume -> initial.channels.describe().substringAfter(' ')
                is Step.SetMute -> initial.channels.describe().substringAfter(' ')
                else -> "1-8"
            },
        )
    }
    var percent by remember { mutableStateOf((initial as? Step.SetVolume)?.percent?.toFloat() ?: 75f) }
    var muted by remember { mutableStateOf((initial as? Step.SetMute)?.muted ?: true) }
    var coords by remember {
        mutableStateOf(
            when (initial) {
                is Step.TapPoint -> "${initial.x}, ${initial.y}"
                is Step.Swipe -> "${initial.x1}, ${initial.y1}, ${initial.x2}, ${initial.y2}"
                is Step.Wait -> initial.millis.toString()
                else -> ""
            },
        )
    }
    var problem by remember { mutableStateOf<String?>(null) }

    fun build(): Step {
        fun nums(n: Int): List<Float> {
            val v = coords.split(',', ' ').filter { it.isNotBlank() }.map { it.trim().toFloatOrNull() ?: error("Numbers only") }
            require(v.size == n) { "Enter $n numbers separated by commas" }
            require(v.all { it in 0f..1f }) { "Positions are fractions of the screen, 0.0 to 1.0" }
            return v
        }
        return when (kind) {
            StepKind.Snapshot -> Step.RecallSnapshot(text.trim().also { require(it.isNotEmpty()) { "Enter the snapshot name" } })
            StepKind.Show -> Step.OpenShow(text.trim().also { require(it.isNotEmpty()) { "Enter the show name" } })
            StepKind.TapText -> Step.TapText(text.trim().also { require(it.isNotEmpty()) { "Enter the button text" } })
            StepKind.Volume -> Step.SetVolume(ChannelSelection.parse(channels), percent.toInt())
            StepKind.Mute -> Step.SetMute(ChannelSelection.parse(channels), muted)
            StepKind.TapPoint -> nums(2).let { Step.TapPoint(it[0], it[1]) }
            StepKind.Swipe -> nums(4).let { Step.Swipe(it[0], it[1], it[2], it[3]) }
            StepKind.Wait -> Step.Wait(coords.trim().toLongOrNull()?.takeIf { it > 0 } ?: error("Enter milliseconds, e.g. 1000"))
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Add step" else "Edit step") },
        confirmButton = {
            TextButton(onClick = {
                runCatching(::build).onSuccess(onSave).onFailure { problem = it.message }
            }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Column {
                    StepKind.entries.chunked(2).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            row.forEach { k -> FilterChip(selected = k == kind, onClick = { kind = k; problem = null }, label = { Text(k.label) }) }
                        }
                    }
                }
                when (kind) {
                    StepKind.Snapshot, StepKind.Show, StepKind.TapText -> OutlinedTextField(
                        text, { text = it }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                        label = { Text(when (kind) { StepKind.Snapshot -> "Snapshot name, e.g. INT"; StepKind.Show -> "Show name"; else -> "Button text" }) },
                    )
                    StepKind.Volume, StepKind.Mute -> {
                        OutlinedTextField(channels, { channels = it }, singleLine = true, label = { Text("Channels, e.g. 1-9 or 1-4, 7") }, modifier = Modifier.fillMaxWidth())
                        if (kind == StepKind.Volume) {
                            Text("Fader position: ${percent.toInt()}%")
                            Slider(percent, { percent = it }, valueRange = 0f..100f, steps = 19)
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(if (muted) "Mute" else "Unmute", Modifier.width(80.dp))
                                Switch(muted, { muted = it })
                            }
                        }
                    }
                    StepKind.TapPoint, StepKind.Swipe, StepKind.Wait -> OutlinedTextField(
                        coords, { coords = it }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                        label = {
                            Text(when (kind) {
                                StepKind.TapPoint -> "x, y as screen fractions, e.g. 0.5, 0.2"
                                StepKind.Swipe -> "x1, y1, x2, y2 as screen fractions"
                                else -> "Milliseconds"
                            })
                        },
                    )
                }
                problem?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
    )
}
