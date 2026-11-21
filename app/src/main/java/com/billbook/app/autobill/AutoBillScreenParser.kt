package com.billbook.app.autobill

/** Payment result pages are a separate input: a chat card is never proof of completion. */
object AutoBillScreenParser {
    data class Capture(
        val pay: AutoBillParser.ParsedPay,
        val transactionId: String,
        val transactionTime: Long? = null,
        val needsDirection: Boolean = false,
        val kind: String = "payment",
        val captureMethod: String = "accessibility"
    )

    data class Inspection(val capture: Capture?, val reason: String)

    private const val WECHAT_SHARED_TRANSFER = "com.tencent.mm.plugin.remittance.ui.RemittanceUI"
    private val wechatActivities = setOf(
        "RemittanceDetailUI", "RemittanceResultNewUI", "RemittanceResultUI",
        "WalletOrderInfoNewUI", "WalletOrderInfoUI"
    )
    private val alipayActivities = setOf("PayResultActivity", "TransferResultActivity")
    private val candidateWechatModules = listOf(
        "com.tencent.mm.plugin.remittance.ui.",
        "com.tencent.mm.plugin.wallet_core.ui."
    )
    private val excludedCandidateName = Regex("""(?i)history|home|list|balance|launcher|chatting|input|entry|request""")
    private val paymentResultHeadings = setOf(
        "转账成功", "支付成功", "付款成功", "收款成功", "收款到账", "已收款",
        "转账详情", "转账结果", "支付结果", "付款结果", "收款详情", "交易详情", "微信转账", "支付凭证",
        "退款详情", "退款结果", "退款到账", "退款成功", "已退回", "已退还"
    )
    private val resultActivityName = Regex("""Detail|Result|OrderInfo""")
    private val structuredFieldLabel = Regex(
        """^(?:收款方|收款人|付款方|付款人|转账人|商户名称|商家|转账金额|收款金额|付款金额|支付金额|实付金额|退款金额|退回金额|金额|退款单号|退款时间|退回时间|转账单号|交易单号|订单号|收款时间|到账时间|交易时间|转账时间|付款时间|支付时间|支付方式|付款方式|到账方式)[:：]?$"""
    )
    private val accountOrHistoryHeadings = setOf(
        "零钱", "微信零钱", "钱包", "交易记录", "转账记录", "历史记录", "零钱明细", "收支明细", "账单", "账单详情"
    )
    private val money = Regex("""^[¥￥]?\s*((?:\d{1,3}(?:,\d{3})+|\d+)(?:\.\d{1,2})?)\s*元?$""")
    private val amountField = Regex("""^(?:转账金额|收款金额|付款金额|支付金额|实付金额|退款金额|退回金额|退还金额|金额)\s*[:：]?\s*(.*)$""")
    private val otherAmountField = Regex(
        """^(?:账户余额|可用余额|余额|微信零钱余额|零钱通余额|零钱余额|微信零钱|零钱通|零钱|支付方式|付款方式|收款方式|到账方式|手续费|服务费|优惠|原价|应付|额度)(?:[:：\s（(]|$)"""
    )
    private val rejected = Regex(
        """^(?:(?:此笔|本次|该笔)?(?:转账|支付|付款|收款|交易|订单|退款|退回|退还)(?:状态[:：\s]*)?)?(?:已)?(?:失败|未成功|未完成|未到账|未支付|未付款|撤销|冲正|处理中|受理中|待支付|待付款|取消|关闭)(?:$|[\s，,。！!：:]|成功)"""
    )
    private val transferAcceptedStatuses = setOf(
        "已被接收", "转账已被接收", "对方已收款", "对方已收钱", "对方已接收", "收款方已收款", "收款人已收款"
    )
    private val expenseStatuses = setOf("转账成功", "支付成功", "付款成功") + transferAcceptedStatuses
    private val pendingTransferStatus = Regex(
        """^(?:待收款|待接收|未收款|未被接收|等待(?:对方)?(?:收款|接收)|请收款|确认收款|确认转账)(?:$|[\s，,。！!：:])"""
    )
    private val incomeStatuses = setOf("收款成功", "收款到账", "已收款", "已收钱", "你已收款", "你已收钱", "已存入零钱", "已存入零钱通", "资金已存入零钱", "资金已存入零钱通")
    private val refundIncomeStatuses = setOf("退款到账", "退款已到账", "退款已原路退回", "已原路退回", "已退回零钱", "已退还至零钱", "已退回到零钱", "资金已退回零钱", "退款已退回零钱")
    private val refundUnknownStatuses = setOf("退款成功", "已退款", "已退回", "已退还", "转账已退回", "转账已退还", "你已退款", "对方已退款")
    private val refundStatuses = refundIncomeStatuses + refundUnknownStatuses
    private val depositStatus = Regex("""^(已存入零钱(?:通)?)\s*[（(]\s*(.+?)\s*[)）]$""")

