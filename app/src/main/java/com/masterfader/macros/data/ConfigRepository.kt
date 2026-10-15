package com.masterfader.macros.data

import android.content.Context
import com.masterfader.macros.core.AppConfig
import com.masterfader.macros.core.DriverSettings
import com.masterfader.macros.core.Macro
import com.masterfader.macros.core.MacroCodec
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.File

/** Keeps macros and driver settings in one JSON file in app storage. */
class ConfigRepository(context: Context) {
    private val file = File(context.filesDir, "config.json")
    private val _config = MutableStateFlow(load())
    val config: StateFlow<AppConfig> = _config.asStateFlow()

    private fun load(): AppConfig =
        if (file.exists()) runCatching { MacroCodec.decode(file.readText()) }.getOrElse { AppConfig() }
        else AppConfig()

    private fun save(config: AppConfig) {
        val tmp = File(file.parentFile, "config.json.tmp")
        tmp.writeText(MacroCodec.encode(config))
        tmp.renameTo(file)
    }

    private fun change(transform: (AppConfig) -> AppConfig) {
        _config.update(transform)
        save(_config.value)
    }

    fun macro(id: String): Macro? = _config.value.macros.find { it.id == id }

    fun upsertMacro(macro: Macro) = change { c ->
        val exists = c.macros.any { it.id == macro.id }
        c.copy(macros = if (exists) c.macros.map { if (it.id == macro.id) macro else it } else c.macros + macro)
    }

    fun deleteMacro(id: String) = change { c -> c.copy(macros = c.macros.filterNot { it.id == id }) }

    fun moveMacro(id: String, delta: Int) = change { c ->
        val list = c.macros.toMutableList()
        val i = list.indexOfFirst { it.id == id }
        val j = i + delta
        if (i < 0 || j !in list.indices) c else c.copy(macros = list.apply { add(j, removeAt(i)) })
    }

    fun updateDriver(transform: (DriverSettings) -> DriverSettings) = change { c -> c.copy(driver = transform(c.driver)) }

    fun exportJson(): String = MacroCodec.encode(_config.value)

    /** Replaces everything with the pasted JSON. Throws if it does not parse. */
    fun importJson(text: String) {
        val parsed = MacroCodec.decode(text)
        change { parsed }
    }
}
