package com.billbook.app.autobill

/**
 * 支付通知文本 → 结构化候选。
 *
 * 纯 Kotlin、不引用任何 Android 类，可直接跑 JVM 单元测试。
 * 通知文案没有契约且随版本变化，所以解析规则必须集中在一个可测的地方，
 * 不能散落在 Service / UI 里。
 *
 * 核心原则：**判不出方向宁可不生成草稿**。错账比漏账难发现得多。
 */
object AutoBillParser {

    const val WECHAT_PKG = "com.tencent.mm"
    const val ALIPAY_PKG = "com.eg.android.AlipayGphone"

    /**
     * 银行 App：包名 → 简称。
     *
     * 实测（农行 0.01 转账）：付款方拿不到微信/支付宝通知，只有银行推送。
     * 银行推送稳定，但**没有商户**，所以草稿确认时要让用户补名称。
     * 包名如与实际不符，可用设置里的「来源诊断」查真实包名后在这里补齐。
     */
    val BANK_PACKAGES = mapOf(
        "com.android.bankabc" to "农行",
        "com.icbc" to "工行",
        "com.chinamworld.main" to "建行",
        "com.chinamworld.bocmbci" to "中行",
        "cmb.pb" to "招行",
        "com.bankcomm.Bankcomm" to "交行",
        "com.yitong.mbank.psbc" to "邮储",
        "com.unionpay" to "云闪付"
    )

    /** 服务需要监听的包名全集 */
    fun watchedPackages(): Set<String> =
        setOf(WECHAT_PKG, ALIPAY_PKG) + BANK_PACKAGES.keys

    /** 包名 → 展示用的渠道名（微信 / 支付宝 / 农行…） */
    fun channelLabel(packageName: String): String = when {
        packageName == WECHAT_PKG -> "微信"
        packageName == ALIPAY_PKG -> "支付宝"
        BANK_PACKAGES.containsKey(packageName) -> BANK_PACKAGES.getValue(packageName)
        else -> packageName
    }

    const val TYPE_EXPENSE = 0
    const val TYPE_INCOME = 1

    /** 与 Bill 的常量保持一致，放在这里是为了让本类保持纯 JVM 可测 */
    data class ParsedPay(
        val amount: Double,
        val type: Int,
        val merchant: String,
        val channel: String,
        val payType: String,
        val originalText: String,
        /** 银行通知里的卡尾号，用于提示「哪张卡」，不参与分类 */
        val accountHint: String = ""
    )

    /** 聊天转账回执只提供确认线索；缺失金额和方向必须由用户补全。 */
    data class TransferHint(
        val merchant: String,
        val originalText: String,
        val type: Int? = null,
        val amount: Double? = null,
        val reason: String = "",
        val aggregateCount: Int = 1
    )

    sealed class Outcome {
        data class Pay(val value: ParsedPay) : Outcome()
        data class NeedsConfirmation(val value: TransferHint) : Outcome()
        data class Skipped(val reason: String) : Outcome()
    }

    private val TRANSFER_CARD_PREFIX = Regex("""^(?:\[转账\]|【转账】)""")
    private val TRANSFER_CARD_AMOUNT = """(?:[¥￥]\s*)?[+\-]?\d[\d,.]*(?:\s*元)?"""
    private val OUTGOING_TRANSFER_CARD_STATUSES = setOf(
        "已被接收", "转账已被接收", "对方已收款", "对方已收钱", "对方已接收", "收款方已收款", "收款人已收款"
    )
    private val COMPLETED_TRANSFER_CARD = Regex(
        """^(?:\[转账\]|【转账】)\s*(?:$TRANSFER_CARD_AMOUNT\s*)?(${OUTGOING_TRANSFER_CARD_STATUSES.joinToString("|")}|已收款|已收钱|已接收|你已收款)(?:\s*[:：]?\s*$TRANSFER_CARD_AMOUNT)?\s*$"""
    )
    private val PENDING_TRANSFER_CARD = Regex("""请收款|待收款|等待收款|未收款|未被接收|待接收""")
    private val COMPLETED_RETURN_STATUS = """退款已到账|退款到账|已原路退回|已退回至?零钱(?:通)?|已退还(?:至|到)零钱(?:通)?|退款成功|转账已退回|已退回"""
    private val COMPLETED_RETURN_CARD = Regex(
        """^(?:\[转账\]|【转账】)\s*(?:$TRANSFER_CARD_AMOUNT\s*)?($COMPLETED_RETURN_STATUS|已退还|转账已退还)(?:\s*[:：]?\s*$TRANSFER_CARD_AMOUNT)?\s*$"""
    )
    private val COMPLETED_RETURN = Regex(COMPLETED_RETURN_STATUS)
    private val INCOMING_RETURN = Regex("""退款已到账|退款到账|已原路退回|已退回至?零钱(?:通)?|已退还(?:至|到)零钱(?:通)?""")
    private val RETURN_NOT_COMPLETED = Regex(
        """退款申请|申请退款|退款审核|审核中|处理中|失败|未成功|未完成|取消|撤销|未到账|尚未|预计|将于|受理中|已受理|申请成功|待退|等待"""
    )