    fun isPaymentActivity(packageName: String, className: String): Boolean {
        val shortName = className.substringAfterLast('.')
        return when (packageName) {
            AutoBillParser.WECHAT_PKG -> className.startsWith("com.tencent.mm.plugin.") && shortName in wechatActivities
            AutoBillParser.ALIPAY_PKG -> className.startsWith("com.alipay.") && shortName in alipayActivities
            else -> false
        }
    }

    /**
     * Versions can rename an Activity inside a payment module. The service must
     * also resolve an unknown candidate as an actual Activity before reading it.
     * parse() then requires structured transaction evidence beyond a status label.
     */
    fun isCandidatePaymentActivity(packageName: String, className: String): Boolean {
        if (isPaymentActivity(packageName, className)) return true
        if (packageName != AutoBillParser.WECHAT_PKG || '$' in className) return false
        val module = candidateWechatModules.firstOrNull { className.startsWith(it) } ?: return false
        val activityName = className.removePrefix(module)
        if (!Regex("[A-Za-z_][A-Za-z0-9_]*").matches(activityName)) return false
        return !excludedCandidateName.containsMatchIn(activityName)
    }

    fun parse(packageName: String, className: String, texts: List<String>): Capture? =
        inspect(packageName, className, texts).capture

    /** Image text has a separate formatting pass; accessibility text keeps its original contract. */
    fun inspectOcr(packageName: String, className: String, texts: List<String>): Inspection {
        val normalized = AutoBillOcrText.normalize(texts)
        val pending = Regex("""^(?:待收款|待接收|未收款|未被接收|等待(?:对方)?(?:收款|接收)|请收款|确认收款|确认转账)(?:$|[\s，,。！!：:])""")
        if (normalized.any { pending.containsMatchIn(it) }) {
            return Inspection(null, "转账尚未收款，暂不生成账单")
        }
        val imageAmounts = ocrAmountsWithoutCurrency(packageName, className, normalized)
        if (imageAmounts.size > 1) {
            return Inspection(null, "本机识图读到相互冲突的交易金额，请手动核对")
        }
        val imageAmount = imageAmounts.singleOrNull()
        var result = inspect(packageName, className, normalized)
        val parsedAmount = result.capture?.pay?.amount
        if (parsedAmount != null && imageAmount != null && parsedAmount != imageAmount) {
            return Inspection(null, "本机识图读到相互冲突的交易金额，请手动核对")
        }
        if (result.capture == null && imageAmount != null &&
            result.reason == "已识别完成状态，但未读到唯一有效的交易金额") {
            result = inspect(packageName, className, normalized + ("金额：" + imageAmount.toString()))
        }
        return result.copy(capture = result.capture?.copy(captureMethod = "local_window_ocr"))
    }

