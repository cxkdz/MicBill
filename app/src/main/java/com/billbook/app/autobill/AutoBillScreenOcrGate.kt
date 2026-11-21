package com.billbook.app.autobill

/** Limits costly image reads and invalidates callbacks when a payment window changes. */
class AutoBillScreenOcrGate {
    data class Ticket internal constructor(val claim: Long)

    private var nextClaim = 0L
    private var current: Ticket? = null
    private var attempts = 0
    private var lastStarted: Long? = null

    fun begin(now: Long): Ticket? {
        if (current != null || attempts >= 2) return null
        if (lastStarted?.let { now - it < 2_500L } == true) return null
        return Ticket(nextClaim++).also {
            current = it
            attempts++
            lastStarted = now
        }
    }

    fun isCurrent(ticket: Ticket): Boolean = current == ticket

    fun finish(ticket: Ticket): Boolean {
        if (!isCurrent(ticket)) return false
        current = null
        return true
    }

    fun clear() {
        current = null
        attempts = 0
        lastStarted = null
    }
}
