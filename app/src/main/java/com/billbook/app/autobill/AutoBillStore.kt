package com.billbook.app.autobill

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import com.billbook.app.db.BillDao
import com.billbook.app.db.CategoryDao
import com.billbook.app.db.ImportConfigDao
import com.billbook.app.model.Bill
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 自动记账的本地状态：捕获诊断记录 + 待确认草稿。
 *
 * 刻意不写进 tb_bill：草稿不是账单，用户确认之前绝不污染账本。
 * 这也让最小方案不需要升 DB 版本，`source` / `importBatchId` 沿用现有列即可。
 *
 * 档 0 的验证价值在这里：无论自动记账开关是否打开，
 * 所有微信/支付宝通知原文都会留下一条记录，供用户判断渠道是否可用。
 */
object AutoBillStore {

    private const val PREFS = "autobill_prefs"
    private const val KEY_RECORDS = "records"
    private const val KEY_SEEN = "seen_keys"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_AUTO_SAVE = "auto_save"
    private const val KEY_SCREEN_OCR = "screen_ocr_enabled"
    private const val KEY_LAST_CONNECTED = "last_connected"
    private const val KEY_LAST_WATCHED = "last_watched_time"
    private const val KEY_LAST_WATCHED_PKG = "last_watched_pkg"
    private const val KEY_PROBE_ENABLED = "probe_enabled"
    private const val KEY_PROBE = "probe_pkgs"
    private const val KEY_SCREEN_DIAGNOSTIC = "screen_diagnostic"
    private const val MAX_RECORDS = 100
    private const val MAX_SEEN = 300
    private const val MAX_PROBE_PKGS = 80
    private const val PROBE_THROTTLE_MS = 2000L

    const val STATUS_LOG = "log"
    const val STATUS_PENDING = "pending"
    const val STATUS_CONFIRMED = "confirmed"
    const val STATUS_IGNORED = "ignored"