    /**
     * The currency glyph in WeChat's large amount is sometimes omitted by OCR.
     * Recover only a complete decimal immediately below an exact status, with
     * two distinct transaction-detail fields. Never join or correct its digits.
     */
    private fun ocrAmountsWithoutCurrency(packageName: String, className: String, lines: List<String>): List<Double> {
        if (packageName != AutoBillParser.WECHAT_PKG ||
            className != "com.tencent.mm.plugin.remittance.ui.RemittanceDetailUI" ||
            lines.any { amountField.matches(it) }) return emptyList()
        val receiptFields = setOf("转账时间", "收款时间", "付款时间", "支付时间",
            "交易时间", "转账单号", "交易单号", "付款方", "付款人", "收款方", "收款人")
        val fields = lines.filter {
            it.substringBefore('：').substringBefore(':').trim() in receiptFields
        }.map { it.substringBefore('：').substringBefore(':').trim() }.distinct()
        if (fields.size < 2) return emptyList()
        val statusSet = expenseStatuses + incomeStatuses + refundStatuses
        return lines.mapIndexedNotNull { index, line ->
            if (!Regex("""\d{1,6}\.\d{2}""").matches(line)) return@mapIndexedNotNull null
            val previous = lines.getOrNull(index - 1) ?: return@mapIndexedNotNull null
            val statusParts = previous.split(Regex("""[\s，,。]+"""))
            if (statusParts.isEmpty() || statusParts.any { it !in statusSet }) return@mapIndexedNotNull null
            line.toDoubleOrNull()?.takeIf(AutoBillPolicy::validAmount)
        }.distinct()
    }

    fun inspect(packageName: String, className: String, texts: List<String>): Inspection {
        fun skipped(reason: String) = Inspection(null, reason)
        val knownActivity = isPaymentActivity(packageName, className)
        if (!knownActivity && !isCandidatePaymentActivity(packageName, className)) return skipped("当前页面尚未适配")
        val lines = texts.flatMap { it.split('\n') }.map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.isEmpty()) return skipped("支付页面没有提供可读取文本")
        if (!knownActivity && lines.firstOrNull() in accountOrHistoryHeadings) return skipped("当前是余额或历史列表，请打开单笔交易详情")
        val sharedTransferActivity = packageName == AutoBillParser.WECHAT_PKG && className == WECHAT_SHARED_TRANSFER
        // Check status nodes, not arbitrary substrings in merchant names or help links.
        if (lines.any { rejected.containsMatchIn(it) }) return skipped("交易尚未完成、已失败或被撤销")
        if (sharedTransferActivity && lines.any { pendingTransferStatus.containsMatchIn(it) }) {
            return skipped("转账尚未收款，请打开对方已收款的交易详情")
        }
        val statuses = lines.flatMap { line ->
            val deposit = depositStatus.matchEntire(line)
            if (deposit != null && money.matches(deposit.groupValues[2]) &&
                hasCurrency(deposit.groupValues[2])) {
                // A parenthesized wallet value can be an account balance. It
                // confirms the deposit state but never supplies transaction money.
                listOf(deposit.groupValues[1])
            } else {
                val parts = line.split(Regex("""[\s，,。]+"""))
                if (parts.all { it in expenseStatuses || it in incomeStatuses || it in refundStatuses }) parts else emptyList()
            }
        }
        val expense = statuses.firstOrNull { it in expenseStatuses }
        val income = statuses.firstOrNull { it in incomeStatuses }
        val refund = statuses.firstOrNull { it in refundStatuses }
        // WeChat uses RemittanceUI for both entry and the sender's completed transfer.
        // Only an explicit recipient-accepted status can qualify the shared screen.
        if (sharedTransferActivity && (refund != null || statuses.none { it in transferAcceptedStatuses })) {
            return skipped("未读取到对方已收款的转账结果")
        }
        if (refund == null && expense == null && income == null) return skipped("未读取到明确的收款、付款或退款完成状态")
        if (refund == null && expense != null && income != null) return skipped("页面同时含有收支状态，请手动核对")
        val needsDirection = refund != null && statuses.none { it in refundIncomeStatuses }
        val type = if (income != null || refund != null) AutoBillParser.TYPE_INCOME else AutoBillParser.TYPE_EXPENSE

