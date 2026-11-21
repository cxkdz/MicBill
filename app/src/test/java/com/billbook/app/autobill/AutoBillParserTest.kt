package com.billbook.app.autobill

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 通知文案没有契约，解析器必须能单测：微信/支付宝改一次文案，
 * 只需补一条用例并改 AutoBillParser，不用碰 Service 和 UI。
 */
class AutoBillParserTest {

    private fun pay(
        pkg: String = AutoBillParser.WECHAT_PKG,
        title: String = "",
        text: String = ""
    ): AutoBillParser.Outcome = AutoBillParser.parse(pkg, title, text, null)

    @Test
    fun wechatMerchantReceipt_isIncome() {
        val r = pay(title = "微信支付-星巴克店 收款 18.00 元")
        assertTrue("应识别出支付事件，实际 $r", r is AutoBillParser.Outcome.Pay)
        val p = (r as AutoBillParser.Outcome.Pay).value
        assertEquals(18.00, p.amount, 0.001)
        assertEquals(AutoBillParser.TYPE_INCOME, p.type)
        assertEquals("星巴克店", p.merchant)
    }

    @Test
    fun alipayArrival_isIncome_notExpense() {
        // 「支付宝」品牌名含「支付」，直接匹配方向词会误判成支出
        val r = pay(pkg = AutoBillParser.ALIPAY_PKG, title = "支付宝到账 12.00 元")
        assertTrue("应识别出支付事件，实际 $r", r is AutoBillParser.Outcome.Pay)
        assertEquals(AutoBillParser.TYPE_INCOME, (r as AutoBillParser.Outcome.Pay).value.type)
    }

    @Test
    fun wechatPaymentSuccess_isExpense() {
        val r = pay(title = "微信支付", text = "付款成功 25.50 元")
        assertTrue("应识别出支付事件，实际 $r", r is AutoBillParser.Outcome.Pay)
        val p = (r as AutoBillParser.Outcome.Pay).value
        assertEquals(25.50, p.amount, 0.001)
        assertEquals(AutoBillParser.TYPE_EXPENSE, p.type)
    }

    @Test
    fun genericCompletedRefund_requiresConfirmationAndDirection() {
        val r = pay(title = "退款成功 12.00 元")
        assertTrue("退款不能自动记账，实际 $r", r is AutoBillParser.Outcome.NeedsConfirmation)
        val hint = (r as AutoBillParser.Outcome.NeedsConfirmation).value
        assertEquals(null, hint.type)
        assertEquals(12.0, hint.amount!!, 0.001)
    }

    @Test
    fun bareRedPacket_isSkipped() {
        // 只有「红包」没有方向短语，宁可不记也不能猜
        val r = pay(title = "红包 8.88 元")
        assertTrue("方向不明应跳过，实际 $r", r is AutoBillParser.Outcome.Skipped)
    }

    @Test
    fun transferDirectionFromPhrase() {
        val out = pay(title = "微信转账助手", text = "转账成功，转账给张三 100.00 元")
        val income = pay(title = "微信转账助手", text = "张三向你转账 100.00 元，已收款")
        assertEquals(AutoBillParser.TYPE_EXPENSE, (out as AutoBillParser.Outcome.Pay).value.type)
        assertEquals(AutoBillParser.TYPE_INCOME, (income as AutoBillParser.Outcome.Pay).value.type)
    }

    @Test
    fun nonWatchedPackage_isSkipped() {
        val r = pay(pkg = "com.example.other", title = "付款成功 10.00 元")
        assertTrue(r is AutoBillParser.Outcome.Skipped)
    }

    @Test
    fun noAmount_isSkipped() {
        // 不能把订单号 / 时间当成金额
        val r = pay(title = "微信支付凭证", text = "订单号 202611150001")
        assertTrue("无金额应跳过，实际 $r", r is AutoBillParser.Outcome.Skipped)
    }

