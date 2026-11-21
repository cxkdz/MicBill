package com.billbook.app.autobill

import org.junit.Assert.*
import org.junit.Test

class AutoBillScreenParserTest {
    private val pkg = AutoBillParser.WECHAT_PKG
    private val result = "com.tencent.mm.plugin.remittance.ui.RemittanceResultNewUI"
    private val detail = "com.tencent.mm.plugin.remittance.ui.RemittanceDetailUI"

    @Test fun actualWechatReceiptSentenceAndOneYuanScreenshotAreIncome() {
        val capture = AutoBillScreenParser.parse(pkg, detail, listOf(
            "你已收款，资金已存入零钱", "￥1.00", "零钱余额",
            "转账时间", "2026年11月21日 16:09:39", "收款时间", "2026年11月21日 16:11:03",
            "零钱通 七日年化0.90%", "给小金库再添一笔", "转入", "账单详情"
        ))!!
        assertEquals(AutoBillParser.TYPE_INCOME, capture.pay.type)
        assertEquals(1.0, capture.pay.amount, 0.001)
        assertFalse(capture.needsDirection)
        val expected = java.text.SimpleDateFormat("yyyy年MM月dd日 HH:mm:ss", java.util.Locale.CHINA)
            .parse("2026年11月21日 16:11:03")!!.time
        assertEquals(expected, capture.transactionTime)
    }

    @Test fun actualReceiptSentenceAlsoWorksWithSeparateCurrencyNodes() {
        val capture = AutoBillScreenParser.parse(pkg, detail,
            listOf("你已收款，资金已存入零钱", "￥", "1.00", "零钱余额"))!!
        assertEquals(AutoBillParser.TYPE_INCOME, capture.pay.type)
        assertEquals(1.0, capture.pay.amount, 0.001)
    }

    @Test fun completedRefundIsRetainedForReviewInsteadOfBeingFilteredOut() {
        val capture = AutoBillScreenParser.parse(pkg, detail, listOf(
            "退款已到账", "退款金额：￥10.00", "退款单号：R202611211234567890", "退款时间：2026-11-21 16:10:00"
        ))!!
        assertEquals("refund", capture.kind)
        assertEquals(AutoBillParser.TYPE_INCOME, capture.pay.type)
        assertFalse(capture.needsDirection)
        assertEquals("R202611211234567890", capture.transactionId)
    }

    @Test fun returnedTransferWithUnclearPerspectiveRequiresDirection() {
        val capture = AutoBillScreenParser.parse(pkg, detail,
            listOf("转账已退回", "退款金额：￥10.00", "转账单号：1000050001202611211234567890"))!!
        assertEquals("refund", capture.kind)
        assertTrue(capture.needsDirection)
        // The original transfer's id is not the identity of a separate refund.
        assertEquals("", capture.transactionId)
    }

    @Test fun unfinishedRefundDoesNotBecomeAnIncomingPayment() {
        for (status in listOf("退款处理中", "退款失败", "退款未到账", "退款申请中")) {
            assertNull(status, AutoBillScreenParser.parse(pkg, detail,
                listOf(status, "退款金额：￥10.00")))
        }
    }

    @Test fun diagnosticsSeparateUnreadableAmountFromMissingCompletion() {
        val statusMissing = AutoBillScreenParser.inspect(pkg, detail, listOf("￥1.00"))
        assertNull(statusMissing.capture)
        assertTrue(statusMissing.reason.contains("完成状态"))
        val amountMissing = AutoBillScreenParser.inspect(pkg, detail, listOf("你已收款，资金已存入零钱"))
        assertNull(amountMissing.capture)
        assertTrue(amountMissing.reason.contains("金额"))
    }

    @Test fun oldReceiptSuccessDoesNotProveTheDirectionOfARefund() {
        val capture = AutoBillScreenParser.parse(pkg, detail,
            listOf("收款成功", "退款成功", "退款金额：￥10.00"))!!
        assertTrue(capture.needsDirection)
        val explicit = AutoBillScreenParser.parse(pkg, detail,
            listOf("退款成功", "退款已到账", "退款金额：￥10.00"))!!
        assertFalse(explicit.needsDirection)
    }

