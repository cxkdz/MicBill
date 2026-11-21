package com.billbook.app.autobill

import org.junit.Assert.*
import org.junit.Test

class AutoBillPolicyTest {
    private val payment = AutoBillPolicy.Payment(18.0, 0, 1_000_000L, "wechat", "早餐店", "key")

    @Test fun sameAmountAtDifferentMerchantsIsKept() {
        assertEquals(AutoBillPolicy.Match.NONE,
            AutoBillPolicy.compare(payment, payment.copy(merchant = "书店", time = payment.time - 10_000)))
    }

    @Test fun repeatedPaymentIsOnlyFlaggedForConfirmation() {
        assertEquals(AutoBillPolicy.Match.POSSIBLE_DUPLICATE,
            AutoBillPolicy.compare(payment, payment.copy(time = payment.time - 10_000)))
    }

    @Test fun crossChannelBankNoticeNeedsReviewRatherThanBeingDropped() {
        assertEquals(AutoBillPolicy.Match.POSSIBLE_DUPLICATE,
            AutoBillPolicy.compare(payment, payment.copy(packageName = "bank", merchant = "")))
    }

    @Test fun sameNotificationUpdateIsDuplicate() {
        assertEquals(AutoBillPolicy.Match.SAME_NOTIFICATION,
            AutoBillPolicy.compare(payment, payment.copy(merchant = "早餐店旗舰店")))
    }

    @Test fun delayedNotificationsUseTheirOwnTime() {
        assertEquals(AutoBillPolicy.Match.NONE,
            AutoBillPolicy.compare(payment, payment.copy(time = payment.time - 600_000)))
    }

    @Test fun differentDirectionsAreIndependent() {
        assertEquals(AutoBillPolicy.Match.NONE,
            AutoBillPolicy.compare(payment, payment.copy(type = 1, notificationKey = "other")))
    }

    @Test fun correctedDraftDoesNotCreateAnotherBillWhenItsNotificationUpdates() {
        assertEquals(AutoBillPolicy.Match.SAME_NOTIFICATION,
            AutoBillPolicy.compare(payment, payment.copy(amount = 20.0, type = 1)))
    }

    @Test fun allPendingDraftsSurviveHistoryTrimming() {
        val pending = (0 until 150).map { "pending$it" }
        val history = (0 until 120).map { "handled$it" }
        val result = AutoBillPolicy.retainHistory(pending + history, 100) { it.startsWith("pending") }
        assertEquals(250, result.size)
        assertTrue(result.containsAll(pending))
        assertEquals(history.take(100), result.takeLast(100))
    }

    @Test fun rejectInvalidMoneyBeforeSaving() {
        listOf(Double.NaN, Double.POSITIVE_INFINITY, 0.0, -1.0, 1_000_000.0, 0.001)
            .forEach { assertFalse("Invalid amount $it", AutoBillPolicy.validAmount(it)) }
        listOf(0.01, 1.10, 999_999.99).forEach { assertTrue(AutoBillPolicy.validAmount(it)) }
    }
}
