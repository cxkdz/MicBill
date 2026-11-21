package com.billbook.app.autobill

/** Pure rules for incomplete transfer receipts and updates to the same notification. */
object AutoBillDraftPolicy {
    fun sameNotification(
        event: AutoBillStore.Event, key: String, time: Long, pkg: String
    ): Boolean = event.status != AutoBillStore.STATUS_LOG && key.isNotBlank() &&
        event.notificationKey == key && event.postTime == time && event.packageName == pkg

    /** Enrichment preserves the draft identity and never reopens a handled event. */
    fun enrich(
        event: AutoBillStore.Event, outcome: AutoBillParser.Outcome, title: String, body: String
    ): AutoBillStore.Event {
        if (event.status != AutoBillStore.STATUS_PENDING ||
            (!event.needsAmount && !event.needsDirection)) return event
        return when (outcome) {
            is AutoBillParser.Outcome.Pay -> {
                val pay = outcome.value
                event.copy(
                    title = title, body = body,
                    amount = pay.amount, type = pay.type,
                    merchant = pay.merchant.ifBlank { event.merchant },
                    payType = pay.payType.ifBlank { event.payType },
                    needsAmount = false, needsDirection = false,
                    note = "待确认 · 通知信息已补全，请核对后入账"
                )
            }
            is AutoBillParser.Outcome.NeedsConfirmation -> {
                val hint = outcome.value
                val needsAmount = event.needsAmount && hint.amount == null
                val needsDirection = event.needsDirection && hint.type == null
                event.copy(
                    title = title, body = body,
                    amount = hint.amount ?: event.amount,
                    type = hint.type ?: event.type,
                    merchant = hint.merchant.ifBlank { event.merchant },
                    needsAmount = needsAmount, needsDirection = needsDirection,
                    note = listOf(incompleteNote(needsAmount, needsDirection), hint.reason)
                        .filter { it.isNotBlank() }.joinToString("；")
                )
            }
            is AutoBillParser.Outcome.Skipped -> event
        }
    }

    fun canConfirm(
        event: AutoBillStore.Event, amountOverride: Double?, typeOverride: Int?
    ): Boolean {
        if (event.status != AutoBillStore.STATUS_PENDING ||
            (event.needsAmount && amountOverride == null) ||
            (event.needsDirection && typeOverride == null)) return false
        val amount = amountOverride ?: event.amount
        val type = typeOverride ?: event.type
        return AutoBillPolicy.validAmount(amount) &&
            type in AutoBillParser.TYPE_EXPENSE..AutoBillParser.TYPE_INCOME
    }

    private fun incompleteNote(needsAmount: Boolean, needsDirection: Boolean): String = when {
        needsAmount && needsDirection -> "待确认 · 转账回执未提供金额和明确方向，请补全"
        needsAmount -> "待补金额 · 转账回执未提供金额，请补全"
        needsDirection -> "待确认 · 转账回执需核对收支方向"
        else -> "待确认 · 通知信息已补全，请核对后入账"
    }
}
