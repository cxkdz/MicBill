package com.billbook.app.autobill

/** Keeps activity evidence separate from events emitted by its child views. */
class AutoBillScreenSession {
    data class Page(val packageName: String, val activity: String, val windowId: Int, val startedAt: Long)

    var page: Page? = null
        private set

    fun observe(
        packageName: String, className: String, windowId: Int,
        isActivity: Boolean, time: Long
    ): Page? {
        if (AutoBillScreenParser.isPaymentActivity(packageName, className) ||
            (isActivity && AutoBillScreenParser.isCandidatePaymentActivity(packageName, className))) {
            if (windowId < 0) {
                page = null
            } else if (page?.packageName != packageName || page?.activity != className || page?.windowId != windowId) {
                page = Page(packageName, className, windowId, time)
            }
        } else if (isActivity) {
            page = null
        }
        // Child view and dialog events never establish or erase an activity identity.
        // The service still verifies the active root package and window before reading.
        return page
    }

    fun matches(packageName: String, windowId: Int): Boolean =
        page?.let { it.packageName == packageName && it.windowId == windowId } == true

    fun clear() { page = null }
}