    @Test
    fun currencySymbolAmount() {
        val r = pay(pkg = AutoBillParser.ALIPAY_PKG, title = "支付宝", text = "付款成功 ¥9.90")
        assertTrue(r is AutoBillParser.Outcome.Pay)
        assertEquals(9.90, (r as AutoBillParser.Outcome.Pay).value.amount, 0.001)
    }

    @Test
    fun bankExpense_noMerchantButTailNumber() {
        // 农行真实文案（用户实测）：银行推送没有商户，只能拿到金额与卡尾号
        val r = AutoBillParser.parse(
            "com.android.bankabc", "中国农业银行",
            "您尾号为8275的农行借记卡于11月15日00:55发生一笔支出0.01元，详情请点击", null
        )
        assertTrue("应识别出银行支出，实际 $r", r is AutoBillParser.Outcome.Pay)
        val p = (r as AutoBillParser.Outcome.Pay).value
        assertEquals(0.01, p.amount, 0.001)
        assertEquals(AutoBillParser.TYPE_EXPENSE, p.type)
        assertEquals("", p.merchant)
        assertEquals("银行卡", p.payType)
        assertEquals("8275", p.accountHint)
    }

    @Test
    fun bankIncome_isIncome() {
        val r = AutoBillParser.parse(
            "com.chinamworld.main", "中国建设银行",
            "您尾号1234的账户于11月15日收入1000.00元", null
        )
        assertTrue(r is AutoBillParser.Outcome.Pay)
        assertEquals(AutoBillParser.TYPE_INCOME, (r as AutoBillParser.Outcome.Pay).value.type)
    }

    @Test
    fun bankRefund_isSkipped() {
        val r = AutoBillParser.parse(
            "com.icbc", "工商银行", "您尾号1234的账户收到退款5.00元", null
        )
        assertTrue("退款不能自动记账，实际 $r", r is AutoBillParser.Outcome.Skipped)
    }

    @Test
    fun bankTransferOut_isExpense() {
        val r = AutoBillParser.parse(
            "com.android.bankabc", "中国农业银行", "尾号8275的账户转出200.00元", null
        )
        assertTrue(r is AutoBillParser.Outcome.Pay)
        assertEquals(AutoBillParser.TYPE_EXPENSE, (r as AutoBillParser.Outcome.Pay).value.type)
    }

    @Test
    fun wechatChatCard_isSkipped() {
        // 真机实测：联系人发来的「【转账】请收款」不是支付结果
        val r = AutoBillParser.parse(
            AutoBillParser.WECHAT_PKG, "小曾（曾温典）", "【转账】请收款", null
        )
        assertTrue("聊天消息不是支付通知，实际 $r", r is AutoBillParser.Outcome.Skipped)
    }

    @Test
    fun wechatChatCardWithAmount_isSkipped() {
        // 聊天里提到金额也不能记：否则「晚上还你100元」这种消息会被记成收入
        val r = AutoBillParser.parse(
            AutoBillParser.WECHAT_PKG, "小曾（曾温典）", "【转账】100.00元 请收款", null
        )
        assertTrue("聊天消息即使带金额也不能记账，实际 $r", r is AutoBillParser.Outcome.Skipped)
    }

    @Test
    fun wechatPayServiceNotice_isIncome() {
        // 反向保护：服务号通知不能被聊天规则误伤
        val r = AutoBillParser.parse(
            AutoBillParser.WECHAT_PKG, "微信支付", "微信支付收款到账 0.01 元", null
        )
        assertTrue("服务号收款通知应识别为收入，实际 $r", r is AutoBillParser.Outcome.Pay)
        val p = (r as AutoBillParser.Outcome.Pay).value
        assertEquals(AutoBillParser.TYPE_INCOME, p.type)
        assertEquals(0.01, p.amount, 0.001)
    }

    @Test
    fun wechatTransferNotYetReceived_isSkipped() {
        val r = AutoBillParser.parse(
            AutoBillParser.WECHAT_PKG, "微信支付", "张三向你转账 50.00 元，待收款", null
        )
        assertTrue("还没到账的转账不能提前记，实际 $r", r is AutoBillParser.Outcome.Skipped)
    }

