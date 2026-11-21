package com.billbook.app.autobill

import java.text.Normalizer

/** Repairs one OCR image's layout and wallet explanation; never guesses amounts or completion states. */
object AutoBillOcrText {
    private val cjkGap = Regex("""(?<=[\p{IsHan},。;:!?、()])[\p{Z}\s]+(?=[\p{IsHan},。;:!?、()])""")
    private val separator = Regex("""[,。.;:!?、]""")
    private val statusCharacters = Regex("""[\p{IsHan},。.;:!?、]+""")
    private val guardedPrefixes = setOf(
        "未", "尚未", "没有", "并未", "待", "等待", "请", "请确认", "未确认", "未完成", "未到账",
        "是否", "如果", "例如", "示例", "如何", "为什么", "怎么", "待收款", "请收款", "确认收款"
    )
    private val canonical = buildMap {
        listOf(
            "转账成功", "支付成功", "付款成功", "已被接收", "转账已被接收", "对方已收款", "对方已收钱",
            "对方已接收", "收款方已收款", "收款人已收款", "收款成功", "收款到账", "已收款", "已收钱",
            "你已收款", "你已收钱", "已存入零钱", "已存入零钱通", "资金已存入零钱", "资金已存入零钱通",
            "退款到账", "退款已到账", "退款已原路退回", "已原路退回", "已退回零钱", "已退还至零钱",
            "已退回到零钱", "资金已退回零钱", "退款已退回零钱", "退款成功", "已退款", "已退回", "已退还",
            "转账已退回", "转账已退还", "你已退款", "对方已退款"
        ).forEach { put(it, it) }
        for (received in listOf("你已收款", "你已收钱", "已收款", "已收钱")) {
            for (deposit in listOf("已存入零钱", "已存入零钱通", "资金已存入零钱", "资金已存入零钱通")) {
                put(received + deposit, "$received,$deposit")
            }
            // Observed bundled-model output: the primary state was correct but
            // its explanatory wallet clause read "资金己存入零钱". Permit only
            // these glyph confusions after an exact, independently valid state;
            // never repair the transaction state itself or a bare deposit line.
            for (funds in listOf("", "资金")) {
                for (already in listOf("已", "己", "巳")) {
                    for (into in listOf("入", "人")) {
                        for (wallet in listOf("零钱", "零钱通")) {
                            put(received + funds + already + "存" + into + wallet,
                                "$received," + funds + "已存入" + wallet)
                        }
                    }
                }
            }
        }
        // In the actual receipt image the reverse also occurs: "你己收款" is
        // uncertain while the independent wallet-deposit state is exact. Keep
        // only that exact clause as evidence; do not invent a repaired primary.
        for (uncertainReceived in listOf("你己收款", "你巳收款", "你己收钱", "你巳收钱")) {
            for (exactDeposit in listOf("已存入零钱", "已存入零钱通", "资金已存入零钱", "资金已存入零钱通")) {
                put(uncertainReceived + exactDeposit, exactDeposit)
            }
        }
    }

    /**
     * Keeps amount-token spacing intact. Only whole known status phrases may be
     * reconstructed from up to six adjacent OCR fragments; fields, prose and
     * numbers form boundaries. This function must be called per image, never on
     * accumulated OCR from different frames.
     */
    fun normalize(texts: List<String>): List<String> {
        val lines = texts.flatMap { it.lineSequence().toList() }.map {
            cjkGap.replace(Normalizer.normalize(it, Normalizer.Form.NFKC).trim(), "")
        }.filter(String::isNotEmpty)
        val output = mutableListOf<String>()
        var index = 0
        while (index < lines.size) {
            val line = lines[index]
            val key = statusKey(line)
            // A separately recognized request/negation must stay attached to its
            // status. Never turn the following fragment into an accepted receipt.
            if (key in guardedPrefixes && index + 1 < lines.size) {
                val guardedStatus = matchKnown(lines, index + 1)
                if (guardedStatus != null) {
                    output.add(line + guardedStatus.first)
                    index += 1 + guardedStatus.second
                    continue
                }
            }
            val matched = matchKnown(lines, index)
            output.add(matched?.first ?: line)
            index += matched?.second ?: 1
        }
        return output
    }

    private fun matchKnown(lines: List<String>, index: Int): Pair<String, Int>? {
        val key = statusKey(lines[index]) ?: return null
        var combined = key
        var best = canonical[combined]?.let { it to 1 }
        if (canonical.keys.none { it.startsWith(key) }) return best
        for (nextIndex in index + 1 until minOf(lines.size, index + 6)) {
            val next = statusKey(lines[nextIndex]) ?: break
            if (next.isEmpty() || combined.length + next.length > 50) break
            combined += next
            if (canonical.keys.none { it.startsWith(combined) }) break
            canonical[combined]?.let { best = it to (nextIndex - index + 1) }
        }
        return best
    }

    private fun statusKey(line: String): String? = if (statusCharacters.matches(line)) {
        separator.replace(line, "").takeIf { it.isNotEmpty() }
    } else null
}
