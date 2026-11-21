package com.billbook.app.autobill

import org.junit.Assert.*
import org.junit.Test

class AutoBillOcrReceiptTest {
    private val pkg = AutoBillParser.WECHAT_PKG
    private val detail = "com.tencent.mm.plugin.remittance.ui.RemittanceDetailUI"
    private val fields = listOf("转账时间", "2026年11月21日 16:09:39",
        "收款时间", "2026年11月21日 16:11:03")

    @Test fun actualWechatImageWithOmittedCurrencyAndAnExactDepositClauseIsIncome() {
        val read = listOf("16:15 G OO", "VPN4S1的会66% O",
            "你己收款,资金已存入零钱", "1.00", "零钱余额") + fields +
            listOf("零钱通 七日年化090%", "转入", "给小金库再添一笔", "账单译情")
        val capture = AutoBillScreenParser.inspectOcr(pkg, detail, read).capture!!
        assertEquals(AutoBillParser.TYPE_INCOME, capture.pay.type)
        assertEquals(1.0, capture.pay.amount, 0.001)
        assertEquals("local_window_ocr", capture.captureMethod)
        assertFalse(capture.pay.originalText.contains("你已收款"))
        // The more permissive image layout rules must not alter node parsing.
        assertNull(AutoBillScreenParser.inspect(pkg, detail, read).capture)
    }

    @Test fun currencylessValueRequiresReceiptFieldsAndImmediateCompletedStatus() {
        for (read in listOf(
            listOf("你已收款", "1.00"),
            listOf("你已收款", "1.00", "收款时间"),
            listOf("你已收款", "零钱余额", "100.00") + fields,
            listOf("你已收款", "1 0.00") + fields,
            listOf("你己收款", "1.00") + fields,
            listOf("你已收款", "付款方", "张三", "1.00") + fields
        )) {
            assertNull(read.toString(), AutoBillScreenParser.inspectOcr(pkg, detail, read).capture)
        }
    }

    @Test fun balanceIsNotChosenWhenCurrencyIsMissingFromTheTransaction() {
        val capture = AutoBillScreenParser.inspectOcr(pkg, detail,
            listOf("你已收款", "1.00", "零钱余额", "￥100.00") + fields).capture!!
        assertEquals(1.0, capture.pay.amount, 0.001)
    }

    @Test fun pendingAndConflictingAmountsCannotBecomeCompletedOcrPayments() {
        for (read in listOf(
            listOf("待收款", "1.00") + fields,
            listOf("待收款", "你已收款", "￥1.00") + fields,
            listOf("你已收款", "1.00", "￥2.00") + fields,
            listOf("你已收款", "1.00", "你已收款", "2.00") + fields
        )) {
            assertNull(read.toString(), AutoBillScreenParser.inspectOcr(pkg, detail, read).capture)
        }
    }

    @Test fun conflictingCurrencylessAmountsCannotBeOverriddenByACurrencyAmount() {
        val read = listOf("你已收款", "1.00", "你已收款", "2.00", "￥3.00") + fields
        val result = AutoBillScreenParser.inspectOcr(pkg, detail, read)
        assertNull(result.capture)
        assertEquals("本机识图读到相互冲突的交易金额，请手动核对", result.reason)
    }
}
