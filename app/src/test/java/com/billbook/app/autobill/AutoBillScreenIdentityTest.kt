package com.billbook.app.autobill

import org.junit.Assert.*
import org.junit.Test

class AutoBillScreenIdentityTest {
    @org.junit.Test fun twoSameAmountActualReceiptPagesInOneWindowRemainSeparate() {
        val pkg = AutoBillParser.WECHAT_PKG
        val activity = "com.tencent.mm.plugin.remittance.ui.RemittanceDetailUI"
        fun received(time: String) = AutoBillScreenParser.parse(pkg, activity,
            listOf("你已收款，资金已存入零钱", "￥1.00", "收款时间", time))!!
        val first = received("2026年11月21日 16:11:03")
        val second = received("2026年11月21日 16:11:20")
        val gate = AutoBillScreenCaptureGate()
        val firstId = AutoBillScreenIdentity.identity(pkg, 100, first)
        val secondId = AutoBillScreenIdentity.identity(pkg, 100, second)
        org.junit.Assert.assertNotEquals(firstId, secondId)
        org.junit.Assert.assertNotNull(gate.begin(firstId))
        org.junit.Assert.assertNotNull(gate.begin(secondId))
        org.junit.Assert.assertNull(gate.begin(firstId))
    }

    @org.junit.Test fun twoEqualRefundsWithDistinctRefundNumbersRemainSeparate() {
        val pkg = AutoBillParser.WECHAT_PKG
        val activity = "com.tencent.mm.plugin.remittance.ui.RemittanceDetailUI"
        fun refund(id: String) = AutoBillScreenParser.parse(pkg, activity,
            listOf("退款已到账", "退款金额：￥10.00", "退款单号：$id"))!!
        val first = refund("R202611211234567801")
        val second = refund("R202611211234567802")
        val firstId = AutoBillScreenIdentity.identity(pkg, 100, first)
        val secondId = AutoBillScreenIdentity.identity(pkg, 100, second)
        org.junit.Assert.assertNotEquals(firstId, secondId)
        org.junit.Assert.assertEquals(firstId, AutoBillScreenIdentity.identity(pkg, 200, first))
    }
    private val pkg = AutoBillParser.WECHAT_PKG
    private val capture = AutoBillScreenParser.Capture(AutoBillParser.ParsedPay(
        18.0, AutoBillParser.TYPE_EXPENSE, "张三", "微信", "微信",
        "转账成功\n金额：18.00 元\n对方：张三\n交易时间：2026-11-21 12:00:01"
    ), "", 1_795_233_601_000L)

    private fun identity(value: AutoBillScreenParser.Capture = capture, session: Long = 100L): String =
        AutoBillScreenIdentity.identity(pkg, session, value)

    @Test fun repeatedSamePageHasStableIdentity() {
        assertEquals(identity(), identity(capture.copy(pay = capture.pay.copy())))
        assertEquals(identity(), identity(capture.copy(pay = capture.pay.copy(
            originalText = "  转账成功 \n金额：18.00   元\n对方：张三\n交易时间：2026-11-21 12:00:01\n"
        ))))
        assertEquals(identity(), identity(capture.copy(pay = capture.pay.copy(
            originalText = capture.pay.originalText.replace("转账成功", "已被接收")
        ))))
    }

    @Test fun equalAmountAtDifferentTimesInSameSessionIsKept() {
        assertNotEquals(identity(), identity(capture.copy(transactionTime = capture.transactionTime!! + 1_000L)))
    }

    @Test fun equalAmountAtDifferentPartiesInSameSessionIsKept() {
        assertNotEquals(identity(), identity(capture.copy(pay = capture.pay.copy(merchant = "李四"))))
    }

    @Test fun structuredResultEvidenceDistinguishesEqualAmountWithoutParsedTime() {
        val first = capture.copy(transactionTime = null)
        val second = first.copy(pay = first.pay.copy(originalText =
            first.pay.originalText.replace("12:00:01", "12:00:22")))
        assertNotEquals(identity(first), identity(second))
    }

    @Test fun noIdWithSameTimeAndPartyInNewSessionRemainsASeparateCandidate() {
        assertNotEquals(identity(session = 100L), identity(session = 101L))
    }

    @Test fun transactionIdIsStableAcrossSessionsAndPageFieldChanges() {
        val strong = capture.copy(transactionId = "  WX202611210000123456  ")
        val reopened = strong.copy(transactionId = "WX202611210000123456", transactionTime = null,
            pay = strong.pay.copy(amount = 20.0, merchant = "new name", originalText = "new layout"))
        assertEquals(identity(strong, 100L), identity(reopened, 200L))
    }

    @Test fun distinctTransactionIdsAndDirectionsStaySeparate() {
        val strong = capture.copy(transactionId = "WX202611210000123456")
        assertNotEquals(identity(strong), identity(strong.copy(transactionId = "WX202611210000123457")))
        assertNotEquals(identity(strong), identity(strong.copy(pay = strong.pay.copy(type = AutoBillParser.TYPE_INCOME))))
    }

    @Test fun originalPaymentAndRefundStaySeparateEvenWithSameOrderId() {
        val strong = capture.copy(transactionId = "WX202611210000123456")
        assertNotEquals(identity(strong), identity(strong.copy(kind = "refund")))
        assertNotEquals(identity(), identity(capture.copy(kind = "refund")))
    }

    @Test fun packagesStaySeparateEvenForTheSameId() {
        val strong = capture.copy(transactionId = "WX202611210000123456")
        assertNotEquals(identity(strong), AutoBillScreenIdentity.identity(AutoBillParser.ALIPAY_PKG, 100L, strong))
    }

    @Test fun repeatedFramesAndReturningToEarlierPageCaptureOnlyOncePerSession() {
        val gate = AutoBillScreenCaptureGate()
        val second = identity(capture.copy(transactionTime = capture.transactionTime!! + 21_000L))
        assertNotNull(gate.begin(identity()))
        assertNull(gate.begin(identity()))
        assertNotNull(gate.begin(second))
        assertNull(gate.begin(identity()))
    }

    @Test fun failedSaveCanRetryAndOldFailureCannotReleaseNewSessionClaim() {
        val gate = AutoBillScreenCaptureGate()
        val first = gate.begin(identity())!!
        gate.failed(first)
        val retry = gate.begin(identity())!!
        gate.failed(first)
        assertNull(gate.begin(identity()))
        gate.failed(retry)
        assertNotNull(gate.begin(identity()))
        gate.clear()
        val current = gate.begin(identity())!!
        gate.failed(first)
        assertNull(gate.begin(identity()))
        gate.failed(current)
        assertNotNull(gate.begin(identity()))
    }
}
