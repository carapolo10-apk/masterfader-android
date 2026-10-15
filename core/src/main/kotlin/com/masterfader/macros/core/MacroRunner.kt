package com.masterfader.macros.core

import kotlinx.coroutines.delay

/**
 * Something that can carry out steps against the mixer. Today the only implementation drives the
 * Master Fader app through Android accessibility; a direct network driver can be dropped in later
 * without touching macros.
 */
interface MixerDriver {
    /** Perform one step. Throw [StepFailedException] with a user-readable message on failure. */
    suspend fun perform(step: Step)
}

class StepFailedException(message: String) : Exception(message)

sealed interface RunEvent {
    data class StepStarted(val index: Int, val total: Int, val step: Step) : RunEvent
    data class StepFailed(val index: Int, val step: Step, val reason: String) : RunEvent
    data object Finished : RunEvent
}

class MacroRunner(
    private val driver: MixerDriver,
    private val attempts: Int = 2,
    private val retryDelayMs: Long = 600,
) {
    /**
     * Runs every step of [macro] in order, preceded by opening Master Fader. Returns true when all
     * steps succeeded; stops at the first step that still fails after retrying.
     */
    suspend fun run(macro: Macro, onEvent: (RunEvent) -> Unit = {}): Boolean {
        val steps = listOf(Step.LaunchMasterFader) + macro.steps
        steps.forEachIndexed { i, step ->
            onEvent(RunEvent.StepStarted(i, steps.size, step))
            var lastError: String? = null
            for (attempt in 1..attempts) {
                try {
                    if (step is Step.Wait) delay(step.millis) else driver.perform(step)
                    lastError = null
                    break
                } catch (e: StepFailedException) {
                    lastError = e.message ?: "failed"
                    if (attempt < attempts) delay(retryDelayMs)
                }
            }
            if (lastError != null) {
                onEvent(RunEvent.StepFailed(i, step, lastError))
                return false
            }
        }
        onEvent(RunEvent.Finished)
        return true
    }
}