        val amount = extractAmount(lines, refund != null)?.takeIf { AutoBillPolicy.validAmount(it) }
            ?: return skipped("已识别完成状态，但未读到唯一有效的交易金额")

        val merchantLabels = if (type == AutoBillParser.TYPE_INCOME) listOf("付款方", "付款人", "转账人")
            else listOf("收款方", "收款人", "商户名称", "商家")
        val merchant = labelledValue(lines, merchantLabels).takeIf {
            it.length in 2..30 && !money.matches(it) && !structuredFieldLabel.matches(it) &&
                it !in expenseStatuses && it !in incomeStatuses && it !in refundStatuses && it !in paymentResultHeadings
        }.orEmpty()
        val idLabels = if (refund != null) listOf("退款单号", "退款交易单号", "退回单号")
            else listOf("转账单号", "交易单号", "订单号")
        val transactionId = labelledValue(lines, idLabels)
            .takeIf { Regex("[A-Za-z0-9]{10,80}").matches(it) }.orEmpty()
        val timeLabels = if (refund != null) {
            listOf("退款时间", "退回时间", "到账时间", "交易时间")
        } else if (type == AutoBillParser.TYPE_INCOME) {
            listOf("收款时间", "到账时间", "交易时间", "转账时间", "付款时间")
        } else listOf("付款时间", "支付时间", "转账时间", "交易时间")
        val transactionTimeText = timeLabels.asSequence().map { labelledValue(lines, listOf(it)) }
            .firstOrNull { it.isNotEmpty() }.orEmpty().take(40)
        val transactionTime = parseTransactionTime(transactionTimeText)
        if (!knownActivity && !sharedTransferActivity) {
            val hasResultHeading = lines.any { it in paymentResultHeadings }
            if (!resultActivityName.containsMatchIn(className.substringAfterLast('.')) && !hasResultHeading) return skipped("未读取到单笔交易详情标题")
            val hasCounterpartyAndHeading = merchant.isNotEmpty() && hasResultHeading
            if (transactionId.isEmpty() && transactionTime == null && !hasCounterpartyAndHeading) return skipped("已读到金额和完成状态，但交易凭证字段不完整")
        }
        // Only structured payment fields leave the view tree; chat text is not saved.
        val original = buildList {
            add(refund ?: income ?: expense.orEmpty())
            add("金额：%.2f 元".format(java.util.Locale.CHINA, amount))
            if (merchant.isNotEmpty()) add("对方：$merchant")
            if (transactionTimeText.isNotEmpty()) add("交易时间：$transactionTimeText")
            if (transactionId.isNotEmpty()) add("交易单号：$transactionId")
        }.joinToString("\n")
        val capture = Capture(AutoBillParser.ParsedPay(
            amount, type, merchant, AutoBillParser.channelLabel(packageName),
            if (packageName == AutoBillParser.WECHAT_PKG) "微信" else "支付宝", original
        ), transactionId, transactionTime, needsDirection, if (refund != null) "refund" else "payment")
        return Inspection(capture, "已读取单笔交易，请核对后确认")
    }

    private fun parseTransactionTime(text: String): Long? {
        val formats = listOf(
            "yyyy-MM-dd HH:mm:ss" to "\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}",
            "yyyy-MM-dd HH:mm" to "\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}",
            "yyyy/MM/dd HH:mm:ss" to "\\d{4}/\\d{2}/\\d{2} \\d{2}:\\d{2}:\\d{2}",
            "yyyy/MM/dd HH:mm" to "\\d{4}/\\d{2}/\\d{2} \\d{2}:\\d{2}",
            "yyyy年MM月dd日 HH:mm:ss" to "\\d{4}年\\d{2}月\\d{2}日 \\d{2}:\\d{2}:\\d{2}",
            "yyyy年MM月dd日 HH:mm" to "\\d{4}年\\d{2}月\\d{2}日 \\d{2}:\\d{2}",
            "yyyy年MM月dd日HH:mm:ss" to "\\d{4}年\\d{2}月\\d{2}日\\d{2}:\\d{2}:\\d{2}",
            "yyyy年MM月dd日HH:mm" to "\\d{4}年\\d{2}月\\d{2}日\\d{2}:\\d{2}",
            "yyyy-MM-dd" to "\\d{4}-\\d{2}-\\d{2}",
            "yyyy/MM/dd" to "\\d{4}/\\d{2}/\\d{2}",
            "yyyy年MM月dd日" to "\\d{4}年\\d{2}月\\d{2}日"
        )
        for ((pattern, shape) in formats) {
            if (!Regex(shape).matches(text)) continue
            val position = java.text.ParsePosition(0)
            val formatter = java.text.SimpleDateFormat(pattern, java.util.Locale.CHINA).apply {
                isLenient = false
            }
            val date = formatter.parse(text, position)
            if (date != null && position.index == text.length) return date.time
        }
        return null
    }

    private fun extractAmount(lines: List<String>, isRefund: Boolean = false): Double? {
        fun value(text: String): Double? = money.matchEntire(text)?.groupValues?.get(1)
            ?.replace(",", "")?.toDoubleOrNull()

        fun followingValue(index: Int): Double? {
            val next = lines.getOrNull(index + 1) ?: return null
            return if (next in setOf("¥", "￥")) {
                value(next + lines.getOrNull(index + 2).orEmpty())
            } else value(next)
        }

        val allLabelled = lines.withIndex().filter { amountField.matches(it.value) }
        val refundLabels = allLabelled.filter { Regex("^(?:退款|退回|退还)金额").containsMatchIn(it.value) }
        val labelled = if (isRefund && refundLabels.isNotEmpty()) refundLabels else allLabelled
        if (isRefund && refundLabels.isEmpty() && allLabelled.any {
                Regex("^(?:转账|收款|付款|支付|实付)金额").containsMatchIn(it.value) }) return null
        if (labelled.isNotEmpty()) {
            val values = labelled.map { (index, line) ->
                val inline = amountField.matchEntire(line)!!.groupValues[1]
                if (inline.isEmpty()) followingValue(index) else if (inline in setOf("¥", "￥")) {
                    value(inline + lines.getOrNull(index + 1).orEmpty())
                } else value(inline)
            }
            if (values.any { it == null }) return null
            return values.filterNotNull().distinct().singleOrNull()
        }

        // Without an amount field, require currency on the value or an adjacent
        // currency node. A contact's number, count or order number is not money.
        val amounts = lines.mapIndexedNotNull { index, line ->
            val previous = lines.getOrNull(index - 1).orEmpty()
            val beforePrevious = lines.getOrNull(index - 2).orEmpty()
            if (otherAmountField.containsMatchIn(previous) ||
                (previous in setOf("¥", "￥") && otherAmountField.containsMatchIn(beforePrevious))) {
                return@mapIndexedNotNull null
            }
            val adjacentCurrency = previous in setOf("¥", "￥") || lines.getOrNull(index + 1) == "元"
            if (hasCurrency(line) || adjacentCurrency) value(line) else null
        }.distinct()
        return amounts.singleOrNull()
    }

    private fun hasCurrency(text: String): Boolean =
        text.startsWith('¥') || text.startsWith('￥') || text.endsWith("元")

    private fun labelledValue(lines: List<String>, labels: List<String>): String {
        for ((index, line) in lines.withIndex()) {
            for (label in labels) {
                if (line == label || line == "$label：" || line == "$label:") {
                    return lines.getOrNull(index + 1).orEmpty()
                }
                if (line.startsWith("$label：") || line.startsWith("$label:")) {
                    return line.substring(label.length + 1).trim()
                }
            }
        }
        return ""
    }
}
