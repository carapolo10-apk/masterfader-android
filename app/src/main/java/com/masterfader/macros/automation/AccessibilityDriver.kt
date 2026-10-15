package com.masterfader.macros.automation

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.os.Bundle
import android.util.DisplayMetrics
import android.view.Display
import android.view.accessibility.AccessibilityNodeInfo
import com.masterfader.macros.core.DriverSettings
import com.masterfader.macros.core.FaderGestureMode
import com.masterfader.macros.core.MixerDriver
import com.masterfader.macros.core.Step
import com.masterfader.macros.core.StepFailedException
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Carries out macro steps by operating the Master Fader app's own screen: finding buttons by their
 * label, clicking them, and moving faders. Master Fader then talks to the mixer exactly as if a
 * person had done it, so nothing about the mixer's private network protocol is needed.
 */
class AccessibilityDriver(
    private val service: AccessibilityService,
    private val settings: DriverSettings,
) : MixerDriver {

    override suspend fun perform(step: Step) {
        when (step) {
            Step.LaunchMasterFader -> launch()
            is Step.OpenShow -> openShow(step.showName)
            is Step.RecallSnapshot -> recallSnapshot(step.snapshotName)
            is Step.SetVolume -> step.channels.channels.forEachPaged { _, x -> setFader(x, step.percent) }
            is Step.SetMute -> setMutes(step.channels.channels, step.muted)
            is Step.TapText -> tapText(step.text, timeoutMs = 3000) ?: fail("\"${step.text}\" is not on screen")
            is Step.TapPoint -> tap(step.x, step.y)
            is Step.Swipe -> swipe(step.x1, step.y1, step.x2, step.y2, step.durationMs)
            is Step.Wait -> delay(step.millis)
        }
        delay(settings.settleMs)
    }

    // ---- App ---------------------------------------------------------------------------------

    private suspend fun launch() {
        val pkg = settings.targetPackage.ifBlank { fail("Choose the Master Fader app in Settings first") }
        if (root()?.packageName?.toString() == pkg) return
        val intent = service.packageManager.getLaunchIntentForPackage(pkg) ?: fail("$pkg is not installed")
        service.startActivity(intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
        repeat(40) {
            delay(250)
            if (root()?.packageName?.toString() == pkg) {
                delay(1500) // let it reconnect to the mixer and draw
                return
            }
        }
        fail("Master Fader did not come to the front")
    }

    private fun root(): AccessibilityNodeInfo? = service.rootInActiveWindow

    private fun requireMasterFaderRoot(): AccessibilityNodeInfo {
        val r = root() ?: fail("Can't read the screen")
        if (r.packageName?.toString() != settings.targetPackage) fail("Master Fader is not on screen")
        return r
    }

    // ---- Shows and snapshots -------------------------------------------------------------------

    private suspend fun openShow(name: String) {
        if (find(name) == null) tapText(settings.showsLabel, 3000) ?: fail("No \"${settings.showsLabel}\" button")
        tapText(name, 3000) ?: fail("Show \"$name\" not found")
        confirmIfAsked()
        closePanel()
    }

    private suspend fun recallSnapshot(name: String) {
        if (find(name) == null) tapText(settings.snapshotsLabel, 3000) ?: fail("No \"${settings.snapshotsLabel}\" button")
        tapText(name, 3000) ?: fail("Snapshot \"$name\" not found")
        delay(settings.settleMs)
        tapText(settings.recallLabel, 1500)
        confirmIfAsked()
        closePanel()
    }

    private suspend fun confirmIfAsked() {
        delay(settings.settleMs)
        for (label in settings.confirmLabels) if (tapText(label, 600) != null) return
    }

    private suspend fun closePanel() {
        delay(settings.settleMs)
        for (label in settings.closePanelLabels) if (tapText(label, 400) != null) return
    }

    // ---- Faders --------------------------------------------------------------------------------

    /**
     * Visits channels in ascending order, paging Master Fader's channel banks with the calibrated
     * next/previous buttons, and returns to the first page afterwards.
     */
    private suspend fun List<Int>.forEachPaged(action: suspend (channel: Int, stripX: Float) -> Unit) {
        val g = settings.fader
        var page = 0
        for (ch in sorted()) {
            val target = (ch - g.firstVisibleChannel).floorDiv(g.stripsPerPage)
            if (target < 0) fail("Channel $ch is left of the first calibrated strip")
            while (page < target) { tap(g.nextPageX, g.nextPageY); page++; delay(settings.settleMs) }
            val slot = (ch - g.firstVisibleChannel) - target * g.stripsPerPage
            action(ch, g.firstStripX + slot * g.stripSpacing)
        }
        while (page > 0) { tap(g.prevPageX, g.prevPageY); page--; delay(settings.settleMs) }
    }

    private suspend fun setFader(stripX: Float, percent: Int) {
        // Best case: Master Fader exposes the fader as a slider we can set exactly.
        sliderNear(stripX)?.let { node ->
            val info = node.rangeInfo
            val value = info.min + (info.max - info.min) * percent / 100f
            val args = Bundle().apply { putFloat(AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE, value) }
            if (node.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS.id, args)) return
        }
        // Fallback: move it with a finger gesture using the calibrated geometry.
        val g = settings.fader
        when (g.gestureMode) {
            FaderGestureMode.RELATIVE -> {
                val travel = g.bottomY - g.topY
                swipe(stripX, g.topY, stripX, (g.bottomY + travel * 0.1f).coerceAtMost(0.99f), 350)
                delay(150)
                swipe(stripX, g.bottomY, stripX, g.bottomY - travel * percent / 100f, 450)
            }
            FaderGestureMode.JUMP -> {
                val y = g.yFor(percent)
                swipe(stripX, y, stripX, y + 0.001f, 120)
            }
        }
    }

    /** A vertical slider whose centre sits within half a strip of [stripX], if Master Fader exposes one. */
    private fun sliderNear(stripX: Float): AccessibilityNodeInfo? {
        val (w, _) = screenSize()
        val half = settings.fader.stripSpacing * w / 2
        return allNodes(requireMasterFaderRoot())
            .filter { it.rangeInfo != null }
            .map { it to Rect().also(it::getBoundsInScreen) }
            .filter { (_, r) -> r.height() > r.width() && kotlin.math.abs(r.exactCenterX() - stripX * w) <= half }
            .minByOrNull { (_, r) -> kotlin.math.abs(r.exactCenterX() - stripX * w) }
            ?.first
    }

    // ---- Mutes ---------------------------------------------------------------------------------

    private suspend fun setMutes(channels: List<Int>, muted: Boolean) {
        channels.forEachPaged { ch, x ->
            val (w, _) = screenSize()
            val button = allNodes(requireMasterFaderRoot())
                .filter { it.labelEquals(settings.muteLabel) }
                .map { it to Rect().also(it::getBoundsInScreen) }
                .minByOrNull { (_, r) -> kotlin.math.abs(r.exactCenterX() - x * w) }
                ?.takeIf { (_, r) -> kotlin.math.abs(r.exactCenterX() - x * w) <= settings.fader.stripSpacing * w / 2 }
                ?.first ?: fail("No \"${settings.muteLabel}\" button found for channel $ch")
            val isMuted = if (button.isCheckable) button.isChecked else button.isSelected
            if (isMuted != muted) click(button)
        }
    }

    // ---- Primitives ----------------------------------------------------------------------------

    private fun allNodes(root: AccessibilityNodeInfo): List<AccessibilityNodeInfo> {
        val out = ArrayList<AccessibilityNodeInfo>()
        fun walk(n: AccessibilityNodeInfo) {
            out += n
            for (i in 0 until n.childCount) n.getChild(i)?.let(::walk)
        }
        walk(root)
        return out
    }

    private fun AccessibilityNodeInfo.labelEquals(label: String) =
        text?.toString()?.trim().equals(label, ignoreCase = true) ||
            contentDescription?.toString()?.trim().equals(label, ignoreCase = true)

    /** Exact (case-insensitive) label match first, otherwise the first node containing the text. */
    private fun find(label: String): AccessibilityNodeInfo? {
        val r = root() ?: return null
        if (r.packageName?.toString() != settings.targetPackage) return null
        val exact = allNodes(r).firstOrNull { it.isVisibleToUser && it.labelEquals(label) }
        return exact ?: r.findAccessibilityNodeInfosByText(label).firstOrNull { it.isVisibleToUser }
    }

    private suspend fun tapText(label: String, timeoutMs: Long): Unit? {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (true) {
            find(label)?.let { click(it); return Unit }
            if (System.currentTimeMillis() > deadline) return null
            delay(200)
        }
    }

    /** Clicks the node or its nearest clickable ancestor; taps its centre if nothing is clickable. */
    private suspend fun click(node: AccessibilityNodeInfo) {
        var n: AccessibilityNodeInfo? = node
        while (n != null) {
            if (n.isClickable && n.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return
            n = n.parent
        }
        val r = Rect().also(node::getBoundsInScreen)
        val (w, h) = screenSize()
        tap(r.exactCenterX() / w, r.exactCenterY() / h)
    }

    private suspend fun tap(x: Float, y: Float) = swipe(x, y, x, y, 60)

    private suspend fun swipe(x1: Float, y1: Float, x2: Float, y2: Float, durationMs: Long) {
        val (w, h) = screenSize()
        val path = Path().apply {
            moveTo(x1 * w, y1 * h)
            lineTo(x2 * w, y2 * h)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, durationMs.coerceAtLeast(1)))
            .build()
        val done = suspendCancellableCoroutine { cont ->
            val ok = service.dispatchGesture(gesture, object : AccessibilityService.GestureResultCallback() {
                override fun onCompleted(g: GestureDescription?) { if (cont.isActive) cont.resume(true) }
                override fun onCancelled(g: GestureDescription?) { if (cont.isActive) cont.resume(false) }
            }, null)
            if (!ok && cont.isActive) cont.resume(false)
        }
        if (!done) fail("The touch gesture was interrupted")
    }

    /** Full physical screen size in pixels, matching gesture coordinates. */
    fun screenSize(): Pair<Int, Int> {
        val display = service.getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY)
        val m = DisplayMetrics()
        @Suppress("DEPRECATION") display.getRealMetrics(m)
        return m.widthPixels to m.heightPixels
    }

    private fun fail(message: String): Nothing = throw StepFailedException(message)
}
