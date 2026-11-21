package com.billbook.app.autobill

/**
 * A bounded series of page reads for one payment session.
 *
 * The service owns its Handler callbacks. It calls request() for page/content
 * events, retry() after an unsuccessful read, and succeeded() after a capture.
 * Failed series stay exhausted until reset() or an explicit semantic change.
 */
class AutoBillScreenSampling {
    private enum class State { READY, ACTIVE, EXHAUSTED }

    private var state = State.READY
    private var attempt = 0
    private val delays = longArrayOf(350L, 500L, 900L, 1_400L, 2_000L)

    /** Ordinary redraws cannot rearm exhaustion; a later changed status can. */
    fun request(contentChanged: Boolean = false): Long? {
        if (state == State.EXHAUSTED && contentChanged) state = State.READY
        if (state != State.READY) return null
        state = State.ACTIVE
        attempt = 0
        return delays[attempt]
    }

    /** Returns the next delay after a failed read; content events never reset it. */
    fun retry(): Long? {
        if (state != State.ACTIVE) return null
        attempt++
        if (attempt >= delays.size) {
            state = State.EXHAUSTED
            return null
        }
        return delays[attempt]
    }

    /** Stops retries and lets a later content event inspect a subsequent payment. */
    fun succeeded() {
        if (state != State.ACTIVE) return
        state = State.READY
        attempt = 0
    }

    /** Call when entering a new payment activity/window session or clearing it. */
    fun reset() {
        state = State.READY
        attempt = 0
    }
}
