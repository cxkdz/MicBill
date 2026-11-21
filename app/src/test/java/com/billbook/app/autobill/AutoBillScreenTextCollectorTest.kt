package com.billbook.app.autobill

import org.junit.Assert.*
import org.junit.Test

class AutoBillScreenTextCollectorTest {
    private class TestNode(
        override val visible: Boolean = true,
        override val editable: Boolean = false,
        override val password: Boolean = false,
        private val nodeText: String? = null,
        override val contentDescription: CharSequence? = null,
        override val stateDescription: CharSequence? = null,
        override val paneTitle: CharSequence? = null,
        val children: List<TestNode?> = emptyList(),
        private val failOnTextRead: Boolean = false
    ) : AutoBillScreenTextCollector.Node {
        var releases = 0
        var childReads = 0
        override val text: CharSequence?
            get() {
                check(!failOnTextRead) { "Text must not be read" }
                return nodeText
            }
        override val childCount: Int get() = children.size
        override fun getChild(index: Int): TestNode? {
            childReads++
            return children[index]
        }
        override fun release() { releases++ }
    }

    @Test fun visibleDescendantsOfHiddenContainersAreCollectedWithoutHiddenText() {
        val amount = TestNode(nodeText = "￥18.00")
        val hidden = TestNode(visible = false, nodeText = "￥999.00", children = listOf(amount))
        val root = TestNode(nodeText = "对方已收款", children = listOf(hidden))

        val snapshot = AutoBillScreenTextCollector.collect(root)

        assertEquals(listOf("对方已收款", "￥18.00"), snapshot.texts)
        assertEquals(3, snapshot.stats.nodesVisited)
        assertEquals(2, snapshot.stats.visibleNodes)
        assertEquals(2, snapshot.stats.textBearingNodes)
        assertFalse(snapshot.stats.truncated)
        assertEquals(0, root.releases)
        assertEquals(1, hidden.releases)
        assertEquals(1, amount.releases)
    }

    @Test fun editableAndPasswordSubtreesCannotContributeText() {
        val protectedChild = TestNode(nodeText = "￥888.00")
        val password = TestNode(password = true, failOnTextRead = true, children = listOf(protectedChild))
        val editable = TestNode(editable = true, failOnTextRead = true, children = listOf(protectedChild))
        val root = TestNode(children = listOf(password, editable, TestNode(nodeText = "￥18.00")))

        val snapshot = AutoBillScreenTextCollector.collect(root)

        assertEquals(listOf("￥18.00"), snapshot.texts)
        assertEquals(4, snapshot.stats.nodesVisited)
        assertEquals(1, snapshot.stats.passwordNodes)
        assertEquals(1, snapshot.stats.editableNodes)
        assertEquals(0, password.childReads)
        assertEquals(0, editable.childReads)
        assertEquals(0, protectedChild.releases)
    }

    @Test fun semanticFieldsAreTrimmedBoundedAndDeduplicatedInTraversalOrder() {
        val longText = "a".repeat(350)
        val root = TestNode(
            nodeText = "  对方已收款  ",
            contentDescription = "对方已收款",
            stateDescription = " 已存入零钱 ",
            paneTitle = " 转账详情 ",
            children = listOf(TestNode(nodeText = " \n "), TestNode(nodeText = longText),
                TestNode(nodeText = "a".repeat(300)))
        )

        val snapshot = AutoBillScreenTextCollector.collect(root)

        assertEquals(listOf("对方已收款", "已存入零钱", "转账详情", "a".repeat(300)), snapshot.texts)
        assertEquals(3, snapshot.stats.textBearingNodes)
        assertFalse(snapshot.stats.truncated)
    }

    @Test fun nodeLimitStopsBeforeFetchingMoreChildrenAndReleasesEveryAcquiredNode() {
        val children = List(405) { TestNode(nodeText = "text $it") }
        val root = TestNode(children = children)

        val snapshot = AutoBillScreenTextCollector.collect(root)

        assertEquals(400, snapshot.stats.nodesVisited)
        assertEquals(399, root.childReads)
        assertEquals(399, snapshot.texts.size)
        assertTrue(snapshot.stats.truncated)
        assertTrue(children.take(399).all { it.releases == 1 })
        assertTrue(children.drop(399).all { it.releases == 0 })
    }

    @Test fun exactNodeLimitWithoutOmittedNodesIsNotTruncated() {
        val root = TestNode(children = List(399) { TestNode() })

        val snapshot = AutoBillScreenTextCollector.collect(root)

        assertEquals(400, snapshot.stats.nodesVisited)
        assertFalse(snapshot.stats.truncated)
    }

    @Test fun depthLimitKeepsTheBoundaryNodeAndDoesNotFetchDeeperContent() {
        var root = TestNode(nodeText = "depth 32")
        for (depth in 31 downTo 0) root = TestNode(nodeText = "depth $depth", children = listOf(root))

        val snapshot = AutoBillScreenTextCollector.collect(root)

        assertEquals(31, snapshot.stats.nodesVisited)
        assertEquals("depth 30", snapshot.texts.last())
        assertFalse(snapshot.texts.contains("depth 31"))
        assertTrue(snapshot.stats.truncated)
    }

    @Test fun nullChildrenAreCountedAndDoNotHideLaterText() {
        val root = TestNode(children = listOf(null, TestNode(nodeText = "￥18.00"), null))

        val snapshot = AutoBillScreenTextCollector.collect(root)

        assertEquals(listOf("￥18.00"), snapshot.texts)
        assertEquals(2, snapshot.stats.nodesVisited)
        assertEquals(2, snapshot.stats.nullChildren)
        assertFalse(snapshot.stats.truncated)
    }

    @Test fun staleTreesWithManyNullChildrenStillHaveABoundedQueryBudget() {
        val root = TestNode(children = List(1_000) { null })

        val snapshot = AutoBillScreenTextCollector.collect(root)

        assertEquals(1, snapshot.stats.nodesVisited)
        assertEquals(400, snapshot.stats.nullChildren)
        assertEquals(400, root.childReads)
        assertTrue(snapshot.stats.truncated)
    }

    @Test fun acquiredChildrenAreReleasedIfReadingFails() {
        val failing = TestNode(failOnTextRead = true)
        val root = TestNode(children = listOf(failing))
        try {
            AutoBillScreenTextCollector.collect(root)
            fail("Expected collection failure")
        } catch (_: IllegalStateException) {
            assertEquals(1, failing.releases)
            assertEquals(0, root.releases)
        }
    }
}