    data class Event(
        val id: String,
        val postTime: Long,
        val packageName: String,
        val title: String,
        val body: String,
        val status: String,
        val note: String,
        val amount: Double = 0.0,
        val type: Int = Bill.TYPE_EXPENSE,
        val merchant: String = "",
        val payType: String = "",
        val billTime: Long = 0L,
        val notificationKey: String = "",
        val captureSource: String = "notification",
        val needsAmount: Boolean = false,
        val needsDirection: Boolean = false
    )

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_ENABLED, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    fun isAutoSaveEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_AUTO_SAVE, false)

    fun setAutoSaveEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_AUTO_SAVE, enabled).apply()
    }

    /** Separate consent: screenshot recognition stays off until explicitly enabled. */
    fun isScreenOcrEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_SCREEN_OCR, false)

    fun setScreenOcrEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_SCREEN_OCR, enabled).apply()
    }

    /** 系统「通知使用权」是否已授予本应用 —— 这是唯一可靠的判据 */
    fun isListenerGranted(context: Context): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(context.applicationContext)
            .contains(context.applicationContext.packageName)

    fun markConnected(context: Context) {
        prefs(context).edit().putLong(KEY_LAST_CONNECTED, System.currentTimeMillis()).apply()
    }

    fun lastConnected(context: Context): Long = prefs(context).getLong(KEY_LAST_CONNECTED, 0L)

    data class ScreenDiagnostic(
        val packageName: String, val activity: String, val time: Long,
        val reason: String, val details: String = ""
    )

    /** Capture stage only: no view tree, chat text, account balance or credentials. */
    fun markScreenDiagnostic(context: Context, packageName: String, activity: String, reason: String, details: String = "") {
        val data = JSONObject().apply {
            put("pkg", packageName)
            put("activity", activity)
            put("time", System.currentTimeMillis())
            put("reason", reason)
            put("details", details)
        }
        prefs(context).edit().putString(KEY_SCREEN_DIAGNOSTIC, data.toString()).apply()
    }

    fun lastScreenDiagnostic(context: Context): ScreenDiagnostic? {
        val raw = prefs(context).getString(KEY_SCREEN_DIAGNOSTIC, null) ?: return null
        return runCatching {
            val data = JSONObject(raw)
            ScreenDiagnostic(data.optString("pkg"), data.optString("activity"), data.optLong("time"),
                data.optString("reason"), data.optString("details"))
        }.getOrNull()
    }

    /**
     * 最近一次收到「被监听包名」的通知（微信/支付宝/银行）。
     *
     * 用来区分两种完全不同的失败：通知压根没来（渠道没数据），还是来了但解析跳过。
     * 前者改代码没用，后者可以调规则。
     */
    fun markWatched(context: Context, packageName: String, time: Long) {
        prefs(context).edit()
            .putLong(KEY_LAST_WATCHED, time)
            .putString(KEY_LAST_WATCHED_PKG, packageName)
            .apply()
    }

    /** @return 包名 to 时间戳；从未收到过返回 null */
    fun lastWatched(context: Context): Pair<String, Long>? {
        val time = prefs(context).getLong(KEY_LAST_WATCHED, 0L)
        if (time <= 0L) return null
        return (prefs(context).getString(KEY_LAST_WATCHED_PKG, "").orEmpty()) to time
    }

    fun formatTime(millis: Long): String =
        if (millis <= 0L) "从未" else SimpleDateFormat("MM-dd HH:mm:ss", Locale.CHINA).format(Date(millis))

    // ==================== 包名诊断（档 0 扩展） ====================
    // 只记「包名 + 标题」，不记正文。用于回答：到底哪些 App 会推支付相关通知。

    data class ProbeEntry(
        val packageName: String,
        val count: Int,
        val lastTitle: String,
        val lastTime: Long
    )

    fun isProbeEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_PROBE_ENABLED, false)

    fun setProbeEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_PROBE_ENABLED, enabled).apply()
    }

    fun recordProbe(context: Context, packageName: String, title: String, postTime: Long) {
        if (!isProbeEnabled(context)) return
        try {
            val root = try {
                JSONObject(prefs(context).getString(KEY_PROBE, "{}") ?: "{}")
            } catch (_: Exception) {
                JSONObject()
            }
            val item = root.optJSONObject(packageName) ?: JSONObject()
            // 同一包名 2 秒内只落一次盘，避免聊天类应用高频通知把主线程拖住
            if (postTime - item.optLong("t") < PROBE_THROTTLE_MS) return

            item.put("n", item.optInt("n") + 1)
            item.put("t", postTime)
            item.put("title", title.take(60))
            root.put(packageName, item)

            if (root.length() > MAX_PROBE_PKGS) {
                val oldest = root.keys().asSequence()
                    .sortedBy { root.optJSONObject(it)?.optLong("t") ?: 0L }
                    .firstOrNull()
                if (oldest != null) root.remove(oldest)
            }
            prefs(context).edit().putString(KEY_PROBE, root.toString()).apply()
        } catch (_: Exception) {
        }
    }

    fun probeEntries(context: Context): List<ProbeEntry> = try {
        val root = JSONObject(prefs(context).getString(KEY_PROBE, "{}") ?: "{}")
        root.keys().asSequence().mapNotNull { pkg ->
            val item = root.optJSONObject(pkg) ?: return@mapNotNull null
            ProbeEntry(
                packageName = pkg,
                count = item.optInt("n"),
                lastTitle = item.optString("title"),
                lastTime = item.optLong("t")
            )
        }.sortedByDescending { it.lastTime }.toList()
    } catch (_: Exception) {
        emptyList()
    }

    fun clearProbe(context: Context) {
        prefs(context).edit().remove(KEY_PROBE).apply()
    }

    // ==================== 记录 ====================

    /**
     * 收到一条被监听的通知。返回本次是否新增了待确认草稿。
     * 捕获与解析在这里分界：将来换成无障碍读屏，只需换掉调用方，本方法不用改。
     */
    @Synchronized
    fun record(
        context: Context,
        notificationKey: String,
        postTime: Long,
        packageName: String,
        title: String?,
        text: String?,
        bigText: String?
    ): Event? {
        val rawTitle = title.orEmpty().trim()
        val rawBody = listOfNotNull(
            text?.trim()?.takeIf { it.isNotBlank() },
            bigText?.trim()?.takeIf { it.isNotBlank() && it != text?.trim() }
        ).distinct().joinToString(" ")
        // An updated notification can retain its key and post time while changing from
        // processing/failed to paid. Include content so the successful update is parsed.
        val id = shortHash("$notificationKey|$postTime|$rawTitle|$rawBody")
        val seen = loadSeen(context)
        if (seen.contains(id)) return null
        seen.add(id)
        while (seen.size > MAX_SEEN) seen.removeAt(0)

        val outcome = AutoBillParser.parse(packageName, title, text, bigText)
        val records = loadAll(context).toMutableList()
        val originIndex = records.indexOfFirst {
            AutoBillDraftPolicy.sameNotification(it, notificationKey, postTime, packageName)
        }
        if (originIndex >= 0) {
            val previous = records[originIndex]
            // Enrich only an update of the exact same notification. A conversation
            // notification key can be reused for independent later transfers.
            val upgraded = AutoBillDraftPolicy.enrich(previous, outcome, rawTitle, rawBody)
            records[originIndex] = upgraded
            saveAll(context, records, seen)
            return upgraded.takeIf { it != previous && it.status == STATUS_PENDING }
        }
        var newDraft: Event? = null
        val event = when (outcome) {
            is AutoBillParser.Outcome.Skipped -> Event(
                id = id, postTime = postTime, packageName = packageName,
                title = rawTitle, body = rawBody, status = STATUS_LOG,
                note = outcome.reason
            )

            is AutoBillParser.Outcome.NeedsConfirmation -> {
                val hint = outcome.value
                val enabled = isEnabled(context)
                val possibleDuplicate = hasRecentTransferHint(context, packageName, postTime)
                val draft = Event(
                    id = id, postTime = postTime, packageName = packageName,
                    title = rawTitle, body = rawBody,
                    status = if (enabled) STATUS_PENDING else STATUS_LOG,
                    note = if (!enabled) "交易回执，自动记账未开启；${hint.reason}"
                        else hintNote(hint) +
                            if (possibleDuplicate) "；可能与近期转账提醒重复，请核对" else "",
                    amount = hint.amount ?: 0.0, type = hint.type ?: Bill.TYPE_EXPENSE,
                    merchant = hint.merchant, payType = payTypeFor(packageName), billTime = postTime,
                    needsAmount = hint.amount == null, needsDirection = hint.type == null
                )
                if (enabled) newDraft = draft
                draft
            }

            is AutoBillParser.Outcome.Pay -> {
                val pay = outcome.value
                val duplicate = if (isEnabled(context)) {
                    duplicateMatch(context, pay, packageName, notificationKey, postTime)
                } else AutoBillPolicy.Match.NONE
                if (!isEnabled(context)) {
                    Event(
                        id = id, postTime = postTime, packageName = packageName,
                        title = rawTitle, body = rawBody, status = STATUS_LOG,
                        note = "已识别 ¥%.2f（自动记账未开启，仅记录）".format(pay.amount),
                        amount = pay.amount, type = pay.type, merchant = pay.merchant,
                        payType = pay.payType, billTime = postTime
                    )
                } else if (duplicate == AutoBillPolicy.Match.SAME_NOTIFICATION) {
                    Event(
                        id = id, postTime = postTime, packageName = packageName,
                        title = rawTitle, body = rawBody, status = STATUS_LOG,
                        note = "重复通知，已跳过",
                        amount = pay.amount, type = pay.type, merchant = pay.merchant,
                        payType = pay.payType, billTime = postTime
                    )
                } else {
                    // 有把握的直接入账，不打扰用户；拿不准的才生成待确认并弹给用户。
                    val autoBill = if (duplicate == AutoBillPolicy.Match.NONE &&
                        isAutoSaveEnabled(context) && isHighConfidence(context, packageName, pay)) {
                        insertAutoBill(
                            context, id, pay.amount, pay.type, pay.payType, postTime,
                            pay.merchant, pay.merchant
                        )
                    } else null

                    if (autoBill != null) {
                        Event(
                            id = id, postTime = postTime, packageName = packageName,
                            title = rawTitle, body = rawBody, status = STATUS_CONFIRMED,
                            note = "已自动入账 · ${autoBill.categoryName}",
                            amount = pay.amount, type = pay.type, merchant = pay.merchant,
                            payType = pay.payType, billTime = postTime
                        )
                    } else {
                        val note = when {
                            duplicate == AutoBillPolicy.Match.POSSIBLE_DUPLICATE ->
                                "待确认 · 可能与近期记录重复，请核对"
                            pay.accountHint.isNotEmpty() -> "待确认 · 尾号${pay.accountHint}"
                            pay.merchant.isEmpty() -> "待确认 · 需补名称"
                            !isAutoSaveEnabled(context) -> "待确认 · 请核对后入账"
                            else -> "待确认 · 未满足直接入账条件"
                        }
                        val draft = Event(
                            id = id, postTime = postTime, packageName = packageName,
                            title = rawTitle, body = rawBody, status = STATUS_PENDING,
                            note = note, amount = pay.amount, type = pay.type,
                            merchant = pay.merchant, payType = pay.payType, billTime = postTime
                        )
                        newDraft = draft
                        draft
                    }
                }
            }
        }

        val captured = event.copy(notificationKey = notificationKey)
        records.add(0, captured)
        trimRecords(records)
        // Persist the record and capture identity together; marking it seen before
        // saving the draft used to permanently lose the payment on a write failure.
        saveAll(context, records, seen)
        return newDraft?.let { captured }
    }

    private fun incompleteNote(needsAmount: Boolean, needsDirection: Boolean): String = when {
        needsAmount && needsDirection -> "待确认 · 交易回执未提供金额和明确方向，请补全"
        needsAmount -> "待补金额 · 通知未提供金额"
        needsDirection -> "待确认 · 交易回执需核对收支方向"
        else -> "待确认 · 交易回执，请核对后入账"
    }

    private fun payTypeFor(packageName: String): String = when (packageName) {
        AutoBillParser.WECHAT_PKG -> Bill.PAY_WECHAT
        AutoBillParser.ALIPAY_PKG -> Bill.PAY_ALIPAY
        else -> Bill.PAY_BANK
    }

    private fun hintNote(hint: AutoBillParser.TransferHint): String =
        listOf(incompleteNote(hint.amount == null, hint.type == null), hint.reason)
            .filter { it.isNotBlank() }.joinToString("；")

    private fun hasRecentTransferHint(context: Context, packageName: String, postTime: Long): Boolean =
        loadAll(context).any {
            it.status != STATUS_LOG && it.packageName == packageName &&
                kotlin.math.abs(it.postTime - postTime) <= AutoBillPolicy.CROSS_CHANNEL_WINDOW_MS
        }

    /** Foreground payment results always need review, including old detail pages. */
    @Synchronized
    fun recordScreen(
        context: Context, packageName: String, sessionStarted: Long,
        capture: AutoBillScreenParser.Capture
    ): Event? {
        if (!isEnabled(context) || packageName !in setOf(AutoBillParser.WECHAT_PKG, AutoBillParser.ALIPAY_PKG)) return null
        val pay = capture.pay
        if (!AutoBillPolicy.validAmount(pay.amount) || pay.type !in Bill.TYPE_EXPENSE..Bill.TYPE_INCOME) return null
        val identity = AutoBillScreenIdentity.identity(packageName, sessionStarted, capture)
        val id = shortHash("screen|$packageName|$identity")
        val seen = loadSeen(context)
        if (id in seen || loadAll(context).any { it.id == id }) return null
        if (BillDao(context).getAutoBillByMarker(Bill.SOURCE_AUTO_SCREEN, "screen:$id") != null) return null
        if (capture.kind == "payment" && capture.transactionId.isNotBlank()) {
            val legacyId = shortHash("screen|$packageName|transaction:${capture.transactionId}:${pay.type}")
            if (legacyId in seen || loadAll(context).any { it.id == legacyId } ||
                BillDao(context).getAutoBillByMarker(Bill.SOURCE_AUTO_SCREEN, "screen:$legacyId") != null) return null
        }
        val now = System.currentTimeMillis()
        val billTime = capture.transactionTime ?: now
        val duplicate = duplicateMatch(context, pay, packageName, "screen:$identity", billTime)
        val note = when {
            capture.kind == "refund" && capture.needsDirection -> "待确认 · 退款或退回，请选择收支并核对原账单"
            capture.kind == "refund" -> "待确认 · 退款或退回，请核对原账单后入账"
            duplicate != AutoBillPolicy.Match.NONE -> "待确认 · 页面识别，可能与近期记录重复，请核对"
            capture.transactionTime == null -> "待确认 · 页面识别，未获取交易时间，暂按识别时间保存"
            else -> "待确认 · 页面识别，请核对交易时间、金额和收支"
        }
        val methodNote = if (capture.captureMethod == "local_window_ocr") "本机识图 · $note" else note
        val event = Event(
            id, now, packageName, "支付结果页面", pay.originalText, STATUS_PENDING,
            methodNote + if (capture.kind == "refund" && duplicate != AutoBillPolicy.Match.NONE) "；可能与近期记录重复" else "",
            pay.amount, pay.type, pay.merchant, pay.payType, billTime,
            "screen:$identity", "screen", needsDirection = capture.needsDirection
        )
        val records = loadAll(context).toMutableList().apply { add(0, event) }
        trimRecords(records)
        seen.add(id)
        while (seen.size > MAX_SEEN) seen.removeAt(0)
        saveAll(context, records, seen)
        return event
    }

    /** Reparse a real captured notification into a draft; never auto-save diagnostics. */
    @Synchronized
    fun retryAsDraft(context: Context, id: String): Event? {
        val records = loadAll(context).toMutableList()
        val index = records.indexOfFirst { it.id == id }
        if (index < 0) return null
        val previous = records[index]
        if (previous.status == STATUS_PENDING) return previous
        if (previous.status != STATUS_LOG || previous.captureSource != "notification" ||
            !previous.notificationKey.contains("|${previous.packageName}|")) return null
        if (records.any { it.id != previous.id && it.status != STATUS_LOG &&
                it.notificationKey == previous.notificationKey && it.postTime == previous.postTime }) return null
        val draft = when (val outcome = AutoBillParser.parse(previous.packageName, previous.title, previous.body, null)) {
            is AutoBillParser.Outcome.Skipped -> return null
            is AutoBillParser.Outcome.NeedsConfirmation -> {
                val hint = outcome.value
                previous.copy(
                    status = STATUS_PENDING, amount = hint.amount ?: 0.0,
                    type = hint.type ?: Bill.TYPE_EXPENSE, merchant = hint.merchant,
                    payType = payTypeFor(previous.packageName), billTime = previous.postTime,
                    needsAmount = hint.amount == null, needsDirection = hint.type == null,
                    note = hintNote(hint) +
                        if (hasRecentTransferHint(context, previous.packageName, previous.postTime))
                            "；可能与近期转账提醒重复，请核对" else ""
                )
            }
            is AutoBillParser.Outcome.Pay -> {
                val pay = outcome.value
                val duplicate = duplicateMatch(context, pay, previous.packageName, previous.notificationKey, previous.postTime)
                if (duplicate == AutoBillPolicy.Match.SAME_NOTIFICATION) return null
                previous.copy(
                    status = STATUS_PENDING,
                    note = if (duplicate == AutoBillPolicy.Match.POSSIBLE_DUPLICATE) "待确认 · 重新识别，可能与近期记录重复，请核对"
                        else "待确认 · 重新识别，请核对后入账",
                    amount = pay.amount, type = pay.type, merchant = pay.merchant,
                    payType = pay.payType, billTime = previous.postTime,
                    needsAmount = false, needsDirection = false
                )
            }
        }
        records[index] = draft
        saveAll(context, records)
        return draft
    }

    /**
     * 是否有把握直接入账、不问用户。
     *
     * 判据刻意保守：必须有商户名、不是银行推送、且规则能匹配到「其他」以外的分类。
     * 银行通知天然没有商户，证据再强也不够，一律走确认。
     */
    fun isHighConfidence(
        context: Context,
        packageName: String,
        pay: AutoBillParser.ParsedPay
    ): Boolean {
        if (pay.merchant.isBlank()) return false
        if (AutoBillParser.BANK_PACKAGES.containsKey(packageName)) return false
        return try {
            val rules = ImportConfigDao(context).getAllEnabled()
            val category = CategoryMatcher.match(
                pay.merchant, rules, CategoryDao(context), pay.type
            )
            category != null && category.name != "其他"
        } catch (_: Exception) {
            false
        }
    }

    /**
     * 幂等写入一条账单，返回实际保存的账单；失败返回 null。
     *
     * 自动入账与用户确认共用这一处，保证两条路径的分类规则永远一致。
     */
    private fun insertAutoBill(
        context: Context,
        eventId: String,
        amount: Double,
        type: Int,
        payType: String,
        billTime: Long,
        searchText: String,
        remark: String,
        source: String = Bill.SOURCE_AUTO_NOTIFY
    ): Bill? {
        if (!AutoBillPolicy.validAmount(amount) || type !in Bill.TYPE_EXPENSE..Bill.TYPE_INCOME) return null
        return try {
            val rules = ImportConfigDao(context).getAllEnabled()
            val category = CategoryMatcher.match(searchText, rules, CategoryDao(context), type)
            val bill = Bill(
                money = amount,
                type = type,
                categoryId = category?.id ?: 0L,
                categoryName = category?.name ?: "其他",
                categoryIcon = category?.icon ?: "",
                categoryIconResId = category?.iconResId ?: 0,
                payType = payType.ifBlank { "其他" },
                billTime = if (billTime > 0) billTime else System.currentTimeMillis(),
                remark = remark,
                source = source,
                importBatchId = if (source == Bill.SOURCE_AUTO_SCREEN) "screen:$eventId" else "notify:$eventId"
            )
            BillDao(context).insertAutoBillIfAbsent(bill)
        } catch (e: Exception) {
            Log.w("AutoBill", "could not save automatic bill", e)
            null
        }
    }

    /**
     * 已处理历史最多保留 100 条；所有待确认草稿继续保留。
     */
    private fun trimRecords(records: MutableList<Event>) {
        val retained = AutoBillPolicy.retainHistory(records, MAX_RECORDS) { it.status == STATUS_PENDING }
        records.clear()
        records.addAll(retained)
    }

    private fun duplicateMatch(
        context: Context, pay: AutoBillParser.ParsedPay, packageName: String,
        notificationKey: String, postTime: Long
    ): AutoBillPolicy.Match {
        val current = AutoBillPolicy.Payment(
            pay.amount, pay.type, postTime, packageName, pay.merchant, notificationKey
        )
        val previousEvents = loadAll(context).filter { it.status != STATUS_LOG }
        // Older drafts used only key + postTime for their id and have no key field.
        if (previousEvents.any { it.notificationKey.isEmpty() &&
                it.id == shortHash("$notificationKey|$postTime") }) {
            return AutoBillPolicy.Match.SAME_NOTIFICATION
        }
        val matches = previousEvents.map { e ->
            AutoBillPolicy.compare(current, AutoBillPolicy.Payment(
                e.amount, e.type, if (e.captureSource == "screen") e.billTime else e.postTime,
                e.packageName, e.merchant, e.notificationKey
            ))
        }
        if (AutoBillPolicy.Match.SAME_NOTIFICATION in matches) return AutoBillPolicy.Match.SAME_NOTIFICATION
        if (AutoBillPolicy.Match.POSSIBLE_DUPLICATE in matches) return AutoBillPolicy.Match.POSSIBLE_DUPLICATE
        if (previousEvents.any { it.packageName == packageName && (it.needsAmount || it.needsDirection) &&
                kotlin.math.abs(it.postTime - postTime) <= AutoBillPolicy.CROSS_CHANNEL_WINDOW_MS }) {
            return AutoBillPolicy.Match.POSSIBLE_DUPLICATE
        }

        // Keep only automatic ledger entries in this check. Manual entries and
        // different merchants must never cause unrelated equal-value payments to vanish.
        return try {
            val window = AutoBillPolicy.CROSS_CHANNEL_WINDOW_MS
            val candidates = BillDao(context).getRecentAutoBills(
                pay.amount, pay.type, postTime - window, postTime + window
            )
            val possible = candidates.any { bill ->
                val previousPackage = when (bill.payType) {
                    Bill.PAY_WECHAT -> AutoBillParser.WECHAT_PKG
                    Bill.PAY_ALIPAY -> AutoBillParser.ALIPAY_PKG
                    else -> "bank"
                }
                AutoBillPolicy.compare(current, AutoBillPolicy.Payment(
                    bill.money, bill.type, bill.billTime, previousPackage, bill.remark
                )) != AutoBillPolicy.Match.NONE
            }
            if (possible) AutoBillPolicy.Match.POSSIBLE_DUPLICATE else AutoBillPolicy.Match.NONE
        } catch (e: Exception) {
            Log.w("AutoBill", "could not check duplicate candidates", e)
            // A failed duplicate check must not lead to silent automatic insertion.
            AutoBillPolicy.Match.POSSIBLE_DUPLICATE
        }
    }

    fun loadAll(context: Context): List<Event> {
        val raw = prefs(context).getString(KEY_RECORDS, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                Event(
                    id = o.optString("id"),
                    postTime = o.optLong("postTime"),
                    packageName = o.optString("pkg"),
                    title = o.optString("title"),
                    body = o.optString("body"),
                    status = o.optString("status"),
                    note = o.optString("note"),
                    amount = o.optDouble("amount"),
                    type = o.optInt("type"),
                    merchant = o.optString("merchant"),
                    payType = o.optString("payType"),
                    billTime = o.optLong("billTime"),
                    notificationKey = o.optString("notificationKey"),
                    captureSource = o.optString("captureSource", "notification"),
                    needsAmount = o.optBoolean("needsAmount", false),
                    needsDirection = o.optBoolean("needsDirection", false)
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun pending(context: Context): List<Event> =
        loadAll(context).filter { it.status == STATUS_PENDING }

    @Synchronized
    private fun markConfirmed(context: Context, id: String, bill: Bill) {
        val records = loadAll(context).toMutableList()
        val index = records.indexOfFirst { it.id == id }
        if (index < 0) return
        records[index] = records[index].copy(
            status = STATUS_CONFIRMED,
            note = "已入账 · ${bill.categoryName}",
            amount = bill.money, type = bill.type, merchant = bill.remark,
            payType = bill.payType, billTime = bill.billTime,
            needsAmount = false, needsDirection = false
        )
        saveAll(context, records)
        AutoBillNotifier.cancel(context, id)
    }

    @Synchronized
    fun setStatus(context: Context, id: String, status: String) {
        val records = loadAll(context).toMutableList()
        val index = records.indexOfFirst { it.id == id }
        if (index < 0) return
        val current = records[index]
        if (current.status != STATUS_PENDING || status != STATUS_IGNORED) return
        // 状态和文案必须一起改：只改 status 会留下「写着待确认、实际上已忽略」的记录，
        // 用户看到「待确认」却点不动，会以为是功能坏了。
        val note = when (status) {
            STATUS_IGNORED -> "已忽略"
            else -> current.note
        }
        records[index] = current.copy(status = status, note = note)
        saveAll(context, records)
        AutoBillNotifier.cancel(context, id)
    }

    /** 用户确认 → 按自动归类规则匹配分类 → 写入 tb_bill */
    @Synchronized
    fun confirm(
        context: Context, id: String, titleOverride: String,
        amountOverride: Double? = null, typeOverride: Int? = null
    ): Boolean {
        val event = loadAll(context).firstOrNull { it.id == id && it.status == STATUS_PENDING }
            ?: return false
        val title = titleOverride.trim()
        if (!AutoBillDraftPolicy.canConfirm(event, amountOverride, typeOverride)) return false
        val amount = amountOverride ?: event.amount
        val type = typeOverride ?: event.type
        // 用户补的名称优先当分类线索：银行通知没有商户，这是唯一的归类依据。
        // 不填就退回原商户/正文，匹配不到自然落到「其他」。
        val searchText = title.ifBlank { event.merchant }.ifBlank { event.body }
        val bill = insertAutoBill(
            context, id, amount, type,
            event.payType.ifBlank { "其他" }, event.billTime,
            searchText, title.ifBlank { event.merchant },
            if (event.captureSource == "screen") Bill.SOURCE_AUTO_SCREEN else Bill.SOURCE_AUTO_NOTIFY
        ) ?: return false
        markConfirmed(context, id, bill)
        return true
    }

    @Synchronized
    fun clearHandled(context: Context) {
        saveAll(context, loadAll(context).filter { it.status == STATUS_PENDING })
    }

    private fun saveAll(context: Context, records: List<Event>, seen: List<String>? = null) {
        val arr = JSONArray()
        records.forEach { e ->
            arr.put(JSONObject().apply {
                put("id", e.id)
                put("postTime", e.postTime)
                put("pkg", e.packageName)
                put("title", e.title)
                put("body", e.body)
                put("status", e.status)
                put("note", e.note)
                put("amount", e.amount)
                put("type", e.type)
                put("merchant", e.merchant)
                put("payType", e.payType)
                put("billTime", e.billTime)
                put("notificationKey", e.notificationKey)
                put("captureSource", e.captureSource)
                put("needsAmount", e.needsAmount)
                put("needsDirection", e.needsDirection)
            })
        }
        val editor = prefs(context).edit().putString(KEY_RECORDS, arr.toString())
        if (seen != null) editor.putString(KEY_SEEN, seen.joinToString("|"))
        check(editor.commit()) { "自动记账记录保存失败" }
    }

    private fun loadSeen(context: Context): MutableList<String> {
        val raw = prefs(context).getString(KEY_SEEN, "") ?: ""
        return if (raw.isEmpty()) mutableListOf()
        else raw.split('|').filter { it.isNotEmpty() }.toMutableList()
    }

    private fun shortHash(input: String): String = try {
        MessageDigest.getInstance("MD5").digest(input.toByteArray())
            .take(8).joinToString("") { "%02x".format(it) }
    } catch (_: Exception) {
        input.hashCode().toString()
    }
}