    // 先完整取出金额 token 再校验，不能把 1,234.56 截成 234.56，
    // 也不能把三位小数或超长数字截短后记入账本。
    private val MONEY_TOKEN = Regex(
        """(?<![\d.,+\-])(?:[¥￥]\s*(\d[\d,.]*)(?:\s*元)?|(\d[\d,.]*)\s*元)"""
    )
    private val MONEY_FORMAT = Regex("""(?:\d{1,3}(?:,\d{3})+|\d+)(?:\.\d{1,2})?""")
    private val AMOUNT_LABEL = Regex(
        """实付(?:金额)?|实际(?:支付|付款|扣款)|交易金额|转账金额|付款金额|支付金额|收款金额|扣款金额|本次(?:消费|支出|收入)|收款到账|收款成功|已收款|付款成功|支付成功|消费成功|转账成功|转账给|向你付款|向你转账|收入|支出|消费|付款|支付|扣款|转出|转入|存入|取款|入账|到账|代发|工资|利息|红包"""
    )
    private val NON_TRANSACTION_LABEL = Regex(
        """余额|结余|额度|累计|今日合计|优惠|立减|原价|应付|待还|账单金额"""
    )
    private val INCOMPLETE_TRANSACTION = Regex(
        """失败|未成功|不成功|未完成|未支付|未付款|未扣款|未到账|未入账|尚未|待支付|待付款|待扣款|待处理|待收款|等待|处理中|受理中|已受理|受理成功|申请成功|预约|将于|即将|预计|请(?:您)?(?:支付|付款|收款|确认)|取消|关闭|撤销|冲正|退款|退回|退还|冻结|解冻|到期|提醒还款|还款提醒|应还"""
    )

    // 先剔除品牌名，否则「支付宝」里的「支付」会污染方向词表
    private val BRAND_WORDS = listOf("微信支付", "支付宝", "云闪付")
    private val WECHAT_SERVICE_NAMES = listOf(
        "微信支付", "微信转账助手", "微信收款助手", "微信支付助手", "收款助手", "转账助手", "支付助手"
    )
    private val PAYMENT_RESULT_TITLE = Regex(
        """^(?:收款到账|收款成功|付款成功|支付成功|转账成功|已收款|支付凭证|交易提醒)(?:$|[\s：:¥￥\d])"""
    )
    private val STRUCTURED_RESULT_BODY = Regex(
        """^(?:微信支付[\s：:]*)?(?:转账成功|付款成功|支付成功|收款到账|收款成功|已收款)(?:$|[\s：:¥￥\d])"""
    )
    private val PARTY_FIELD = Regex(
        """(?:收款|付款)(?:方|人|账户|商户|单位)\s*[:：]\s*[^\n,，。;；]+"""
    )
    private val TRANSFER_COMPLETED = Regex(
        """转账成功|转账已成功|转账已到账|收款成功|收款到账|已收款|已收钱|已存入零钱|收到转账"""
    )
    // 「对方已收款」是付款人的转账结果，不是付款人又收到一笔收入。
    private val RECIPIENT_ACCEPTED = Regex("""(?:对方|收款方|收款人)已收款""")

    // 收入短语必须先查：含「转账」的复合短语不能被支出词抢走
    private val INCOME_PHRASES = listOf(
        "收款到账", "收款成功", "已收款", "已收钱", "已存入零钱", "向你付款", "向你转账",
        "收到转账", "转入", "到账", "收款", "收到红包", "领取红包"
    )
    private val EXPENSE_PHRASES = listOf(
        "付款成功", "已支付", "支付成功", "消费成功", "转账成功", "转账已成功", "发出红包", "发红包",
        "付款", "支付", "消费", "转账给", "付款给"
    )