    @Test fun partialRefundUsesRefundAmountInsteadOfTheOriginalPayment() {
        val capture = AutoBillScreenParser.parse(pkg, detail,
            listOf("退款已到账", "支付金额：100元", "退款金额：20元"))!!
        assertEquals(20.0, capture.pay.amount, 0.001)
        assertNull(AutoBillScreenParser.parse(pkg, detail,
            listOf("退款已到账", "支付金额：100元")))
        assertNull(AutoBillScreenParser.parse(pkg, detail,
            listOf("退款已到账", "退款金额：20元", "退款金额：30元")))
    }

    @Test fun chineseDateNodesWithoutASpacePreserveActualReceiptTime() {
        val capture = AutoBillScreenParser.parse(pkg, detail,
            listOf("你已收款，资金已存入零钱", "￥1.00", "收款时间：2026年11月21日16:11:03"))!!
        val expected = java.text.SimpleDateFormat("yyyy年MM月dd日 HH:mm:ss", java.util.Locale.CHINA)
            .parse("2026年11月21日 16:11:03")!!.time
        assertEquals(expected, capture.transactionTime)
    }

    @Test fun refundStatusIsNeverUsedAsAMerchantName() {
        val capture = AutoBillScreenParser.parse(pkg, detail,
            listOf("退款已到账", "退款金额：￥10.00", "付款方", "已退款"))!!
        assertEquals("", capture.pay.merchant)
    }

    @Test fun completedTransferToSomeoneElseIsExpense() {
        val capture = AutoBillScreenParser.parse(pkg, result,
            listOf("转账成功", "￥18.00", "收款方", "张三", "待对方确认收款"))!!
        assertEquals(0, capture.pay.type)
        assertEquals(18.0, capture.pay.amount, 0.001)
        assertEquals("张三", capture.pay.merchant)
    }

    @Test fun collectedTransferIsIncome() {
        val capture = AutoBillScreenParser.parse(pkg, detail,
            listOf("已收款", "￥0.01", "已存入零钱", "付款方：李四"))!!
        assertEquals(1, capture.pay.type)
        assertEquals(0.01, capture.pay.amount, 0.001)
    }

    @Test fun completedChatCardIsNotAResultPage() {
        assertNull(AutoBillScreenParser.parse(pkg, "com.tencent.mm.ui.chatting.ChattingUI",
            listOf("已收款", "￥10.00")))
        assertNull(AutoBillScreenParser.parse(pkg, "com.tencent.mm.ui.LauncherUI",
            listOf("转账成功", "￥10.00")))
    }

    @Test fun transferEntryAndUncollectedRequestsAreSkipped() {
        assertNull(AutoBillScreenParser.parse(pkg, detail, listOf("待收款", "￥10.00", "确认收款")))
        assertNull(AutoBillScreenParser.parse(pkg, "com.tencent.mm.plugin.remittance.ui.RemittanceUI",
            listOf("转账", "10.00", "转账成功")))
    }

    @Test fun failedOrReturnedPaymentsAreSkipped() {
        for (status in listOf("支付失败", "已退回", "退款成功", "处理中")) {
            assertNull(AutoBillScreenParser.parse(pkg, result, listOf("转账成功", status, "18.00")))
        }
    }

    @Test fun conflictingDirectionsAreSkipped() {
        assertNull(AutoBillScreenParser.parse(pkg, detail, listOf("已收款", "转账成功", "10.00")))
    }

    @Test fun multipleUnlabelledAmountsAreSkipped() {
        assertNull(AutoBillScreenParser.parse(pkg, result, listOf("转账成功", "10.00", "100.00")))
    }

    @Test fun explicitTransactionAmountCanExcludeBalance() {
        val capture = AutoBillScreenParser.parse(pkg, result,
            listOf("转账成功", "转账金额：￥1,234.56", "余额", "100.00"))!!
        assertEquals(1234.56, capture.pay.amount, 0.001)
    }

