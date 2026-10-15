package com.masterfader.macros.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Everything the app stores: macros plus how to drive Master Fader on this particular device. */
@Serializable
data class AppConfig(
    val macros: List<Macro> = listOf(SampleMacros.chapel),
    val driver: DriverSettings = DriverSettings(),
)

/**
 * Labels and geometry used by the accessibility driver. Labels default to Master Fader 5's English
 * UI; everything is editable in Settings because the app's wording differs between versions.
 */
@Serializable
data class DriverSettings(
    /** Android package of Master Fader; picked from installed apps in Settings. */
    val targetPackage: String = "",
    val snapshotsLabel: String = "Snapshots",
    val showsLabel: String = "Shows",
    val recallLabel: String = "Recall",
    val confirmLabels: List<String> = listOf("Recall", "OK", "Yes", "Load"),
    /** Buttons that close the snapshot/show panel after a recall, tried in order if visible. */
    val closePanelLabels: List<String> = listOf("Done", "Close"),
    val muteLabel: String = "M",
    /** Pause after each tap so Master Fader can redraw, in ms. */
    val settleMs: Long = 700,
    val fader: FaderGeometry = FaderGeometry(),
)

/**
 * Fallback fader geometry in screen fractions, measured on the Mixer view in landscape. Used only
 * when Master Fader does not expose its faders as adjustable accessibility elements.
 */
@Serializable
data class FaderGeometry(
    /** Horizontal centre of the leftmost visible channel strip. */
    val firstStripX: Float = 0.10f,
    /** Distance between adjacent strips. */
    val stripSpacing: Float = 0.10f,
    /** Channel number of the leftmost visible strip. */
    val firstVisibleChannel: Int = 1,
    val stripsPerPage: Int = 8,
    /** Top (100%) and bottom (0%) of fader travel. */
    val topY: Float = 0.30f,
    val bottomY: Float = 0.88f,
    /** Where the bank/page "next" control sits, to reach channels beyond the first page. */
    val nextPageX: Float = 0.97f,
    val nextPageY: Float = 0.55f,
    val prevPageX: Float = 0.03f,
    val prevPageY: Float = 0.55f,
    val gestureMode: FaderGestureMode = FaderGestureMode.RELATIVE,
) {
    fun stripX(channel: Int): Float? {
        val slot = channel - firstVisibleChannel
        return if (slot in 0 until stripsPerPage) firstStripX + slot * stripSpacing else null
    }

    fun yFor(percent: Int): Float = bottomY - (bottomY - topY) * percent / 100f
}

/** How a Master Fader fader reacts to a finger, which decides the fallback gesture. */
@Serializable
enum class FaderGestureMode {
    /** Dragging anywhere on the strip moves the fader by the drag distance: pull to 0, then push up. */
    RELATIVE,
    /** Touching the track jumps the fader to the finger: one short drag at the target height. */
    JUMP,
}

object MacroCodec {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
        classDiscriminator = "type"
    }

    fun encode(config: AppConfig): String = json.encodeToString(AppConfig.serializer(), config)
    fun decode(text: String): AppConfig = json.decodeFromString(AppConfig.serializer(), text)
}

object SampleMacros {
    val chapel = Macro(
        id = "chapel",
        name = "Chapel scene",
        color = 0xFF6D4C41,
        steps = listOf(
            Step.RecallSnapshot("INT"),
            Step.SetVolume(ChannelSelection.range(1, 9), 75),
        ),
    )
}
