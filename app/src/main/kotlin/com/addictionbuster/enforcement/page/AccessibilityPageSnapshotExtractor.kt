package com.addictionbuster.enforcement.page

import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.addictionbuster.enforcement.InvalidEnforcementContextException
import com.addictionbuster.enforcement.PageSnapshot

class AccessibilityPageSnapshotExtractor(
    private val maxNodes: Int = 250,
    private val maxTextChars: Int = 12_000
) {
    init {
        if (maxNodes <= 0) {
            throw InvalidEnforcementContextException("maxNodes must be > 0")
        }
        if (maxTextChars <= 0) {
            throw InvalidEnforcementContextException("maxTextChars must be > 0")
        }
    }

    fun extract(
        event: AccessibilityEvent?,
        rootNode: AccessibilityNodeInfo?
    ): PageSnapshot? {
        val activityClassName = event?.className?.toString().orEmpty()
        if (rootNode == null) {
            return if (activityClassName.isBlank()) null else PageSnapshot(activityClassName, "")
        }
        val textCollector = StringBuilder()
        var visited = 0

        fun visit(node: AccessibilityNodeInfo?) {
            if (node == null || visited >= maxNodes || textCollector.length >= maxTextChars) return
            visited += 1
            appendNodeText(textCollector, node.text)
            appendNodeText(textCollector, node.contentDescription)
            for (index in 0 until node.childCount) {
                visit(node.getChild(index))
            }
        }

        visit(rootNode)
        return PageSnapshot(
            activityClassName = activityClassName,
            visibleText = textCollector.toString().take(maxTextChars)
        )
    }

    private fun appendNodeText(builder: StringBuilder, value: CharSequence?) {
        val text = value?.toString()?.trim().orEmpty()
        if (text.isBlank()) return
        if (builder.isNotEmpty()) {
            builder.append('\n')
        }
        builder.append(text)
    }
}