    @Test fun transactionIdentifierIsKeptForDeduplication() {
        val capture = AutoBillScreenParser.parse(pkg, detail,
            listOf("已收款", "￥10.00", "转账单号", "1000050001202611211234567890"))!!
        assertEquals("1000050001202611211234567890", capture.transactionId)
    }

    @Test fun otherAppsCannotSupplyPaymentPages() {
        assertNull(AutoBillScreenParser.parse("com.example.other", result, listOf("转账成功", "10.00")))
    }

    @Test fun originalRecordContainsOnlyPaymentFields() {
        val capture = AutoBillScreenParser.parse(pkg, result,
            listOf("转账成功", "￥18.00", " unrelated text not saved "))!!
        assertFalse(capture.pay.originalText.contains("unrelated"))
    }

    @Test fun senderViewingCounterpartyCollectedStillExpense() {
        val capture = AutoBillScreenParser.parse(pkg, detail, listOf("对方已收款", "￥10.00"))!!
        assertEquals(0, capture.pay.type)
    }

    @Test fun sharedRemittanceActivityCapturesCompletedSenderTransfer() {
        val activity = "com.tencent.mm.plugin.remittance.ui.RemittanceUI"
        for (status in listOf("已被接收", "对方已收款", "对方已收钱")) {
            val capture = AutoBillScreenParser.parse(pkg, activity,
                listOf("转账", status, "￥", "0.01", "转账时间：2026-11-21 16:34:00"))!!
            assertEquals(AutoBillParser.TYPE_EXPENSE, capture.pay.type)
            assertEquals(0.01, capture.pay.amount, 0.001)
            assertFalse(capture.needsDirection)
            assertNotNull(capture.transactionTime)
        }
    }

    @Test fun sharedRemittanceActivityNeedsAcceptedStateAndAnUnambiguousAmount() {
        val activity = "com.tencent.mm.plugin.remittance.ui.RemittanceUI"
        for (lines in listOf(
            listOf("转账", "￥10.00", "确认转账"),
            listOf("转账成功", "￥10.00"),
            listOf("待收款", "已被接收", "￥10.00"),
            listOf("对方已收款", "确认转账", "￥10.00"),
            listOf("对方已收款", "已退还", "￥10.00"),
            listOf("已收款", "￥10.00"),
            listOf("对方已收款", "零钱余额", "￥10.00"),
            listOf("对方已收款", "￥10.00", "￥20.00")
        )) {
            assertNull(lines.toString(), AutoBillScreenParser.parse(pkg, activity, lines))
        }
    }

    @Test fun transferAcceptedResultWithAmountIsExpense() {
        val capture = AutoBillScreenParser.parse(pkg, detail, listOf("已被接收", "￥10.00"))!!
        assertEquals(0, capture.pay.type)
        assertEquals(10.0, capture.pay.amount, 0.001)
        assertNull(AutoBillScreenParser.parse(pkg, detail, listOf("已被接收")))
    }

    @Test fun bareNumbersWithoutCurrencyOrAmountLabelAreNotMoney() {
        assertNull(AutoBillScreenParser.parse(pkg, result, listOf("转账成功", "20261121")))
        assertNull(AutoBillScreenParser.parse(pkg, result, listOf("转账成功", "18.00")))
        assertNull(AutoBillScreenParser.parse(pkg, result, listOf("转账成功", "收款人数", "10")))
    }

    @Test fun separatedCurrencyAndValueAreRecognized() {
        for (amountNodes in listOf(listOf("￥", "18.00"), listOf("18.00", "元"))) {
            val capture = AutoBillScreenParser.parse(pkg, result, listOf("转账成功") + amountNodes)!!
            assertEquals(18.0, capture.pay.amount, 0.001)
        }
    }

    @Test fun separateAmountLabelUsesItsOwnValueAndExcludesBalance() {
        val capture = AutoBillScreenParser.parse(pkg, result,
            listOf("转账成功", "余额", "￥1,000.00", "转账金额", "18.00"))!!
        assertEquals(18.0, capture.pay.amount, 0.001)
    }

