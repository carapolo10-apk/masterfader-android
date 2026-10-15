package com.masterfader.macros.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A one-button task, e.g. "Chapel scene": recall the INT snapshot, then set channels 1-9 to 75%.
 */
@Serializable
data class Macro(
    val id: String,
    val name: String,
    /** ARGB colour of the button on the home screen. */
    val color: Long = 0xFF3D5AFE,
    val steps: List<Step> = emptyList(),
)

/** One action inside a macro. Steps run in order; the macro stops at the first failing step. */
@Serializable
sealed interface Step {
    /** Short human description shown in the editor and in run progress. */
    fun describe(): String

    /** Bring the Master Fader app to the foreground. Added implicitly before every run. */
    @Serializable @SerialName("launch")
    data object LaunchMasterFader : Step {
        override fun describe() = "Open Master Fader"
    }

    /** Load a show ("scene" file) from Master Fader's Shows menu. */
    @Serializable @SerialName("open_show")
    data class OpenShow(val showName: String) : Step {
        override fun describe() = "Open show \"$showName\""
    }

    /** Recall a snapshot by name from the current show. */
    @Serializable @SerialName("recall_snapshot")
    data class RecallSnapshot(val snapshotName: String) : Step {
        override fun describe() = "Recall snapshot \"$snapshotName\""
    }

    /** Move channel faders to a percentage of fader travel (0 = bottom, 100 = top). */
    @Serializable @SerialName("set_volume")
    data class SetVolume(val channels: ChannelSelection, val percent: Int) : Step {
        init { require(percent in 0..100) { "percent must be 0..100" } }
        override fun describe() = "Set ${channels.describe()} to $percent%"
    }

    /** Mute or unmute channels. */
    @Serializable @SerialName("set_mute")
    data class SetMute(val channels: ChannelSelection, val muted: Boolean) : Step {
        override fun describe() = "${if (muted) "Mute" else "Unmute"} ${channels.describe()}"
    }

    /** Tap the first on-screen element whose text matches (escape hatch for anything not modelled). */
    @Serializable @SerialName("tap_text")
    data class TapText(val text: String) : Step {
        override fun describe() = "Tap \"$text\""
    }

    /** Tap a screen position given as fractions of screen width/height (0.0..1.0). */
    @Serializable @SerialName("tap_point")
    data class TapPoint(val x: Float, val y: Float) : Step {
        override fun describe() = "Tap at (${pct(x)}, ${pct(y)})"
    }

    /** Swipe between two fractional screen positions. */
    @Serializable @SerialName("swipe")
    data class Swipe(val x1: Float, val y1: Float, val x2: Float, val y2: Float, val durationMs: Long = 300) : Step {
        override fun describe() = "Swipe (${pct(x1)}, ${pct(y1)}) → (${pct(x2)}, ${pct(y2)})"
    }

    @Serializable @SerialName("wait")
    data class Wait(val millis: Long) : Step {
        override fun describe() = "Wait ${millis} ms"
    }
}

private fun pct(f: Float) = "${(f * 100).toInt()}%"

/** A set of input channels, written the way people type it: "1-9", "1-4, 7, 12-16". */
@Serializable
data class ChannelSelection(val channels: List<Int>) {
    init { require(channels.all { it >= 1 }) { "channels are numbered from 1" } }

    fun describe(): String {
        if (channels.isEmpty()) return "no channels"
        val ranges = mutableListOf<String>()
        var start = channels.first()
        var prev = start
        for (c in channels.drop(1) + Int.MIN_VALUE) {
            if (c == prev + 1) { prev = c; continue }
            ranges += if (start == prev) "$start" else "$start-$prev"
            start = c; prev = c
        }
        return (if (channels.size == 1) "channel " else "channels ") + ranges.joinToString(", ")
    }

    override fun toString() = describe()

    companion object {
        fun parse(text: String): ChannelSelection {
            val out = sortedSetOf<Int>()
            text.replace(Regex("\\s*[-–]\\s*"), "-").split(',', ';', ' ').map { it.trim() }.filter { it.isNotEmpty() }.forEach { part ->
                val bounds = part.split('-', '–').map { it.trim() }
                when (bounds.size) {
                    1 -> out += bounds[0].toIntOrNull() ?: throw IllegalArgumentException("Not a channel: \"$part\"")
                    2 -> {
                        val a = bounds[0].toIntOrNull() ?: throw IllegalArgumentException("Not a range: \"$part\"")
                        val b = bounds[1].toIntOrNull() ?: throw IllegalArgumentException("Not a range: \"$part\"")
                        out += minOf(a, b)..maxOf(a, b)
                    }
                    else -> throw IllegalArgumentException("Not a range: \"$part\"")
                }
            }
            require(out.isNotEmpty()) { "Enter at least one channel" }
            return ChannelSelection(out.toList())
        }

        fun range(from: Int, to: Int) = ChannelSelection((from..to).toList())
    }
}