    @Test
    fun thousandsSeparatedAmount_isNotTruncated() {
        val result = pay(title = "微信支付", text = "付款成功 1,234.56元")
        assertTrue("应识别完整的千分位金额，实际 $result", result is AutoBillParser.Outcome.Pay)
        assertEquals(1234.56, (result as AutoBillParser.Outcome.Pay).value.amount, 0.001)
    }

    @Test
    fun malformedOrOversizedAmount_isNeverPartiallyMatched() {
        listOf("1,23.45元", "1.234元", "123456789元", "¥12.345", "-10.00元", "+10元")
            .forEach { amount ->
                val result = pay(title = "微信支付", text = "付款成功 $amount")
                assertTrue("不能截短金额 $amount：$result", result is AutoBillParser.Outcome.Skipped)
            }
    }

    @Test
    fun accountBalanceBeforeTransaction_isExcluded() {
        val result = pay(pkg = "com.icbc", title = "工商银行",
            text = "账户余额9,800.00元，本次支出20.00元，可用额度30,000.00元")
        assertTrue(result is AutoBillParser.Outcome.Pay)
        assertEquals(20.0, (result as AutoBillParser.Outcome.Pay).value.amount, 0.001)
        assertEquals(AutoBillParser.TYPE_EXPENSE, result.value.type)
    }

    @Test
    fun balanceOnlyNotice_isSkipped() {
        val result = pay(pkg = "com.icbc", title = "消费提醒", text = "当前余额800.00元")
        assertTrue(result is AutoBillParser.Outcome.Skipped)
    }

    @Test
    fun actualPaymentWinsOverDiscountAndOriginalPrice() {
        val result = pay(pkg = AutoBillParser.ALIPAY_PKG, title = "支付宝",
            text = "原价100.00元，优惠10.00元，实付90.00元，支付成功")
        assertTrue(result is AutoBillParser.Outcome.Pay)
        assertEquals(90.0, (result as AutoBillParser.Outcome.Pay).value.amount, 0.001)
    }

    @Test
    fun multipleTransactionsWithDifferentAmounts_areSkipped() {
        val result = pay(title = "微信支付", text = "付款成功10元，付款成功20元")
        assertTrue("无法确定单笔金额时应跳过：$result", result is AutoBillParser.Outcome.Skipped)
    }

    @Test
    fun failedPendingCancelledAndReversedTransactions_areSkipped() {
        listOf("支付失败", "付款未成功", "待付款", "扣款处理中", "转账已受理", "付款已取消",
            "交易关闭", "消费冲正", "预约扣款", "应还款", "请支付")
            .forEach { status ->
                val result = pay(pkg = AutoBillParser.ALIPAY_PKG, title = "支付宝", text = "$status 20.00元")
                assertTrue("$status 不能入账：$result", result is AutoBillParser.Outcome.Skipped)
            }
    }

    @Test
    fun paymentRecipientLabel_doesNotTurnExpenseIntoIncome() {
        val result = pay(title = "微信支付", text = "付款成功 18.00元\n收款方：瑞幸咖啡")
        assertTrue(result is AutoBillParser.Outcome.Pay)
        val parsed = (result as AutoBillParser.Outcome.Pay).value
        assertEquals(AutoBillParser.TYPE_EXPENSE, parsed.type)
        assertEquals("瑞幸咖啡", parsed.merchant)
    }

    @Test
    fun payerToUser_isIncomeDespitePaymentWord() {
        val result = pay(title = "微信支付", text = "张三向你付款 20.00元")
        assertTrue(result is AutoBillParser.Outcome.Pay)
        assertEquals(AutoBillParser.TYPE_INCOME, (result as AutoBillParser.Outcome.Pay).value.type)
        assertEquals("张三", result.value.merchant)
    }

