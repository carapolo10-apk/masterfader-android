package com.masterfader.macros.automation

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import com.masterfader.macros.MacroApp
import com.masterfader.macros.core.Macro
import com.masterfader.macros.core.MacroRunner
import com.masterfader.macros.core.RunEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

/**
 * The accessibility service that drives Master Fader. Android only lets an enabled accessibility
 * service read another app's screen and inject taps/swipes, so every macro runs in here.
 */
class MasterFaderService : AccessibilityService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var running: Job? = null

    override fun onServiceConnected() {
        instance = this
        _connected.value = true
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() = Unit

    override fun onDestroy() {
        instance = null
        _connected.value = false
        scope.cancel()
        super.onDestroy()
    }

    private fun runMacro(macro: Macro) {
        if (running?.isActive == true) {
            toast("A macro is already running")
            return
        }
        val settings = (application as MacroApp).repository.config.value.driver
        val driver = AccessibilityDriver(this, settings)
        running = scope.launch {
            _status.value = "Running ${macro.name}"
            val ok = MacroRunner(driver).run(macro) { event ->
                when (event) {
                    is RunEvent.StepStarted -> _status.value = "${macro.name}: ${event.step.describe()}"
                    is RunEvent.StepFailed -> {
                        _status.value = "${macro.name} stopped at step ${event.index}: ${event.reason}"
                        toast("${macro.name} stopped: ${event.step.describe()} (${event.reason})")
                    }
                    RunEvent.Finished -> _status.value = "${macro.name} done"
                }
            }
            if (ok) toast("${macro.name} done")
        }
    }

    /** Opens Master Fader, waits for it to draw, then saves its accessibility tree for diagnosis. */
    private fun captureScreen(onDone: (File?) -> Unit) {
        val settings = (application as MacroApp).repository.config.value.driver
        scope.launch {
            val driver = AccessibilityDriver(this@MasterFaderService, settings)
            val file = runCatching {
                driver.perform(com.masterfader.macros.core.Step.LaunchMasterFader)
                delay(2500)
                val out = File(getExternalFilesDir(null) ?: filesDir, "masterfader-screen.txt")
                out.writeText(TreeDump.dump(rootInActiveWindow, driver.screenSize()))
                out
            }.getOrNull()
            toast(if (file != null) "Master Fader screen captured" else "Capture failed: is Master Fader selected in Settings?")
            onDone(file)
        }
    }

    private fun toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_LONG).show()

    companion object {
        private var instance: MasterFaderService? = null
        private val _connected = MutableStateFlow(false)
        val connected: StateFlow<Boolean> = _connected.asStateFlow()
        private val _status = MutableStateFlow("")
        val status: StateFlow<String> = _status.asStateFlow()

        /** Returns false when the accessibility service is not switched on. */
        fun run(macro: Macro): Boolean {
            val service = instance ?: return false
            service.runMacro(macro)
            return true
        }

        fun capture(onDone: (File?) -> Unit): Boolean {
            val service = instance ?: return false
            service.captureScreen(onDone)
            return true
        }

        fun openAccessibilitySettings(context: Context) {
            context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}
