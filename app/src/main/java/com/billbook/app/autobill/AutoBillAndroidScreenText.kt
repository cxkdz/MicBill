package com.billbook.app.autobill

import android.accessibilityservice.AccessibilityServiceInfo
import android.os.Build
import android.view.accessibility.AccessibilityNodeInfo

/** Platform adapter; collection rules are shared with the JVM and device probes. */
object AutoBillAndroidScreenText {
    @JvmStatic
    fun configure(info: AccessibilityServiceInfo): AccessibilityServiceInfo = info.apply {
        flags = flags or AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS or
            AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
            AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
    }

    /** The caller still owns root; the collector releases only acquired children. */
    @JvmStatic
    fun collect(root: AccessibilityNodeInfo): AutoBillScreenTextCollector.Snapshot =
        AutoBillScreenTextCollector.collect(AndroidNode(root))

    private class AndroidNode(private val node: AccessibilityNodeInfo) : AutoBillScreenTextCollector.Node {
        override val visible get() = node.isVisibleToUser
        override val editable get() = node.isEditable
        override val password get() = node.isPassword
        override val text get() = node.text
        override val contentDescription get() = node.contentDescription
        override val stateDescription get() = if (Build.VERSION.SDK_INT >= 30) node.stateDescription else null
        override val paneTitle get() = if (Build.VERSION.SDK_INT >= 28) node.paneTitle else null
        override val childCount get() = node.childCount
        override fun getChild(index: Int) = node.getChild(index)?.let(::AndroidNode)
        override fun release() { node.recycle() }
    }
}