    @Test
    fun independentIncomeAndExpense_areAmbiguous() {
        val result = pay(title = "微信支付", text = "收款到账20.00元，付款成功20.00元")
        assertTrue(result is AutoBillParser.Outcome.Skipped)
    }

    @Test
    fun ordinaryChatMentioningPayment_isSkipped() {
        val result = pay(title = "张三", text = "我已支付20元，记得收款")
        assertTrue("聊天中的金额不是付款凭证：$result", result is AutoBillParser.Outcome.Skipped)
    }

    @Test
    fun expandedNotification_preservesMerchantLinesAndDoesNotDuplicateSummary() {
        val result = AutoBillParser.parse(AutoBillParser.WECHAT_PKG, "微信支付",
            "付款成功 25.50元", "付款成功 25.50元\n收款方：7-Eleven便利店")
        assertTrue(result is AutoBillParser.Outcome.Pay)
        val parsed = (result as AutoBillParser.Outcome.Pay).value
        assertEquals(25.50, parsed.amount, 0.001)
        assertEquals("7-Eleven便利店", parsed.merchant)
    }

    @Test
    fun wechatTransferAssistant_completedOutgoingUsesRecipient() {
        val result = pay(title = "微信转账助手",
            text = "转账成功\n转账金额：0.01元\n付款方：我\n收款方：张三")
        assertTrue("转账完成应识别：$result", result is AutoBillParser.Outcome.Pay)
        val parsed = (result as AutoBillParser.Outcome.Pay).value
        assertEquals(0.01, parsed.amount, 0.001)
        assertEquals(AutoBillParser.TYPE_EXPENSE, parsed.type)
        assertEquals("张三", parsed.merchant)
    }

    @Test
    fun wechatReceiptAssistant_completedIncomingUsesPayer() {
        val result = pay(title = "微信收款助手",
            text = "已收款\n收款金额：0.01元\n收款方：我\n付款方：李四")
        assertTrue("收款完成应识别：$result", result is AutoBillParser.Outcome.Pay)
        val parsed = (result as AutoBillParser.Outcome.Pay).value
        assertEquals(AutoBillParser.TYPE_INCOME, parsed.type)
        assertEquals("李四", parsed.merchant)
    }

    @Test
    fun transferAssistantTitle_doesNotSupplyIncomeDirection() {
        val result = pay(title = "微信收款助手", text = "转账成功 10元，收款方：张三")
        assertTrue(result is AutoBillParser.Outcome.Pay)
        assertEquals(AutoBillParser.TYPE_EXPENSE, (result as AutoBillParser.Outcome.Pay).value.type)
    }

    @Test
    fun recipientAcceptedTransfer_isSenderExpense() {
        listOf("对方已收款", "收款方已收款", "收款人已收款").forEach { status ->
            val result = pay(title = "微信转账助手", text = "$status\n转账金额：10元\n收款方：张三")
            assertTrue("$status 应识别：$result", result is AutoBillParser.Outcome.Pay)
            assertEquals(AutoBillParser.TYPE_EXPENSE, (result as AutoBillParser.Outcome.Pay).value.type)
        }
    }

    @Test
    fun incomingPartyFields_doNotSupplyExpenseDirection() {
        val result = pay(title = "微信支付", text = "收款到账 10元\n付款方：李四\n收款方：张三")
        assertTrue(result is AutoBillParser.Outcome.Pay)
        val parsed = (result as AutoBillParser.Outcome.Pay).value
        assertEquals(AutoBillParser.TYPE_INCOME, parsed.type)
        assertEquals("李四", parsed.merchant)
    }

    @Test
    fun genericWechatTitle_requiresStructuredCompletedResult() {
        val result = pay(title = "微信", text = "转账成功\n转账金额：0.01元\n收款方：张三")
        assertTrue(result is AutoBillParser.Outcome.Pay)
        assertEquals(AutoBillParser.TYPE_EXPENSE, (result as AutoBillParser.Outcome.Pay).value.type)
        assertTrue(pay(title = "微信", text = "转账成功 0.01元") is AutoBillParser.Outcome.Skipped)
        assertTrue(pay(title = "微信", text = "张三：转账成功 0.01元\n收款方：张三")
            is AutoBillParser.Outcome.Skipped)
    }