    // 银行文案与支付平台不同：说的是「账户变动」而不是「支付成功」
    private val BANK_INCOME_PHRASES = listOf(
        "收入", "转入", "存入", "入账", "到账", "代发", "工资", "利息"
    )
    private val BANK_EXPENSE_PHRASES = listOf(
        "支出", "消费", "转出", "取款", "付款", "扣款", "还款", "缴费"
    )

    private val TAIL_PATTERN = Regex("""尾号[为]?\s*(\d{3,4})""")

    private val MERCHANT_PATTERNS = listOf(
        Regex("""(?:收款方|收款人|收款商户|收款单位|商户名称|商户|商家|交易商户)\s*[:：]\s*([^\n,，。;；]+)"""),
        Regex("""微信支付[-－—]\s*(.+?)\s*(?:收款|付款|支付|消费)"""),
        Regex("""向\s*(.+?)\s*(?:付款|支付|转账)"""),
        Regex("""(?:付款给|转账给)\s*(.+?)(?=\s*[¥￥\d]|[\n,，。;；]|$)"""),
        Regex("""(?:^|\s)([^\n,，。;；]+?)向你(?:付款|转账)""")
    )

    fun parse(packageName: String, title: String?, text: String?, bigText: String?): Outcome {
        val isBank = packageName in BANK_PACKAGES
        val channel = channelLabel(packageName)
        if (!isBank && packageName != WECHAT_PKG && packageName != ALIPAY_PKG) {
            return Outcome.Skipped("非监听包名")
        }
        // 与 Bill.PAY_* 文案一致；银行推送一律归到「银行卡」
        val payType = when {
            packageName == WECHAT_PKG -> "微信"
            packageName == ALIPAY_PKG -> "支付宝"
            else -> "银行卡"
        }

        val rawTitle = title.orEmpty().trim()
        val shortBody = text.orEmpty().trim()
        val expandedBody = bigText.orEmpty().trim()
        val body = when {
            expandedBody.contains(shortBody) -> expandedBody
            shortBody.contains(expandedBody) -> shortBody
            else -> listOf(shortBody, expandedBody).filter { it.isNotEmpty() }.joinToString("\n")
        }
        val all = listOf(rawTitle, body).filter { it.isNotEmpty() }.joinToString("\n")
        if (all.isBlank()) return Outcome.Skipped("通知无文本")

        val aggregate = if (packageName == WECHAT_PKG && rawTitle.isNotEmpty()) {
            Regex("""^(?:\[([1-9]\d?)条\]|【([1-9]\d?)条】)\s*${Regex.escape(rawTitle)}\s*[:：]\s*(.+)$""")
                .matchEntire(body)
        } else null
        val cardBody = aggregate?.groupValues?.get(3) ?: body
        val aggregateCount = aggregate?.let { it.groupValues[1].ifBlank { it.groupValues[2] }.toInt() } ?: 1
        val aggregateReason = if (aggregateCount > 1) {
            "合并通知含${aggregateCount}条，只能识别当前回执；需逐笔核对，不自动生成${aggregateCount}笔"
        } else ""

        if (packageName == WECHAT_PKG && TRANSFER_CARD_PREFIX.containsMatchIn(cardBody)) {
            if (PENDING_TRANSFER_CARD.containsMatchIn(cardBody)) {
                return Outcome.Skipped("转账尚未收款，暂不记账")
            }
            val returned = COMPLETED_RETURN_CARD.matchEntire(cardBody)
            if (returned != null) {
                val type = TYPE_INCOME.takeIf { INCOMING_RETURN.matches(returned.groupValues[1]) }
                return Outcome.NeedsConfirmation(TransferHint(
                    contactName(rawTitle), all, type, validHintAmount(cardBody),
                    listOf("转账退回回执，需核对原账单，避免重复记账", aggregateReason)
                        .filter { it.isNotEmpty() }.joinToString("；"), aggregateCount
                ))
            }
            val receipt = COMPLETED_TRANSFER_CARD.matchEntire(cardBody)
            if (receipt != null) {
                val status = receipt.groupValues[1]
                val type = TYPE_EXPENSE.takeIf { status in OUTGOING_TRANSFER_CARD_STATUSES }
                // 联系人标题和聊天回执无法认证完整交易，只生成草稿，绝不走自动入账。
                return Outcome.NeedsConfirmation(TransferHint(
                    contactName(rawTitle), all, type, validHintAmount(cardBody), aggregateReason, aggregateCount
                ))
            }
            return Outcome.Skipped("转账聊天卡片未提供明确的完成回执")
        }

        val returned = COMPLETED_RETURN.find(all)
        if (returned != null && (isBank || isServiceTitle(packageName, rawTitle) ||
                Regex("""^(?:退款已到账|退款到账|退款成功|转账已退回|已原路退回)(?:$|[\s：:¥￥\d])""")
                    .containsMatchIn(rawTitle))) {
            if (RETURN_NOT_COMPLETED.containsMatchIn(all)) {
                return Outcome.Skipped("退款或退回尚未完成，暂不记账")
            }
            val type = TYPE_INCOME.takeIf { INCOMING_RETURN.containsMatchIn(all) }
            val merchant = if (isBank) "" else extractMerchant(rawTitle, all, type ?: TYPE_INCOME)
            return Outcome.NeedsConfirmation(TransferHint(
                merchant, all, type, validHintAmount(all), "退款或退回需核对原账单，确认后再入账"
            ))
        }

        if (INCOMPLETE_TRANSACTION.containsMatchIn(all)) {
            return Outcome.Skipped("交易未完成或已退回，不自动记账")
        }

        // 微信聊天里的「【转账】请收款」「【动画表情】」是联系人发来的消息卡片，
        // 不是支付结果：标题是联系人名字、正文以【】开头。这类即便带了金额也不该记账。
        if (!isBank) {
            val titleIsService = isServiceTitle(packageName, rawTitle)
            if (body.trimStart().startsWith("【") && !titleIsService) {
                return Outcome.Skipped("微信聊天消息，不是支付通知")
            }
            // 系统有时只用「微信」作标题，此时同时要求结果状态和交易字段。
            // 联系人消息即使自称付款成功，也不能凭正文中的几个关键词入账。
            val genericWechatResult = rawTitle == "微信" &&
                STRUCTURED_RESULT_BODY.containsMatchIn(body) && PARTY_FIELD.containsMatchIn(body)
            if (packageName == WECHAT_PKG && !titleIsService && !genericWechatResult &&
                !PAYMENT_RESULT_TITLE.containsMatchIn(rawTitle)) {
                return Outcome.Skipped("非支付服务通知，无法确认交易")
            }
            // 别人发起、你还没点的转账：钱没到账，提前记会记成收入
            if (all.contains("请收款") || all.contains("待收款")) {
                return Outcome.Skipped("转账还未收款")
            }
            if (all.contains("转账") && !TRANSFER_COMPLETED.containsMatchIn(all)) {
                return Outcome.Skipped("转账尚无完成凭证，不自动记账")
            }
        }

        val amount = extractAmount(all) ?: return Outcome.Skipped("未找到明确的交易金额")
        if (amount <= 0.0 || amount >= 1_000_000.0) return Outcome.Skipped("金额超出合理范围")

        // 品牌名清洗后判方向
        var dirText = all
        WECHAT_SERVICE_NAMES.forEach { dirText = dirText.replace(it, " ") }
        BRAND_WORDS.forEach { dirText = dirText.replace(it, " ") }
        // 「收款方」是付款凭证中的字段，不能把它当成收入。
        dirText = dirText.replace(PARTY_FIELD, " ")
        val recipientAccepted = RECIPIENT_ACCEPTED.containsMatchIn(dirText)
        dirText = dirText.replace(RECIPIENT_ACCEPTED, " ")
        dirText = dirText.replace(Regex("(?:收款|付款)(?:方|人|账户|商户|单位)"), " ")

        val incomeList = if (isBank) BANK_INCOME_PHRASES else INCOME_PHRASES
        val expenseList = if (isBank) BANK_EXPENSE_PHRASES else EXPENSE_PHRASES
        val incomeHit = incomeList.firstOrNull { dirText.contains(it) }
        // 「向你付款」自身包含「付款」，去掉收入短语后再找独立的支出证据。
        var expenseText = dirText
        incomeList.forEach { expenseText = expenseText.replace(it, " ") }
        val expenseHit = expenseList.firstOrNull { expenseText.contains(it) }
            ?: "对方已收款".takeIf { recipientAccepted }

        val type = when {
            // 红包 / 转账方向由短语决定，单独的词不算数
            (all.contains("红包") || all.contains("转账")) && incomeHit == null && expenseHit == null ->
                return Outcome.Skipped("红包/转账方向不明")

            incomeHit != null && expenseHit != null ->
                return Outcome.Skipped("通知同时包含收支信息，需手工确认")
            incomeHit != null -> TYPE_INCOME
            expenseHit != null -> TYPE_EXPENSE
            else -> return Outcome.Skipped("判不出收支方向")
        }

        // 银行推送没有商户，硬猜只会把账记歪，留空交给用户在确认时补
        val merchant = if (isBank) "" else extractMerchant(rawTitle, all, type)
        val accountHint = if (isBank) {
            TAIL_PATTERN.find(all)?.groupValues?.getOrNull(1).orEmpty()
        } else ""
        return Outcome.Pay(
            ParsedPay(
                amount = amount,
                type = type,
                merchant = merchant,
                channel = channel,
                payType = payType,
                originalText = all,
                accountHint = accountHint
            )
        )
    }

