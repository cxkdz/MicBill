package com.billbook.app.autobill

import org.junit.Assert.*
import org.junit.Test

class AutoBillOcrTextTest {
    private val pkg = AutoBillParser.WECHAT_PKG
    private val detail = "com.tencent.mm.plugin.remittance.ui.RemittanceDetailUI"

    private fun parse(vararg lines: String) = AutoBillScreenParser.parse(pkg, detail,
        AutoBillOcrText.normalize(lines.toList()))

    @Test fun spacedChineseReceiptAndFullWidthMoneyRemainACompletedIncome() {
        val normalized = AutoBillOcrText.normalize(listOf(
            "转 账 详 情", "你 已 收 款 ， 资 金 已 存 入 零 钱 。", "￥１８．００"))
        assertEquals(listOf("转账详情", "你已收款,资金已存入零钱", "¥18.00"), normalized)
        val capture = AutoBillScreenParser.parse(pkg, detail, normalized)!!
        assertEquals(AutoBillParser.TYPE_INCOME, capture.pay.type)
        assertEquals(18.0, capture.pay.amount, 0.001)
    }

    @Test fun wholeReceiptSplitAcrossOcrLinesCanBeReconstructed() {
        val capture = parse("你已收", "款，资金已存入", "零钱", "￥1.00")!!
        assertEquals(AutoBillParser.TYPE_INCOME, capture.pay.type)
        assertEquals(1.0, capture.pay.amount, 0.001)
        assertEquals(listOf("你已收款,资金已存入零钱"),
            AutoBillOcrText.normalize(listOf("你已收款", "资金已存入零钱")))
    }

    @Test fun explicitOutgoingReceiptKeepsThePayersDirection() {
        val capture = parse("对 方 已 收 款", "￥10.00")!!
        assertEquals(AutoBillParser.TYPE_EXPENSE, capture.pay.type)
        assertEquals(listOf("对方已收款"), AutoBillOcrText.normalize(listOf("对方已收", "款")))
    }

    @Test fun missingStatusIsNotRepairedFromSimilarCharactersOrHelpProse() {
        for (status in listOf("你巳收款", "请在对方已收款后查看", "什么是已收款", "待 收 款", "尚 未 收 款")) {
            assertNull(status, parse(status, "￥10.00"))
        }
    }

    @Test fun anIsolatedNegationOrRequestCannotLeaveAnAcceptedStatusBehind() {
        for (prefix in listOf("未", "尚未", "请", "是否", "例如", "如何", "待收款")) {
            assertNull(prefix, parse(prefix, "你 已 收 款", "￥10.00"))
        }
        assertNull(parse("未", "你", "已收款", "￥10.00"))
        assertNull(parse("请", "你已", "收款", "资金己存人零钱", "￥10.00"))
    }

    @Test fun observedModelTypoInWalletExplanationDoesNotDiscardExactPrimaryState() {
        val raw = listOf("转账详信", "你已收款,资金己存入零钱", "¥ 18.00")
        val normalized = AutoBillOcrText.normalize(raw)
        assertEquals("你已收款,资金已存入零钱", normalized[1])
        val capture = AutoBillScreenParser.parse(pkg, detail, normalized)!!
        assertEquals(AutoBillParser.TYPE_INCOME, capture.pay.type)
        assertEquals(18.0, capture.pay.amount, 0.001)
    }

    @Test fun completionNeedsAtLeastOneIndependentlyExactReceiptClause() {
        for (status in listOf("你己收款,资金己存入零钱", "你巳收款,资金已存人零钱",
            "你己收款", "你巳收款", "资金己存入零钱", "你已收款,资金尚未存入零钱", "你已收款,请存入零钱",
            "你已收款,资金己存入零钱请查收")) {
            assertNull(status, parse(status, "¥ 18.00"))
        }
        val capture = parse("你已收款", "资金巳存人零钱通", "¥18.00")!!
        assertEquals(AutoBillParser.TYPE_INCOME, capture.pay.type)
    }

    @Test fun actualReceiptWithUncertainPrimaryKeepsOnlyItsExactDepositEvidence() {
        val raw = listOf("你己收款,资金已存入零钱", "￥1.00", "零钱余额")
        val normalized = AutoBillOcrText.normalize(raw)
        assertEquals("资金已存入零钱", normalized[0])
        val capture = AutoBillScreenParser.parse(pkg, detail, normalized)!!
        assertEquals(AutoBillParser.TYPE_INCOME, capture.pay.type)
        assertEquals(1.0, capture.pay.amount, 0.001)
        assertFalse(capture.pay.originalText.contains("你已收款"))
        assertEquals(listOf("资金已存入零钱"),
            AutoBillOcrText.normalize(listOf("你己收", "款,资金已存入", "零钱")))
        assertNull(parse("未", "你己收", "款,资金已存入零钱", "￥1.00"))
    }

    @Test fun amountAndFieldBoundariesCannotCompleteAStatusFragment() {
        assertNull(parse("你已收", "￥10.00", "款"))
        assertNull(parse("你已收", "转账时间", "款", "￥10.00"))
        assertNull(parse("付款方:你已收款", "￥10.00"))
    }

    @Test fun whitespaceInsideNumbersIsNeverJoinedIntoAnAmount() {
        val normalized = AutoBillOcrText.normalize(listOf("你 已 收 款", "￥1 8.00"))
        assertEquals("¥1 8.00", normalized[1])
        assertNull(AutoBillScreenParser.parse(pkg, detail, normalized))
        assertEquals(listOf("转账金额: 1 8 . 0 0"),
            AutoBillOcrText.normalize(listOf("转 账 金 额 ： 1 8 . 0 0")))
    }

    @Test fun conflictingIncomeAndExpenseEvidenceStillRequiresManualReview() {
        assertNull(parse("对 方 已 收 款", "你 已 收 款", "￥10.00"))
    }
}