    @Test
    fun contactNamesContainingServiceOrSuccessWords_areStillSkipped() {
        listOf("张三微信支付", "微信支付小助手张三", "张三付款成功", "微信转账助手张三").forEach { title ->
            val result = pay(title = title, text = "转账成功 10元\n收款方：李四")
            assertTrue("联系人名 $title 不应绕过来源规则：$result", result is AutoBillParser.Outcome.Skipped)
        }
    }

    @Test
    fun transferInvitationWithoutCompletion_isSkipped() {
        listOf("张三向你转账10元", "转账给张三10元", "收到一笔转账10元，请及时收取").forEach { body ->
            val result = pay(title = "微信转账助手", text = body)
            assertTrue("转账没有完成状态不能入账：$result", result is AutoBillParser.Outcome.Skipped)
        }
    }

    @Test
    fun simpleCompletedResultTitle_isRecognized() {
        val out = pay(title = "转账成功", text = "转账金额：5元\n收款方：张三")
        val income = pay(title = "已收款", text = "收款金额：5元\n付款方：李四")
        assertTrue(out is AutoBillParser.Outcome.Pay)
        assertTrue(income is AutoBillParser.Outcome.Pay)
        assertEquals(AutoBillParser.TYPE_EXPENSE, (out as AutoBillParser.Outcome.Pay).value.type)
        assertEquals(AutoBillParser.TYPE_INCOME, (income as AutoBillParser.Outcome.Pay).value.type)
    }

    @Test
    fun contactTransferAcceptedWithoutAmount_createsPartialConfirmation() {
        val result = pay(title = "家人（示例）", text = "[转账] 已被接收")
        assertTrue("已完成但缺金额应待补全：$result", result is AutoBillParser.Outcome.NeedsConfirmation)
        val hint = (result as AutoBillParser.Outcome.NeedsConfirmation).value
        assertEquals("家人（示例）", hint.merchant)
        assertEquals(AutoBillParser.TYPE_EXPENSE, hint.type)
        assertEquals(null, hint.amount)
        assertTrue(hint.originalText.contains("[转账] 已被接收"))
    }

    @Test
    fun completedChatReceipts_neverBecomeAutomaticallySavedPayments() {
        for ((body, amount) in listOf("【转账】10.00元 已被接收" to 10.0,
            "[转账] 对方已收款 ￥1,234.56" to 1234.56,
            "【转账】已被接收：¥18.00" to 18.0,
            "[转账] 转账已被接收 ¥18.00" to 18.0,
            "[转账] 对方已收钱 18元" to 18.0,
            "[转账] 对方已接收 18元" to 18.0)) {
            val result = pay(title = "张三", text = body)
            assertTrue("聊天回执只能生成确认草稿：$result", result is AutoBillParser.Outcome.NeedsConfirmation)
            val hint = (result as AutoBillParser.Outcome.NeedsConfirmation).value
            assertEquals(AutoBillParser.TYPE_EXPENSE, hint.type)
            assertEquals(amount, hint.amount!!, 0.001)
        }
    }

    @Test
    fun chatCollectedStatusWithoutPerspective_requiresUserToChooseDirection() {
        for (body in listOf("[转账] 已收款", "【转账】已收钱 20元", "【转账】已接收", "[转账] 你已收款")) {
            val result = pay(title = "李四", text = body)
            assertTrue(result is AutoBillParser.Outcome.NeedsConfirmation)
            val hint = (result as AutoBillParser.Outcome.NeedsConfirmation).value
            assertEquals(null, hint.type)
        }
    }

