package com.billbook.app.autobill

import kotlin.math.abs
import kotlin.math.roundToLong

/** Pure rules shared by notification capture and draft confirmation. */
object AutoBillPolicy {
    const val CROSS_CHANNEL_WINDOW_MS = 2 * 60 * 1000L
    private const val SAME_CHANNEL_WINDOW_MS = 60 * 1000L

    data class Payment(
        val amount: Double,
        val type: Int,
        val time: Long,
        val packageName: String,
        val merchant: String,
        val notificationKey: String = ""
    )

    enum class Match { NONE, SAME_NOTIFICATION, POSSIBLE_DUPLICATE }

    fun validAmount(amount: Double): Boolean = amount.isFinite() && amount > 0.0 &&
        amount < 1_000_000.0 && abs(amount * 100 - (amount * 100).roundToLong()) < 0.000001

    fun compare(current: Payment, previous: Payment): Match {
        if (current.notificationKey.isNotEmpty() &&
            current.notificationKey == previous.notificationKey &&
            current.packageName == previous.packageName && current.time == previous.time
        ) return Match.SAME_NOTIFICATION

        if (!validAmount(current.amount) || !validAmount(previous.amount) ||
            current.type != previous.type ||
            (current.amount * 100).roundToLong() != (previous.amount * 100).roundToLong()
        ) return Match.NONE

        val distance = abs(current.time - previous.time)
        if (distance > CROSS_CHANNEL_WINDOW_MS) return Match.NONE
        // An equal amount is only a reason to ask the user, never proof of a duplicate.
        if (current.packageName != previous.packageName) return Match.POSSIBLE_DUPLICATE
        if (distance <= SAME_CHANNEL_WINDOW_MS &&
            (current.merchant.isBlank() || previous.merchant.isBlank() ||
                current.merchant.trim() == previous.merchant.trim())
        ) return Match.POSSIBLE_DUPLICATE
        return Match.NONE
    }

    /** Pending drafts are retained even when there are more than the history limit. */
    fun <T> retainHistory(items: List<T>, handledLimit: Int, isPending: (T) -> Boolean): List<T> {
        var handled = 0
        return items.filter { isPending(it) || handled++ < handledLimit }
    }
}
