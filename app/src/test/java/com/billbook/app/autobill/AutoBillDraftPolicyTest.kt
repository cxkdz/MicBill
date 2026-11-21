package com.billbook.app.autobill

import org.junit.Assert.*
import org.junit.Test

class AutoBillDraftPolicyTest {
    private val draft = AutoBillStore.Event(
        id = "original-id", postTime = 1_000_000L,
        packageName = AutoBillParser.WECHAT_PKG,
        title = "家人（示例）", body = "[转账] 已被接收",
        status = AutoBillStore.STATUS_PENDING, note = "待补金额",
        merchant = "家人（示例）", payType = "微信",
        billTime = 1_000_000L, notificationKey = "wechat-conversation-key",
        needsAmount = true
    )
    private val fullPay = AutoBillParser.Outcome.Pay(AutoBillParser.ParsedPay(
        18.0, AutoBillParser.TYPE_EXPENSE, "", "微信", "微信", "支付成功 18元"
    ))

    @Test fun exactNotificationUpdateEnrichesOneDraftAndPreservesIdentity() {
        assertTrue(AutoBillDraftPolicy.sameNotification(
            draft, draft.notificationKey, draft.postTime, draft.packageName))
        val updated = AutoBillDraftPolicy.enrich(draft, fullPay, "微信支付", "支付成功 18元")
        assertEquals(draft.id, updated.id)
        assertEquals(draft.notificationKey, updated.notificationKey)
        assertEquals(draft.postTime, updated.postTime)
        assertEquals(draft.billTime, updated.billTime)
        assertEquals(draft.packageName, updated.packageName)
        assertEquals(AutoBillStore.STATUS_PENDING, updated.status)
        assertEquals(18.0, updated.amount, 0.0)
        assertEquals(draft.merchant, updated.merchant)
        assertFalse(updated.needsAmount)
        assertFalse(updated.needsDirection)
        assertTrue(AutoBillDraftPolicy.canConfirm(updated, null, null))
    }

    @Test fun reusedConversationKeyTwentyOneSecondsLaterIsIndependent() {
        assertFalse(AutoBillDraftPolicy.sameNotification(
            draft, draft.notificationKey, draft.postTime + 21_000L, draft.packageName))
    }

    @Test fun emptyKeyDifferentPackageAndDiagnosticsAreNotSameNotification() {
        assertFalse(AutoBillDraftPolicy.sameNotification(draft.copy(notificationKey = ""), "", draft.postTime, draft.packageName))
        assertFalse(AutoBillDraftPolicy.sameNotification(draft, draft.notificationKey, draft.postTime, AutoBillParser.ALIPAY_PKG))
        assertFalse(AutoBillDraftPolicy.sameNotification(draft.copy(status = AutoBillStore.STATUS_LOG), draft.notificationKey, draft.postTime, draft.packageName))
    }

    @Test fun confirmedAndIgnoredDraftsNeverReopenOrEnrich() {
        for (status in listOf(AutoBillStore.STATUS_CONFIRMED, AutoBillStore.STATUS_IGNORED)) {
            val handled = draft.copy(status = status)
            assertTrue(AutoBillDraftPolicy.sameNotification(handled, draft.notificationKey, draft.postTime, draft.packageName))
            assertEquals(handled, AutoBillDraftPolicy.enrich(handled, fullPay, "new", "new"))
            assertFalse(AutoBillDraftPolicy.canConfirm(handled, 18.0, 0))
        }
    }

    @Test fun completePendingDraftAndSkippedUpdatesArePreserved() {
        val complete = draft.copy(amount = 18.0, needsAmount = false)
        assertEquals(complete, AutoBillDraftPolicy.enrich(complete, fullPay, "new", "new"))
        assertEquals(draft, AutoBillDraftPolicy.enrich(draft, AutoBillParser.Outcome.Skipped("no amount"), "new", "new"))
    }

    @Test fun amountlessHintRetainsMissingAmountAndCannotConfirm() {
        val hint = AutoBillParser.Outcome.NeedsConfirmation(AutoBillParser.TransferHint(
            "", "[转账] 已被接收", type = AutoBillParser.TYPE_EXPENSE
        ))
        val updated = AutoBillDraftPolicy.enrich(draft, hint, draft.title, draft.body)
        assertTrue(updated.needsAmount)
        assertEquals(0.0, updated.amount, 0.0)
        assertEquals(draft.merchant, updated.merchant)
        assertFalse(AutoBillDraftPolicy.canConfirm(updated, null, null))
    }

    @Test fun partialUpdateRetainsAlreadyKnownAmountAndDirection() {
        val amountKnown = draft.copy(amount = 18.0, needsAmount = false, needsDirection = true)
        val hint = AutoBillParser.Outcome.NeedsConfirmation(AutoBillParser.TransferHint(
            "张三", "[转账] 已被接收", type = AutoBillParser.TYPE_EXPENSE
        ))
        val updated = AutoBillDraftPolicy.enrich(amountKnown, hint, "张三", "updated")
        assertEquals(18.0, updated.amount, 0.0)
        assertFalse(updated.needsAmount)
        assertFalse(updated.needsDirection)
        assertEquals("张三", updated.merchant)
        assertTrue(AutoBillDraftPolicy.canConfirm(updated, null, null))
        val stillUnknown = AutoBillDraftPolicy.enrich(amountKnown,
            AutoBillParser.Outcome.NeedsConfirmation(AutoBillParser.TransferHint("", "receipt")), "", "")
        assertTrue(stillUnknown.needsDirection)
        assertEquals(18.0, stillUnknown.amount, 0.0)
        assertEquals(amountKnown.type, stillUnknown.type)
    }

    @Test fun missingAmountOrDirectionRequiresExplicitUserValues() {
        assertFalse(AutoBillDraftPolicy.canConfirm(draft, null, null))
        assertTrue(AutoBillDraftPolicy.canConfirm(draft, 18.0, null))
        val unknownDirection = draft.copy(amount = 18.0, needsAmount = false, needsDirection = true)
        assertFalse(AutoBillDraftPolicy.canConfirm(unknownDirection, null, null))
        assertFalse(AutoBillDraftPolicy.canConfirm(unknownDirection, 18.0, null))
        assertTrue(AutoBillDraftPolicy.canConfirm(unknownDirection, null, AutoBillParser.TYPE_INCOME))
        val bothMissing = draft.copy(needsDirection = true)
        assertFalse(AutoBillDraftPolicy.canConfirm(bothMissing, 18.0, null))
        assertFalse(AutoBillDraftPolicy.canConfirm(bothMissing, null, 1))
        assertTrue(AutoBillDraftPolicy.canConfirm(bothMissing, 18.0, 1))
    }

    @Test fun explicitValuesStillRequireValidMoneyAndDirection() {
        for (amount in listOf(Double.NaN, Double.POSITIVE_INFINITY, 0.0, -1.0, 0.001, 1_000_000.0)) {
            assertFalse(AutoBillDraftPolicy.canConfirm(draft, amount, 0))
        }
        assertFalse(AutoBillDraftPolicy.canConfirm(draft, 18.0, -1))
        assertFalse(AutoBillDraftPolicy.canConfirm(draft, 18.0, 2))
    }
}
