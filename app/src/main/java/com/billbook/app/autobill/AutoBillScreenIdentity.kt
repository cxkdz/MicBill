package com.billbook.app.autobill

/** Screen identities use transaction evidence rather than treating equal amounts as one payment. */
object AutoBillScreenIdentity {
    fun identity(
        packageName: String, sessionStarted: Long, capture: AutoBillScreenParser.Capture
    ): String {
        val pay = capture.pay
        val transactionId = capture.transactionId.trim()
        if (transactionId.isNotEmpty()) {
            return fields("screen-v3", "transaction", packageName, transactionId, capture.kind, pay.type.toString())
        }
        // Without a transaction id, reopening the page remains a separate candidate
        // for human review. Within one session, timestamps, parties and structured
        // result fields distinguish consecutive equal-value transactions.
        val amount = if (pay.amount.isFinite()) java.math.BigDecimal.valueOf(pay.amount)
            .stripTrailingZeros().toPlainString() else pay.amount.toString()
        val original = pay.originalText.lineSequence().map { normalize(it) }
            // Completion labels can change while the same result page settles.
            // Retain structured fields; their parsed amount, time and party are
            // also explicit identity components below.
            .filter { Regex("^[^:：]{1,20}[:：].*").matches(it) }.joinToString("\n")
        return fields(
            "screen-v3", "session", packageName, sessionStarted.toString(),
            capture.kind, pay.type.toString(), amount, capture.transactionTime?.toString().orEmpty(),
            normalize(pay.merchant), original
        )
    }

    private fun normalize(value: String): String = value.trim().replace(Regex("[\\p{Z}\\s]+"), " ")

    /** Length prefixes keep arbitrary field values and delimiters unambiguous. */
    private fun fields(vararg values: String): String = values.joinToString("") { "${it.length}:$it" }
}

/** Remembers all captures in the current activity session, including A → B → A redraws. */
class AutoBillScreenCaptureGate {
    data class Ticket internal constructor(val identity: String, internal val claim: Long)

    private var nextClaim = 0L
    private val captured = mutableMapOf<String, Long>()

    fun begin(identity: String): Ticket? {
        if (captured.containsKey(identity)) return null
        val ticket = Ticket(identity, nextClaim++)
        captured[identity] = ticket.claim
        return ticket
    }

    /** A failed save may retry, but an old session's callback cannot release a new claim. */
    fun failed(ticket: Ticket) {
        if (captured[ticket.identity] == ticket.claim) captured.remove(ticket.identity)
    }

    fun clear() {
        captured.clear()
    }
}