    private fun contactName(title: String): String = title.takeIf {
        it != "微信" && it !in WECHAT_SERVICE_NAMES
    }.orEmpty().take(30)

    private fun validHintAmount(text: String): Double? = extractAmount(text)
        ?.takeIf { it > 0.0 && it < 1_000_000.0 }

    private fun isServiceTitle(packageName: String, title: String): Boolean =
        if (packageName == WECHAT_PKG) {
            WECHAT_SERVICE_NAMES.any { service ->
                title == service || title.startsWith("$service-") || title.startsWith("$service－") ||
                    title.startsWith("$service—") || title.startsWith("$service：") || title.startsWith("$service:")
            }
        } else packageName == ALIPAY_PKG && (title == "支付宝" || title.startsWith("支付宝："))

    private fun extractAmount(all: String): Double? {
        data class Candidate(val value: Double?, val score: Int)

        val candidates = MONEY_TOKEN.findAll(all).mapNotNull { match ->
            // 同一句里最近的金额标签决定其用途，余额和优惠不能参与记账。
            val clauseStart = all.lastIndexOfAny(charArrayOf('\n', '，', ',', '。', ';', '；'),
                match.range.first - 1) + 1
            val prefix = all.substring(clauseStart, match.range.first).takeLast(32)
            val transactionLabel = AMOUNT_LABEL.findAll(prefix).lastOrNull()
            val nonTransactionLabel = NON_TRANSACTION_LABEL.findAll(prefix).lastOrNull()
            if (nonTransactionLabel != null &&
                (transactionLabel == null || nonTransactionLabel.range.first > transactionLabel.range.first)
            ) return@mapNotNull null

            val token = match.groupValues[1].ifBlank { match.groupValues[2] }.trimEnd(',')
            val value = if (MONEY_FORMAT.matches(token)) token.replace(",", "").toDoubleOrNull() else null
            val score = if (transactionLabel == null) 0 else 1
            Candidate(value, score)
        }.toList()
        if (candidates.isEmpty()) return null
        val bestScore = candidates.maxOf { it.score }
        val best = candidates.filter { it.score == bestScore }
        // 不能从一条包含多笔金额或损坏金额的通知中随便取第一个。
        if (best.any { it.value == null }) return null
        return best.mapNotNull { it.value }.distinct().singleOrNull()
    }

