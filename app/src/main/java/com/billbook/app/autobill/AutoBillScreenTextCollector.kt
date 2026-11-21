package com.billbook.app.autobill

/** Collects one verified payment-window snapshot without retaining platform nodes. */
object AutoBillScreenTextCollector {
    interface Node {
        val visible: Boolean
        val editable: Boolean
        val password: Boolean
        val text: CharSequence?
        val contentDescription: CharSequence?
        val stateDescription: CharSequence?
        val paneTitle: CharSequence?
        val childCount: Int
        fun getChild(index: Int): Node?
        fun release()
    }

    /** Counts only; safe to include in diagnostics without payment text. */
    data class Stats(
        val nodesVisited: Int,
        val visibleNodes: Int,
        val textBearingNodes: Int,
        val passwordNodes: Int,
        val editableNodes: Int,
        val nullChildren: Int,
        val truncated: Boolean
    )

    data class Snapshot(val texts: List<String>, val stats: Stats)

    private const val MAX_NODES = 400
    private const val MAX_DEPTH = 30
    private const val MAX_TEXT_LENGTH = 300

    /**
     * The caller owns root. Every child acquired here is released, including on
     * failure. Hidden containers can have visible children, while password and
     * editable subtrees are excluded entirely. Text-bearing counts eligible
     * visible nodes before deduplication.
     */
    fun collect(root: Node): Snapshot {
        val texts = linkedSetOf<String>()
        var nodesVisited = 0
        var visibleNodes = 0
        var textBearingNodes = 0
        var passwordNodes = 0
        var editableNodes = 0
        var nullChildren = 0
        var childReads = 0
        var truncated = false

        fun visit(node: Node, depth: Int) {
            nodesVisited++
            val visible = node.visible
            val password = node.password
            val editable = node.editable
            if (visible) visibleNodes++
            if (password) passwordNodes++
            if (editable) editableNodes++
            if (password || editable) return

            if (visible) {
                val values = listOf(node.text, node.contentDescription, node.stateDescription, node.paneTitle)
                    .mapNotNull { it?.toString()?.trim()?.takeIf(String::isNotEmpty)?.take(MAX_TEXT_LENGTH) }
                if (values.isNotEmpty()) textBearingNodes++
                texts.addAll(values)
            }

            val childCount = node.childCount.coerceAtLeast(0)
            if (depth >= MAX_DEPTH) {
                if (childCount > 0) truncated = true
                return
            }
            for (index in 0 until childCount) {
                // Bound child queries as well: stale trees may report many null children.
                if (nodesVisited >= MAX_NODES || childReads >= MAX_NODES) {
                    truncated = true
                    break
                }
                childReads++
                val child = node.getChild(index)
                if (child == null) {
                    nullChildren++
                    continue
                }
                try {
                    visit(child, depth + 1)
                } finally {
                    child.release()
                }
            }
        }

        visit(root, 0)
        return Snapshot(
            texts.toList(),
            Stats(nodesVisited, visibleNodes, textBearingNodes, passwordNodes, editableNodes, nullChildren, truncated)
        )
    }
}
