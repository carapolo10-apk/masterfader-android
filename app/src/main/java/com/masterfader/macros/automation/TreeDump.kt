package com.masterfader.macros.automation

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Plain-text dump of what Master Fader exposes to accessibility: class, text, ids, bounds (also as
 * screen fractions), and whether a node is clickable or a slider. This is what tells us whether
 * faders can be set directly or need the gesture fallback, and which labels to use.
 */
object TreeDump {
    fun dump(root: AccessibilityNodeInfo?, screen: Pair<Int, Int>): String {
        if (root == null) return "No active window"
        val sb = StringBuilder("package=${root.packageName} screen=${screen.first}x${screen.second}\n")
        walk(root, 0, sb, screen)
        return sb.toString()
    }

    private fun walk(node: AccessibilityNodeInfo, depth: Int, sb: StringBuilder, screen: Pair<Int, Int>) {
        val r = Rect().also { node.getBoundsInScreen(it) }
        val fx = "%.2f".format(r.exactCenterX() / screen.first)
        val fy = "%.2f".format(r.exactCenterY() / screen.second)
        sb.append("  ".repeat(depth)).append(node.className?.toString()?.substringAfterLast('.'))
        node.text?.let { sb.append(" text=\"").append(it).append('"') }
        node.contentDescription?.let { sb.append(" desc=\"").append(it).append('"') }
        node.viewIdResourceName?.let { sb.append(" id=").append(it) }
        node.rangeInfo?.let { sb.append(" range=${it.min}..${it.max}@${it.current}") }
        if (node.isClickable) sb.append(" clickable")
        if (node.isCheckable) sb.append(" checked=${node.isChecked}")
        if (node.isScrollable) sb.append(" scrollable")
        sb.append(" bounds=").append(r.toShortString()).append(" centre=($fx,$fy)\n")
        for (i in 0 until node.childCount) node.getChild(i)?.let { walk(it, depth + 1, sb, screen) }
    }
}