    private fun extractMerchant(title: String, all: String, type: Int): String {
        val counterparty = if (type == TYPE_INCOME) "付款" else "收款"
        val partyPattern = Regex("""$counterparty(?:方|人|账户|商户|单位)\s*[:：]\s*([^\n,，。;；]+)""")
        val patterns = listOf(partyPattern) + MERCHANT_PATTERNS.filterNot {
            it == MERCHANT_PATTERNS.first() && type == TYPE_INCOME
        }
        for (pattern in patterns) {
            val m = pattern.find(all) ?: continue
            val candidate = m.groupValues[1]
                .replace("微信支付", "")
                .replace("支付宝", "")
                .trim(' ', '-', '－', '—', ':', '：', '，', ',', '。')
            if (validMerchant(candidate)) {
                return candidate
            }
        }
        // 只从明确带品牌前缀的标题兜底，联系人名不能成为自动入账依据。
        if (!BRAND_WORDS.any { title.startsWith(it) }) return ""
        val fallback = title
            .replace("微信支付", "")
            .replace("支付宝", "")
            .replace(Regex("""\d+(\.\d+)?\s*元?"""), "")
            .replace(Regex("""(收款|付款|支付|成功|到账|通知|凭证|消费)"""), "")
            .trim(' ', '-', '－', '—', ':', '：', '，', ',', '。')
        return if (validMerchant(fallback)) fallback else ""
    }

    private fun validMerchant(value: String): Boolean =
        value.length in 2..30 && value !in setOf("你", "您", "对方", "微信", "通知") &&
            !Regex("元|[¥￥]|收款|付款|支付|到账|转账|凭证|成功|提醒|交易金额").containsMatchIn(value)
}