    @Test fun separateAmountLabelWithCurrencyNodeIsRecognized() {
        val capture = AutoBillScreenParser.parse(pkg, result,
            listOf("转账成功", "转账金额：", "￥", "18.00", "元"))!!
        assertEquals(18.0, capture.pay.amount, 0.001)
        val inlineSymbol = AutoBillScreenParser.parse(pkg, result,
            listOf("转账成功", "转账金额：￥", "18.00"))!!
        assertEquals(18.0, inlineSymbol.pay.amount, 0.001)
    }

    @Test fun labelledInvalidOrConflictingAmountsCannotFallBackToBalance() {
        assertNull(AutoBillScreenParser.parse(pkg, result,
            listOf("转账成功", "金额：12.345", "余额", "￥100.00")))
        assertNull(AutoBillScreenParser.parse(pkg, result,
            listOf("转账成功", "转账金额", "10.00", "转账金额", "20.00")))
    }

    @Test fun unrelatedBalanceAndCountAreNotTheTransactionAmount() {
        assertNull(AutoBillScreenParser.parse(pkg, result,
            listOf("转账成功", "余额", "￥", "100.00", "联系人数量", "10")))
        val capture = AutoBillScreenParser.parse(pkg, result,
            listOf("转账成功", "￥18.00", "余额", "￥100.00", "10"))!!
        assertEquals(18.0, capture.pay.amount, 0.001)
    }

    @Test fun combinedCollectedAndDepositStatusIsIncome() {
        val capture = AutoBillScreenParser.parse(pkg, detail,
            listOf("已收款，已存入零钱", "￥0.01"))!!
        assertEquals(AutoBillParser.TYPE_INCOME, capture.pay.type)
        assertNull(AutoBillScreenParser.parse(pkg, detail,
            listOf("转账成功 已收款", "￥0.01")))
    }

    @Test fun cancelledOrIncompleteStatusOverridesSuccess() {
        for (status in listOf("交易已取消", "支付未完成", "交易已关闭", "转账失败，请稍后重试")) {
            assertNull(AutoBillScreenParser.parse(pkg, result, listOf("转账成功", status, "￥18.00")))
        }
    }

    @Test fun merchantAndHelpTextContainingFailureWordsDoNotChangeStatus() {
        val capture = AutoBillScreenParser.parse(pkg, result,
            listOf("转账成功", "￥18.00", "收款方", "失败也要加油便利店", "查看退款帮助"))!!
        assertEquals(18.0, capture.pay.amount, 0.001)
    }

    @Test fun packageAndActivityMustBothBeAllowed() {
        assertFalse(AutoBillScreenParser.isPaymentActivity(pkg,
            "com.example.fake.RemittanceDetailUI"))
        assertFalse(AutoBillScreenParser.isPaymentActivity(AutoBillParser.ALIPAY_PKG, detail))
        assertTrue(AutoBillScreenParser.isPaymentActivity(pkg, detail))
    }