    @Test
    fun transferCardWithoutClearValidCurrencyAmount_keepsAmountMissing() {
        for (body in listOf("[转账] 10 已被接收", "[转账] ￥1.234 已被接收",
            "[转账] -10元 已被接收", "[转账] 0元 已被接收",
            "[转账] 1000000元 已被接收", "[转账] 10元 已被接收 20元")) {
            val result = pay(title = "张三", text = body)
            assertTrue("已完成回执保留人工确认入口：$result", result is AutoBillParser.Outcome.NeedsConfirmation)
            assertEquals(body, null, (result as AutoBillParser.Outcome.NeedsConfirmation).value.amount)
        }
    }

    @Test
    fun pendingChatTransfers_haveSpecificReasonAndNoDraft() {
        for (body in listOf("[转账] 请收款", "【转账】100元 待收款", "[转账] 未被接收")) {
            val result = pay(title = "张三", text = body)
            assertTrue(result is AutoBillParser.Outcome.Skipped)
            assertEquals("转账尚未收款，暂不记账", (result as AutoBillParser.Outcome.Skipped).reason)
        }
    }

    @Test
    fun proseOrUnrecognisedCardCannotForgeACompletedPayment() {
        for (body in listOf("我发的[转账] 已被接收", "[转账] 已被接收，记得还我",
            "【转账】完成了", "[转账] 你已收款，请查收", "[转账] 已被接收，未到账")) {
            val result = pay(title = "张三", text = body)
            assertTrue("不完整或带散文的回执不能生成草稿：$result", result is AutoBillParser.Outcome.Skipped)
        }
    }

    @Test
    fun transferChatCardHint_isLimitedToWechat() {
        val result = pay(pkg = AutoBillParser.ALIPAY_PKG, title = "李四", text = "[转账] 已被接收")
        assertTrue(result is AutoBillParser.Outcome.Skipped)
    }

    @Test
    fun actualAggregatedWechatReceipt_retainsOneHintAndWarnsAboutHiddenMessages() {
        val title = "家人（示例）"
        val body = "[2条]家人（示例）: [转账] 已被接收"
        val first = pay(title = title, text = body)
        val second = pay(title = title, text = body)
        assertTrue(first is AutoBillParser.Outcome.NeedsConfirmation)
        assertTrue(second is AutoBillParser.Outcome.NeedsConfirmation)
        val hint = (first as AutoBillParser.Outcome.NeedsConfirmation).value
        assertEquals(hint, (second as AutoBillParser.Outcome.NeedsConfirmation).value)
        assertEquals(2, hint.aggregateCount)
        assertEquals(title, hint.merchant)
        assertEquals(null, hint.amount)
        assertEquals(AutoBillParser.TYPE_EXPENSE, hint.type)
        assertTrue(hint.reason.contains("只能识别当前回执"))
        assertTrue(hint.reason.contains("不自动生成2笔"))
        assertTrue(hint.originalText.contains(body))
    }

    @Test
    fun aggregateEnvelopeRequiresExactTitleAndBoundedCount() {
        for (body in listOf("[2条]张三: [转账] 已被接收", "[0条]李四: [转账] 已被接收",
            "[100条]李四: [转账] 已被接收", "[02条]李四: [转账] 已被接收",
            "[2条]李四别名: [转账] 已被接收", "[2条]李四: 我发的[转账] 已被接收")) {
            assertTrue(body, pay(title = "李四", text = body) is AutoBillParser.Outcome.Skipped)
        }
        val accepted = pay(title = "李四", text = "【99条】李四：[转账] 10元 已收款")
        assertTrue(accepted is AutoBillParser.Outcome.NeedsConfirmation)
        assertEquals(99, (accepted as AutoBillParser.Outcome.NeedsConfirmation).value.aggregateCount)
        assertEquals(10.0, accepted.value.amount!!, 0.001)
        assertEquals(null, accepted.value.type)
    }

    @Test
    fun aggregateCountCannotSupplyAMissingTransactionAmount() {
        val result = pay(title = "李四", text = "[20条]李四: [转账] 已被接收")
        val hint = (result as AutoBillParser.Outcome.NeedsConfirmation).value
        assertEquals(20, hint.aggregateCount)
        assertEquals(null, hint.amount)
        val pending = pay(title = "李四", text = "[2条]李四: [转账] 请收款")
        assertTrue(pending is AutoBillParser.Outcome.Skipped)
    }

    @Test
    fun explicitlyCompletedIncomingRefunds_requireManualConfirmation() {
        for (status in listOf("退款到账", "退款已到账", "已原路退回", "已退回零钱", "已退回至零钱",
            "已退还至零钱", "已退还到零钱通")) {
            val result = pay(title = "微信支付", text = "$status 10元\n商户名称：星巴克")
            assertTrue("$status 只能待确认：$result", result is AutoBillParser.Outcome.NeedsConfirmation)
            val hint = (result as AutoBillParser.Outcome.NeedsConfirmation).value
            assertEquals(AutoBillParser.TYPE_INCOME, hint.type)
            assertEquals(10.0, hint.amount!!, 0.001)
            assertTrue(hint.reason.contains("核对原账单"))
        }
    }

    @Test
    fun genericReturnedTransfer_hasNoInventedDirection() {
        for (body in listOf("转账已退回 10元", "已退回 10元")) {
            val result = pay(title = "微信转账助手", text = body)
            assertTrue(result is AutoBillParser.Outcome.NeedsConfirmation)
            assertEquals(null, (result as AutoBillParser.Outcome.NeedsConfirmation).value.type)
        }
        val card = pay(title = "李四", text = "[2条]李四: [转账] 已退回 10元")
        assertTrue(card is AutoBillParser.Outcome.NeedsConfirmation)
        assertEquals(null, (card as AutoBillParser.Outcome.NeedsConfirmation).value.type)
        assertEquals(2, card.value.aggregateCount)
    }

    @Test
    fun returnedWechatCardWithoutAmount_requiresCheckingOriginalBill() {
        for ((body, count) in listOf("[转账] 已退还" to 1, "[2条]张三: [转账] 已退还" to 2)) {
            val result = pay(title = "张三", text = body)
            assertTrue(body, result is AutoBillParser.Outcome.NeedsConfirmation)
            val hint = (result as AutoBillParser.Outcome.NeedsConfirmation).value
            assertEquals(null, hint.type)
            assertEquals(null, hint.amount)
            assertEquals(count, hint.aggregateCount)
            assertTrue(hint.reason.contains("核对原账单"))
        }
    }

    @Test
    fun pendingOrConversationalWechatReturnsDoNotCreateDrafts() {
        for (body in listOf("[转账] 待退还", "[转账] 已退还，尚未到账",
            "[转账] 已退还，记得查看", "我发的[转账] 已退还")) {
            assertTrue(body, pay(title = "张三", text = body) is AutoBillParser.Outcome.Skipped)
        }
    }

    @Test
    fun pendingOrFailedRefundsAreNotCompletedRefundHints() {
        for (body in listOf("退款申请 10元", "退款审核中 10元", "退款处理中 10元", "退款失败 10元",
            "退款已到账10元，预计明日生效", "退款成功10元，尚未到账", "退款成功10元，审核中")) {
            val result = pay(title = "微信支付", text = body)
            assertTrue("$body 尚未完成：$result", result is AutoBillParser.Outcome.Skipped)
        }
    }

    @Test
    fun ordinaryContactRefundProse_isNotARefundReceipt() {
        for (body in listOf("退款到账 10元", "我已经退款成功10元", "已原路退回10元，请查收")) {
            val result = pay(title = "张三", text = body)
            assertTrue("普通聊天不能变成退款凭证：$result", result is AutoBillParser.Outcome.Skipped)
        }
        val alipay = pay(pkg = AutoBillParser.ALIPAY_PKG, title = "支付宝", text = "退款已到账 ￥10.00")
        assertTrue(alipay is AutoBillParser.Outcome.NeedsConfirmation)
        assertEquals(AutoBillParser.TYPE_INCOME, (alipay as AutoBillParser.Outcome.NeedsConfirmation).value.type)
    }
}