    @Test fun historicalLabelledTransactionTimeIsPreserved() {
        val capture = AutoBillScreenParser.parse(pkg, detail,
            listOf("已收款", "￥18.00", "转账时间", "2025-03-08 12:34:56"))!!
        val expected = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.CHINA)
            .parse("2025-03-08 12:34:56")!!.time
        assertEquals(expected, capture.transactionTime)
    }

    @Test fun supportedFullTimeFormatsAreParsedStrictly() {
        val inputs = listOf(
            "2025-03-08 12:34" to "yyyy-MM-dd HH:mm",
            "2025/03/08 12:34:56" to "yyyy/MM/dd HH:mm:ss",
            "2025/03/08 12:34" to "yyyy/MM/dd HH:mm",
            "2025年03月08日 12:34:56" to "yyyy年MM月dd日 HH:mm:ss",
            "2025年03月08日 12:34" to "yyyy年MM月dd日 HH:mm",
            "2025-03-08" to "yyyy-MM-dd"
        )
        for ((text, pattern) in inputs) {
            val capture = AutoBillScreenParser.parse(pkg, detail,
                listOf("已收款", "￥18.00", "交易时间：$text"))!!
            val expected = java.text.SimpleDateFormat(pattern, java.util.Locale.CHINA).parse(text)!!.time
            assertEquals(text, expected, capture.transactionTime)
        }
    }

    @Test fun malformedOrPartialTransactionTimeIsNotCoerced() {
        for (time in listOf("2025-02-30 12:34:56", "2025-03-08 25:00:00",
            "2025-03-08 12:34:56 extra", "20250308123456", "11月21日", "2025-3-8 12:34")) {
            val capture = AutoBillScreenParser.parse(pkg, detail,
                listOf("已收款", "￥18.00", "付款时间：$time"))!!
            assertNull("非法日期 $time 不应变成交易时间", capture.transactionTime)
        }
    }

    @Test fun datesAndNumericIdentifiersWithoutTimeLabelAreNotTransactionTime() {
        val capture = AutoBillScreenParser.parse(pkg, detail,
            listOf("已收款", "￥18.00", "2025-03-08 12:34:56", "订单号：20250308123456"))!!
        assertNull(capture.transactionTime)
        assertEquals("20250308123456", capture.transactionId)
    }

    @Test fun receiptDateTakesPriorityOverTheSendersEarlierTransferDate() {
        val capture = AutoBillScreenParser.parse(pkg, detail,
            listOf("已收款", "￥18.00", "转账时间：2025-03-08 12:34:56", "收款时间：2025-03-09 08:30:00"))!!
        val expected = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.CHINA)
            .parse("2025-03-09 08:30:00")!!.time
        assertEquals(expected, capture.transactionTime)
    }

    @Test fun completedWechatBalanceIncomeWithSeparateStatusNodesIsRecognized() {
        val capture = AutoBillScreenParser.parse(pkg, detail,
            listOf("已收款", "￥0.01", "已存入零钱", "到账方式", "微信零钱", "付款方：李四"))!!
        assertEquals(AutoBillParser.TYPE_INCOME, capture.pay.type)
        assertEquals(0.01, capture.pay.amount, 0.001)
        assertEquals("微信", capture.pay.payType)
    }

    @Test fun spaceSeparatedCollectedAndDepositStatusesAreRecognized() {
        for (status in listOf("已收款 已存入零钱", "已收款\t已存入零钱通")) {
            val capture = AutoBillScreenParser.parse(pkg, detail, listOf(status, "￥0.01"))!!
            assertEquals(AutoBillParser.TYPE_INCOME, capture.pay.type)
        }
    }

    @Test fun outgoingWechatBalancePaymentKeepsExpenseDirection() {
        val capture = AutoBillScreenParser.parse(pkg, result,
            listOf("转账成功", "转账金额：10.00元", "支付方式", "微信零钱", "收款方：张三"))!!
        assertEquals(AutoBillParser.TYPE_EXPENSE, capture.pay.type)
        assertEquals("微信", capture.pay.payType)
    }

    @Test fun paymentMethodAndWalletBalanceNodesCannotBecomeTransactionMoney() {
        for (label in listOf("微信零钱", "零钱", "微信零钱余额", "零钱通余额")) {
            val capture = AutoBillScreenParser.parse(pkg, detail,
                listOf("已收款", "￥0.01", label, "￥1,000.00"))!!
            assertEquals(0.01, capture.pay.amount, 0.001)
            assertNull(AutoBillScreenParser.parse(pkg, detail,
                listOf("已收款", label, "￥1,000.00")))
        }
    }

    @Test fun depositStatusWithWalletValueNeedsIndependentTransactionAmount() {
        for (status in listOf("已存入零钱(¥100.00)", "已存入零钱通（￥100.00）")) {
            val capture = AutoBillScreenParser.parse(pkg, detail,
                listOf(status, "收款金额：￥0.01"))!!
            assertEquals(AutoBillParser.TYPE_INCOME, capture.pay.type)
            assertEquals(0.01, capture.pay.amount, 0.001)
            assertNull(AutoBillScreenParser.parse(pkg, detail, listOf(status)))
        }
    }

    @Test fun transferAlreadyAcceptedStatusIsExpenseOnAllowedDetailPage() {
        val capture = AutoBillScreenParser.parse(pkg, detail,
            listOf("转账已被接收", "转账金额：￥10.00", "支付方式：零钱"))!!
        assertEquals(AutoBillParser.TYPE_EXPENSE, capture.pay.type)
    }

    @Test fun walletHomeBalanceWithoutCompletedTransactionIsNeverIncome() {
        assertNull(AutoBillScreenParser.parse(pkg, "com.tencent.mm.ui.LauncherUI",
            listOf("微信零钱", "￥1,000.00", "已存入零钱")))
        assertNull(AutoBillScreenParser.parse(pkg, detail,
            listOf("微信零钱", "￥1,000.00", "余额")))
        assertNull(AutoBillScreenParser.parse(pkg, detail,
            listOf("已收款", "微信零钱", "￥1,000.00")))
    }

    // Synthetic names exercise version compatibility; none is claimed as a
    // class observed on a user's device. Module prefixes are the boundary.
    private val syntheticResult = "com.tencent.mm.plugin.remittance.ui.SyntheticTerminalUI"
    private val syntheticWalletResult = "com.tencent.mm.plugin.wallet_core.ui.SyntheticTerminalUI"

    @Test fun unknownPaymentModuleClassRequiresStructuredEvidence() {
        assertFalse(AutoBillScreenParser.isPaymentActivity(pkg, syntheticResult))
        assertTrue(AutoBillScreenParser.isCandidatePaymentActivity(pkg, syntheticResult))
        assertNull(AutoBillScreenParser.parse(pkg, syntheticResult, listOf("转账成功", "￥10.00")))
        val capture = AutoBillScreenParser.parse(pkg, syntheticResult,
            listOf("转账成功", "￥10.00", "收款方：张三"))!!
        assertEquals(AutoBillParser.TYPE_EXPENSE, capture.pay.type)
    }

    @Test fun unknownPaymentModuleCanUseValidTransactionId() {
        val capture = AutoBillScreenParser.parse(pkg, syntheticResult,
            listOf("转账详情", "对方已收款", "￥10.00", "交易单号：1000050001202611211234567890"))!!
        assertEquals(AutoBillParser.TYPE_EXPENSE, capture.pay.type)
        assertEquals("1000050001202611211234567890", capture.transactionId)
        assertNull(AutoBillScreenParser.parse(pkg, syntheticResult,
            listOf("转账详情", "对方已收款", "￥10.00", "交易单号：123")))
    }

    @Test fun unknownWalletModuleCanUseStrictLabelledTimestamp() {
        assertTrue(AutoBillScreenParser.isCandidatePaymentActivity(pkg, syntheticWalletResult))
        val capture = AutoBillScreenParser.parse(pkg, syntheticWalletResult,
            listOf("已收款", "￥10.00", "收款时间：2025-03-08 12:34:56"))!!
        assertEquals(AutoBillParser.TYPE_INCOME, capture.pay.type)
        assertNotNull(capture.transactionTime)
        assertNull(AutoBillScreenParser.parse(pkg, syntheticWalletResult,
            listOf("已收款", "￥10.00", "收款时间：2025-02-30 12:34:56")))
    }

    @Test fun unknownPageCounterpartyNeedsExactResultHeading() {
        assertNull(AutoBillScreenParser.parse(pkg, syntheticResult,
            listOf("对方已收款", "￥10.00", "收款方：张三")))
        assertNotNull(AutoBillScreenParser.parse(pkg, syntheticResult,
            listOf("转账详情", "对方已收款", "￥10.00", "收款方：张三")))
        assertNull(AutoBillScreenParser.parse(pkg, syntheticResult,
            listOf("请查看转账详情", "对方已收款", "￥10.00", "收款方：张三")))
    }

    @Test fun unknownEntryRequestFailedOrConflictingPagesAreNotCaptured() {
        val fields = listOf("￥10.00", "收款方：张三", "交易单号：1000050001202611211234567890")
        for (status in listOf("转账", "待收款", "确认收款", "请付款", "转账失败")) {
            assertNull(AutoBillScreenParser.parse(pkg, syntheticResult, listOf(status) + fields))
        }
        assertNull(AutoBillScreenParser.parse(pkg, syntheticResult,
            listOf("转账成功", "已收款") + fields))
        assertNull(AutoBillScreenParser.parse(pkg, syntheticResult,
            listOf("转账成功", "支付失败") + fields))
        assertNull(AutoBillScreenParser.parse(pkg, syntheticResult,
            listOf("转账成功", "￥20.00") + fields))
    }

    @Test fun historyAndAccountClassesRemainOutsideCompatibilityCandidates() {
        for (name in listOf("SyntheticHistoryUI", "SyntheticHomeUI", "SyntheticListUI",
            "SyntheticBalanceUI", "SyntheticInputUI", "SyntheticRequestUI")) {
            val className = "com.tencent.mm.plugin.remittance.ui.$name"
            assertFalse(name, AutoBillScreenParser.isCandidatePaymentActivity(pkg, className))
            assertNull(AutoBillScreenParser.parse(pkg, className,
                listOf("转账成功", "￥10.00", "收款方：张三")))
        }
    }

    @Test fun unknownAccountOrHistoryHeadingCannotReuseCompletedPaymentWords() {
        for (heading in listOf("零钱", "微信零钱", "交易记录", "转账记录", "账单详情")) {
            assertNull(AutoBillScreenParser.parse(pkg, syntheticWalletResult,
                listOf(heading, "已收款", "￥10.00", "付款方：李四", "交易单号：1000050001202611211234567890")))
        }
    }

    @Test fun compatibilityCandidatesStayWithinVerifiedModulesAndPackage() {
        for (className in listOf(
            "com.tencent.mm.plugin.remittance.other.SyntheticTerminalUI",
            "com.tencent.mm.plugin.remittance.uiother.SyntheticTerminalUI",
            "com.tencent.mm.plugin.wallet.ui.SyntheticTerminalUI",
            "com.tencent.mm.plugin.remittance.ui.SyntheticTerminalUI\$Nested",
            "com.tencent.mm.plugin.remittance.ui.nested.SyntheticTerminalUI",
            "com.tencent.mm.ui.chatting.ChattingUI", "com.tencent.mm.ui.LauncherUI"
        )) {
            assertFalse(className, AutoBillScreenParser.isCandidatePaymentActivity(pkg, className))
        }
        assertFalse(AutoBillScreenParser.isCandidatePaymentActivity("com.example.other", syntheticResult))
        assertFalse(AutoBillScreenParser.isCandidatePaymentActivity(AutoBillParser.ALIPAY_PKG, syntheticResult))
        assertTrue(AutoBillScreenParser.isCandidatePaymentActivity(pkg, detail))
    }

    @Test fun missingCounterpartyCannotConsumeNextFieldLabelOrResultHeading() {
        for (nextLabel in listOf("支付方式", "转账金额", "交易时间：", "收款方", "转账详情", "已收款")) {
            val capture = AutoBillScreenParser.parse(pkg, detail,
                listOf("已收款", "￥10.00", "付款方", nextLabel))
            if (capture != null) assertEquals(nextLabel, "", capture.pay.merchant)
            assertNull(AutoBillScreenParser.parse(pkg, syntheticResult,
                listOf("已收款", "￥10.00", "付款方", nextLabel)))
        }
    }

    @Test fun genericCandidateCannotUseOneHistoryRowsIdOrTimeWithoutHeading() {
        assertNull(AutoBillScreenParser.parse(pkg, syntheticResult,
            listOf("对方已收款", "￥10.00", "交易单号：1000050001202611211234567890")))
        assertNull(AutoBillScreenParser.parse(pkg, syntheticResult,
            listOf("对方已收款", "￥10.00", "交易时间：2025-03-08 12:34:56")))
        val syntheticDetail = "com.tencent.mm.plugin.remittance.ui.SyntheticDetailUI"
        assertNotNull(AutoBillScreenParser.parse(pkg, syntheticDetail,
            listOf("对方已收款", "￥10.00", "交易时间：2025-03-08 12:34:56")))
    }
}
