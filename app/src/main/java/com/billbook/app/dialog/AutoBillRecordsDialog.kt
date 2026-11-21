package com.billbook.app.dialog

import android.content.Context
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import com.billbook.app.R
import com.billbook.app.autobill.AutoBillStore
import com.billbook.app.autobill.AutoBillParser
import com.billbook.app.autobill.AutoBillOcrDiagnostics
import com.billbook.app.util.AnimeDialogHelper
import com.billbook.app.util.AppExecutors
import kotlin.math.roundToInt

/**
 * 档 0 的诊断面板 + 档 1 的草稿确认面板。
 *
 * 两者故意共用一个界面：档 0 要回答「通知里到底有什么」，
 * 档 1 要回答「这条要不要入账」，本来就是同一条数据流的两个视角。
 */
object AutoBillRecordsDialog {

    // 对话框是深色玻璃底，页面用的 @color/text_primary 在这里会变成深字压深底。
    private const val DLG_TITLE = 0xE0FFFFFF.toInt()
    private const val DLG_MSG = 0xB0FFFFFF.toInt()
    private const val DLG_HINT = 0x80FFFFFF.toInt()

    fun show(context: Context, onChanged: () -> Unit = {}) {
        val dp = context.resources.displayMetrics.density
        fun d(v: Float) = (v * dp).roundToInt()

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(d(20f), d(12f), d(20f), d(8f))
        }
        root.addView(TextView(context).apply {
            text = "微信零钱不会触发银行卡扣款通知。请确认「支付页面识别」已开启并连接，再进入付款完成页或已收款转账详情；页面识别生成的草稿需确认才入账。\n转账通知没提供金额时会保留为「待补金额」，可手动补全。若同时出现页面草稿，请核对后只确认一条。聊天消息、待收款请求不会记账。"
            setTextColor(DLG_MSG)
            textSize = 12f
        })

        val container = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        val scroll = ScrollView(context).apply {
            addView(container, ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ))
        }
        root.addView(scroll, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, (context.resources.displayMetrics.heightPixels * 0.4f).toInt()
        ).apply { topMargin = d(8f) })

        val dialog = AlertDialog.Builder(context)
            .setTitle("自动记账记录")
            .setView(root)
            .setPositiveButton("关闭", null)
            .setNeutralButton("清空已处理", null)
            .create()

        fun render() {
            container.removeAllViews()
            val records = AutoBillStore.loadAll(context)
            val pending = records.filter { it.status == AutoBillStore.STATUS_PENDING }
            // 列表里是历史记录，开关是当前状态：两者混在一起时用户会把旧记录当成本次设置的结果
            val draftOn = AutoBillStore.isEnabled(context)
            val overlayOn = android.provider.Settings.canDrawOverlays(context)
            val pageCaptureEnabled = com.billbook.app.autobill.AutoBillListenerHealth.isPageCaptureEnabled(context)
            val pageCaptureConnected = com.billbook.app.autobill.AutoBillAccessibilityService.isConnected
            val pageCaptureState = when {
                !pageCaptureEnabled -> "未开启"
                pageCaptureConnected -> "已开启并连接"
                else -> "已开启但未连接，请在系统设置关闭后重开"
            }
            val screenDiagnostic = AutoBillStore.lastScreenDiagnostic(context)
            val lastScreenText = if (screenDiagnostic == null) {
                "尚无记录，开启并连接后进入支付完成页查看"
            } else {
                "${AutoBillStore.formatTime(screenDiagnostic.time)} " +
                    "${AutoBillParser.channelLabel(screenDiagnostic.packageName)} · ${screenDiagnostic.reason}"
            }
            val lastWatched = AutoBillStore.lastWatched(context)
            val lastText = if (lastWatched == null) {
                "还没收到（微信/支付宝/银行都没推过）"
            } else {
                val mins = (System.currentTimeMillis() - lastWatched.second) / 60_000
                "%s %s（%d 分钟前）".format(
                    com.billbook.app.autobill.AutoBillParser.channelLabel(lastWatched.first),
                    android.text.format.DateFormat.format("HH:mm", lastWatched.second),
                    mins
                )
            }
            container.addView(TextView(context).apply {
                text = "待确认 ${pending.size} 笔\n当前设置：草稿生成${if (draftOn) "已开启" else "已关闭"}" +
                    " · 可信自动入账${if (AutoBillStore.isAutoSaveEnabled(context)) "已开启" else "已关闭"}" +
                    " · 后台弹窗${if (overlayOn) "已授权" else "未授权（走通知栏）"}" +
                    "\n通知监听：${if (com.billbook.app.autobill.PayNotificationListenerService.isConnected) "已连接" else "未连接"}" +
                    "\n支付页面识别：$pageCaptureState" +
                    "\n最近页面识别：$lastScreenText" +
                    "\n最近支付通知：$lastText" +
                    "\n服务最后连接：${AutoBillStore.formatTime(AutoBillStore.lastConnected(context))}"
                setTextColor(DLG_MSG)
                textSize = 12f
                setPadding(0, 0, 0, d(8f))
            })

            if (pending.isNotEmpty()) {
                container.addView(AnimeDialogHelper.createTag(context, "逐笔确认（${pending.size} 笔）").apply {
                    setOnClickListener {
                        val oldest = AutoBillStore.pending(context).minByOrNull { it.postTime }
                            ?: return@setOnClickListener
                        runCatching {
                            context.startActivity(com.billbook.app.autobill.ConfirmAutoBillActivity
                                .intentFor(context, oldest.id, reviewPending = true))
                        }.onSuccess { dialog.dismiss() }.onFailure {
                            android.widget.Toast.makeText(context, "确认面板打开失败，请重试", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                }, LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = d(10f) })
            }

            if (!pageCaptureEnabled || !pageCaptureConnected) {
                container.addView(AnimeDialogHelper.createTag(context,
                    if (pageCaptureEnabled) "重新连接支付页面识别" else "开启支付页面识别").apply {
                    setOnClickListener {
                        runCatching {
                            context.startActivity(android.content.Intent(
                                android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS
                            ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
                        }.onSuccess { dialog.dismiss() }.onFailure {
                            android.widget.Toast.makeText(context,
                                "请在系统无障碍设置中开启本应用的支付结果页面识别", android.widget.Toast.LENGTH_LONG).show()
                        }
                    }
                }, LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = d(10f) })
            }

            if (records.isEmpty()) {
                container.addView(TextView(context).apply {
                    text = "还没有捕获到支付通知。微信前台操作可能不发通知；可开启「支付页面识别」，再进入已完成付款/转账的结果页。下方示例只用于预览解析结果。"
                    setTextColor(DLG_MSG)
                    textSize = 13f
                    setPadding(0, d(16f), 0, d(16f))
                })
                return
            }
            records.forEach { event ->
                renderRow(context, container, event, ::render, onChanged) { id ->
                    dialog.dismiss()
                    runCatching {
                        context.startActivity(
                            com.billbook.app.autobill.ConfirmAutoBillActivity.intentFor(context, id)
                        )
                    }.onFailure {
                        android.widget.Toast.makeText(context, "面板打开失败，可在记录中重试", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        // 示例只调用解析器；横向滚动避免小屏幕挤掉诊断入口。
        val tools = AnimeDialogHelper.createTagRow(context,
            listOf("页面诊断", "付款示例", "转出示例", "收款示例", "缺金额转账", "银行示例", "来源诊断")) { tag ->
            when (tag) {
                "页面诊断" -> showPageDiagnostic(context)
                "付款示例" -> showTestInput(context, "微信支付-星巴克店 付款成功 18.00 元")
                "转出示例" -> showTestInput(context, "转账给张三 50.00 元，转账成功")
                "收款示例" -> showTestInput(context, "张三向你转账 50.00 元，已收钱")
                "缺金额转账" -> showTestInput(context, "[转账] 已被接收",
                    AutoBillParser.WECHAT_PKG, "家人（示例）")
                "银行示例" -> showTestInput(context,
                    "您尾号为8275的农行借记卡发生一笔支出36.50元", "com.android.bankabc")
                "来源诊断" -> showProbeDialog(context)
            }
        }
        root.addView(tools, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = d(8f) })

        dialog.setOnShowListener {
            AnimeDialogHelper.applyAnimeStyle(dialog)
            // 覆盖默认监听，避免点「清空已处理」直接关掉弹窗
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL)?.setOnClickListener {
                val button = dialog.getButton(AlertDialog.BUTTON_NEUTRAL)
                button.isEnabled = false
                AppExecutors.io {
                    val ok = runCatching { AutoBillStore.clearHandled(context.applicationContext) }.isSuccess
                    AppExecutors.main {
                        if (!dialog.isShowing) return@main
                        button.isEnabled = true
                        if (ok) { render(); onChanged() }
                        else android.widget.Toast.makeText(context, "清理失败，请重试", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
        dialog.show()
        render()
    }

    /** 仅由用户主动打开/复制的技术元数据，便于确认具体支付页面兼容性。 */
    @Suppress("DEPRECATION")
    private fun showPageDiagnostic(context: Context) {
        val version = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull().orEmpty().ifBlank { "未知" }
        val diagnostic = AutoBillStore.lastScreenDiagnostic(context)
        val snapshot = buildString {
            append("MicBill 页面识别诊断\n应用版本：$version")
            append("\nAndroid API：${android.os.Build.VERSION.SDK_INT}")
            append("\n自动记账：${if (AutoBillStore.isEnabled(context)) "已开启" else "已关闭"}")
            append("\n页面识别授权：${if (com.billbook.app.autobill.AutoBillListenerHealth.isPageCaptureEnabled(context)) "已开启" else "未开启"}")
            append("\n页面识别连接：${if (com.billbook.app.autobill.AutoBillAccessibilityService.isConnected) "已连接" else "未连接"}")
            append("\n通知使用权：${if (AutoBillStore.isListenerGranted(context)) "已授权" else "未授权"}")
            append("\n通知监听连接：${if (com.billbook.app.autobill.PayNotificationListenerService.isConnected) "已连接" else "未连接"}")
            if (diagnostic == null) {
                append("\n最近页面识别：无记录")
            } else {
                append("\n应用包名：${diagnostic.packageName.ifBlank { "未知" }}")
                append("\n页面类名：${diagnostic.activity.ifBlank { "未知" }}")
                append("\n识别时间：${AutoBillStore.formatTime(diagnostic.time)}")
                append("\n识别结果：${diagnostic.reason}")
                if (diagnostic.details.isNotBlank()) append("\n采集详情：${diagnostic.details}")
            }
        }
        val builder = AlertDialog.Builder(context)
            .setTitle("页面识别诊断")
            .setMessage("以下仅包含版本、连接状态和页面技术信息，不含聊天正文、金额或卡号。\n\n$snapshot")
            .setNegativeButton("关闭", null)
            .setPositiveButton("复制页面诊断") { _, _ ->
                val copied = runCatching {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE)
                        as? android.content.ClipboardManager ?: error("Clipboard unavailable")
                    clipboard.setPrimaryClip(android.content.ClipData.newPlainText("页面识别诊断", snapshot))
                }.isSuccess
                android.widget.Toast.makeText(context,
                    if (copied) "页面诊断已复制" else "复制失败，请重试", android.widget.Toast.LENGTH_SHORT).show()
            }
        if (AutoBillOcrDiagnostics.latest() != null) {
            builder.setNeutralButton("查看本次识图文字") { _, _ -> showOcrText(context) }
        }
        val dialog = builder.create()
        dialog.setOnShowListener { AnimeDialogHelper.applyAnimeStyle(dialog) }
        dialog.show()
    }

    /** OCR content is shown or copied only after the user explicitly requests it. */
    private fun showOcrText(context: Context) {
        val snapshot = AutoBillOcrDiagnostics.latest()
        if (snapshot == null) {
            android.widget.Toast.makeText(context, "本次识图文字已过期，请重新打开转账详情后查看",
                android.widget.Toast.LENGTH_LONG).show()
            return
        }
        val text = buildString {
            append("MicBill 本次识图文字")
            append("\n识别时间：" + AutoBillStore.formatTime(snapshot.time))
            append("\n识别结果：" + snapshot.reason)
            append("\n\n")
            snapshot.texts.forEachIndexed { index, line -> append((index + 1).toString() + ". " + line + "\n") }
            if (snapshot.texts.isEmpty()) append("（本次未识别到文字）")
        }
        val density = context.resources.displayMetrics.density
        fun dp(value: Int) = (density * value).roundToInt()
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(12), dp(20), dp(8))
        }
        root.addView(TextView(context).apply {
            this.text = "以下是本次支付窗口的识图结果，可能含金额或对方姓名。仅临时保留在内存中，5 分钟后过期，不保存、不上传。复制页面诊断不会包含这些文字；只有点击下方「复制识图文字」才会复制本页内容。"
            setTextColor(DLG_MSG)
            textSize = 12f
        })
        root.addView(ScrollView(context).apply {
            addView(TextView(context).apply {
                this.text = text
                setTextColor(DLG_MSG)
                textSize = 13f
                setTextIsSelectable(true)
                setPadding(0, dp(12), 0, dp(8))
            }, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            (context.resources.displayMetrics.heightPixels * 0.45f).toInt()))
        val dialog = AlertDialog.Builder(context)
            .setTitle("本次识图文字")
            .setView(root)
            .setNegativeButton("关闭", null)
            .setPositiveButton("复制识图文字") { _, _ ->
                val copied = runCatching {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE)
                        as? android.content.ClipboardManager ?: error("Clipboard unavailable")
                    clipboard.setPrimaryClip(android.content.ClipData.newPlainText("本次识图文字", text))
                }.isSuccess
                android.widget.Toast.makeText(context,
                    if (copied) "识图文字已复制" else "复制失败，请重试", android.widget.Toast.LENGTH_SHORT).show()
            }
            .create()
        dialog.setOnShowListener { AnimeDialogHelper.applyAnimeStyle(dialog) }
        dialog.show()
    }

    /** 来源诊断：按包名聚合「谁在推通知、推了什么标题」，不展示正文 */
    private fun showProbeDialog(context: Context) {
        val dp = context.resources.displayMetrics.density
        fun d(v: Float) = (v * dp).roundToInt()

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(d(20f), d(12f), d(20f), d(8f))
        }
        root.addView(TextView(context).apply {
            text = "只记录包名与标题，不记录正文。次数为近似值（同一应用 2 秒内只记一次）。"
            setTextColor(DLG_MSG)
            textSize = 12f
        })

        val container = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        root.addView(ScrollView(context).apply {
            addView(container, ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ))
        }, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, (context.resources.displayMetrics.heightPixels * 0.4f).toInt()
        ).apply { topMargin = d(8f) })

        fun render() {
            container.removeAllViews()
            val entries = AutoBillStore.probeEntries(context)
            if (entries.isEmpty()) {
                container.addView(TextView(context).apply {
                    text = "还没有数据。先打开设置里的「记录所有通知来源」，正常用一段时间再回来。"
                    setTextColor(DLG_MSG)
                    textSize = 13f
                    setPadding(0, d(16f), 0, d(16f))
                })
                return
            }
            entries.forEach { e ->
                val card = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    background = ContextCompat.getDrawable(context, R.drawable.bg_glass_card)
                    setPadding(d(12f), d(10f), d(12f), d(10f))
                }
                container.addView(card, LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = d(8f) })

                val friendly = friendlyName(e.packageName)
                card.addView(TextView(context).apply {
                    text = if (friendly.isEmpty()) e.packageName else "$friendly  ·  ${e.packageName}"
                    setTextColor(DLG_TITLE)
                    textSize = 13f
                })
                card.addView(TextView(context).apply {
                    text = "${e.count} 次   最近：${AutoBillStore.formatTime(e.lastTime)}"
                    setTextColor(DLG_MSG)
                    textSize = 12f
                    setPadding(0, d(4f), 0, 0)
                })
                if (e.lastTitle.isNotBlank()) {
                    card.addView(TextView(context).apply {
                        text = e.lastTitle
                        setTextColor(DLG_HINT)
                        textSize = 12f
                        setPadding(0, d(4f), 0, 0)
                    })
                }
            }
        }

        val dialog = AlertDialog.Builder(context)
            .setTitle("通知来源诊断")
            .setView(root)
            .setPositiveButton("关闭", null)
            .setNeutralButton("清空", null)
            .create()
        dialog.setOnShowListener {
            AnimeDialogHelper.applyAnimeStyle(dialog)
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL)?.setOnClickListener {
                val button = dialog.getButton(AlertDialog.BUTTON_NEUTRAL)
                button.isEnabled = false
                AppExecutors.io {
                    val ok = runCatching { AutoBillStore.clearProbe(context.applicationContext) }.isSuccess
                    AppExecutors.main {
                        if (!dialog.isShowing) return@main
                        button.isEnabled = true
                        if (ok) render()
                        else android.widget.Toast.makeText(context, "清理失败，请重试", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
        dialog.show()
        render()
    }

    private fun friendlyName(pkg: String): String = when (pkg) {
        "com.tencent.mm" -> "微信"
        "com.eg.android.AlipayGphone" -> "支付宝"
        "com.unionpay" -> "云闪付"
        "com.android.bankabc" -> "农行掌银"
        "com.icbc" -> "工行"
        "com.chinamworld.main" -> "建行"
        "cmb.pb" -> "招行"
        "com.bankcomm.Bankcomm" -> "交行"
        "com.android.mms" -> "短信"
        "com.coloros.sms" -> "短信"
        "com.tencent.mobileqq" -> "QQ"
        else -> ""
    }

    /** 手动喂一段通知文案给解析器，用于档 0 的规则验证；不产生真实支付数据 */
    private fun showTestInput(
        context: Context,
        presetText: String,
        packageName: String = AutoBillParser.WECHAT_PKG,
        titleOverride: String? = null
    ) {
        val input = AnimeDialogHelper.createEditText(
            context,
            "粘贴通知原文",
            presetText
        )
        // 先 create 再挂「解析」：否则回调里引用 dialog 本身会编译不过
        val dialog = AlertDialog.Builder(context)
            .setTitle("通知解析预览（不会记入账本）")
            .setView(input)
            .setNegativeButton("取消", null)
            .create()

        dialog.setButton(AlertDialog.BUTTON_POSITIVE, "解析") { _, _ ->
            val text = input.text.toString().trim()
            if (text.isNotBlank()) {
                // 按文案里出现的特征推断来源，便于在真机上复现银行通知
                val pkg = when {
                    text.contains("支付宝") -> com.billbook.app.autobill.AutoBillParser.ALIPAY_PKG
                    text.contains("银行卡") || text.contains("借记卡") || text.contains("尾号") ->
                        "com.android.bankabc"
                    else -> packageName
                }
                // 渠道简称「微信」不是服务通知标题，不能喂给来源校验。
                val serviceTitle = titleOverride ?: when (pkg) {
                    AutoBillParser.WECHAT_PKG -> "微信支付"
                    AutoBillParser.ALIPAY_PKG -> "支付宝"
                    else -> AutoBillParser.channelLabel(pkg)
                }
                val outcome = AutoBillParser.parse(pkg, serviceTitle, text, null)
                val message = when (outcome) {
                    is AutoBillParser.Outcome.Pay -> {
                        val p = outcome.value
                        val direction = if (p.type == com.billbook.app.model.Bill.TYPE_EXPENSE) "支出" else "收入"
                        "$direction ¥%.2f\n商户：${p.merchant.ifBlank { "未识别" }}\n渠道：${p.payType}".format(p.amount)
                    }
                    is AutoBillParser.Outcome.NeedsConfirmation -> {
                        val hint = outcome.value
                        val amount = hint.amount?.let { "¥%.2f".format(it) } ?: "待补金额（通知未提供）"
                        val direction = when (hint.type) {
                            com.billbook.app.model.Bill.TYPE_EXPENSE -> "支出"
                            com.billbook.app.model.Bill.TYPE_INCOME -> "收入"
                            else -> "待核对收支"
                        }
                        val aggregate = if (hint.aggregateCount > 1) {
                            "\n通知合并了 ${hint.aggregateCount} 条消息；这里只保留一条核对线索，不代表已生成相同数量的账单，请按实际交易逐笔核对。"
                        } else ""
                        val reason = hint.reason.takeIf { it.isNotBlank() }?.let { "\n提示：$it" }.orEmpty()
                        "转账回执待核对\n金额：$amount\n方向：$direction\n对方：${hint.merchant.ifBlank { "未识别" }}$reason$aggregate\n需打开转账详情识别，或补全信息后确认。"
                    }
                    is AutoBillParser.Outcome.Skipped -> friendlyNote(outcome.reason)
                }
                AlertDialog.Builder(context)
                    .setTitle("解析结果")
                    .setMessage(message + "\n\n本次仅预览，没有生成草稿或账单。")
                    .setPositiveButton("知道了", null)
                    .create().also { result ->
                        result.setOnShowListener { AnimeDialogHelper.applyAnimeStyle(result) }
                        result.show()
                    }
            }
        }
        // createEditText 用的是浅色文字，必须套上深色对话框底，否则白字压白底看不见
        dialog.setOnShowListener { AnimeDialogHelper.applyAnimeStyle(dialog) }
        dialog.show()
    }

    /** 把解析器的内部原因翻译成用户能判断下一步的话 */
    private fun friendlyNote(note: String): String = when {
        note.contains("非支付服务通知") -> "旧通知未通过支付来源校验；已完成的转账可重新识别"
        note.contains("未找到金额") -> "通知里没有金额，已跳过"
        note.contains("判不出收支方向") -> "判不出收支方向，已跳过"
        note.contains("红包/转账方向不明") -> "红包/转账方向不明，已跳过"
        note.contains("非监听包名") -> "非支付应用，已忽略"
        note.contains("通知无文本") -> "通知没有正文，已忽略"
        note.contains("退款通知") -> "退款通知，不自动记账"
        note.contains("重复通知") -> "重复通知，已跳过"
        else -> note.replace("自动记账未开启", "草稿生成未开启")
    }

    private fun renderRow(
        context: Context,
        parent: LinearLayout,
        event: AutoBillStore.Event,
        refresh: () -> Unit,
        onChanged: () -> Unit,
        onOpenPanel: (String) -> Unit
    ) {
        val dp = context.resources.displayMetrics.density
        fun d(v: Float) = (v * dp).roundToInt()

        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = ContextCompat.getDrawable(context, R.drawable.bg_glass_card)
            setPadding(d(12f), d(10f), d(12f), d(10f))
        }
        parent.addView(card, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = d(8f) })

        // 待确认的整条卡片可点：用户往往是「看到待确认却不知道该点哪里」，
        // 与其让他去猜底部那个小按钮，不如点哪儿都直接弹面板。
        if (event.status == AutoBillStore.STATUS_PENDING) {
            card.isClickable = true
            card.isFocusable = true
            card.setOnClickListener { onOpenPanel(event.id) }
        }

        val channel = com.billbook.app.autobill.AutoBillParser.channelLabel(event.packageName)
        val currentOutcome = if (event.status == AutoBillStore.STATUS_LOG) {
            AutoBillParser.parse(event.packageName, event.title, event.body, null)
        } else null
        card.addView(TextView(context).apply {
            // 存量记录（JSON）不迁移，渲染时统一措辞：原文案太像「功能坏了」，
            // 但多数情况是通知本身就没有可用信息。
            // 存量数据里可能有「note 写着待确认、status 已是忽略/已入账」的记录，按状态兜底
            val noteText = when {
                event.status == AutoBillStore.STATUS_PENDING -> event.note
                event.status == AutoBillStore.STATUS_IGNORED -> "已忽略"
                event.status == AutoBillStore.STATUS_CONFIRMED && event.note.startsWith("待确认") ->
                    "已入账"
                currentOutcome is AutoBillParser.Outcome.NeedsConfirmation ->
                    "已识别交易回执，可重新识别为草稿并核对"
                else -> friendlyNote(event.note)
            }
            text = "${AutoBillStore.formatTime(event.postTime)}   $channel   $noteText"
            setTextColor(
                when (event.status) {
                    AutoBillStore.STATUS_PENDING -> ContextCompat.getColor(context, R.color.expense_red)
                    AutoBillStore.STATUS_CONFIRMED -> ContextCompat.getColor(context, R.color.income_green)
                    else -> DLG_MSG
                }
            )
            textSize = 12f
        })
        card.addView(TextView(context).apply {
            // 这是通知原文的引用，不是按钮 —— 银行文案里的「详情请点击」指银行 App，这里点不了
            val raw = listOf(event.title, event.body).filter { it.isNotBlank() }.joinToString("  /  ")
            text = "原文：${raw.ifBlank { "(无文本)" }}"
            setTextColor(DLG_HINT)
            textSize = 12f
            setPadding(0, d(6f), 0, 0)
        })

        if (event.status != AutoBillStore.STATUS_PENDING) {
            // 旧版本未识别的真实通知可以重试；重试只生成草稿，仍要用户确认。
            if (event.status == AutoBillStore.STATUS_LOG &&
                event.notificationKey.contains("|${event.packageName}|") &&
                !event.note.contains("重复") &&
                (currentOutcome is AutoBillParser.Outcome.Pay || currentOutcome is AutoBillParser.Outcome.NeedsConfirmation)
            ) {
                card.addView(AnimeDialogHelper.createTag(context, "重新识别为草稿").apply {
                    setOnClickListener {
                        val button = this
                        button.isEnabled = false
                        AppExecutors.io {
                            val draft = runCatching {
                                AutoBillStore.retryAsDraft(context.applicationContext, event.id)
                            }.getOrNull()
                            AppExecutors.main {
                                button.isEnabled = true
                                if (draft != null) {
                                    refresh()
                                    onChanged()
                                    onOpenPanel(draft.id)
                                } else android.widget.Toast.makeText(context,
                                    "没有可恢复的草稿，请检查该记录是否已处理", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }, LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = d(8f) })
            }
            return
        }

        val sign = if (event.type == com.billbook.app.model.Bill.TYPE_EXPENSE) "-" else "+"
        card.addView(TextView(context).apply {
            val amountText = when {
                event.needsAmount -> "待补金额"
                event.needsDirection -> "¥%.2f · 待核对收支".format(event.amount)
                else -> "$sign¥%.2f".format(event.amount)
            }
            text = "解析：$amountText" +
                if (event.merchant.isNotEmpty()) {
                    "   商户：${event.merchant}"
                } else {
                    "   商户：未识别（填标题可归类）"
                }
            setTextColor(DLG_TITLE)
            textSize = 13f
            setPadding(0, d(8f), 0, 0)
        })

        if (event.needsAmount || event.needsDirection) {
            card.addView(AnimeDialogHelper.createTag(context, "补全金额/收支并确认").apply {
                setOnClickListener { onOpenPanel(event.id) }
            }, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                d(44f)
            ).apply { topMargin = d(8f) })
            return
        }

        val input = AnimeDialogHelper.createEditText(
            context, "填名称可自动归类（选填）", event.merchant
        )
        card.addView(input, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = d(8f) })

        val actions = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
        }
        actions.addView(TextView(context).apply {
            text = "忽略"
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(DLG_MSG)
            background = ContextCompat.getDrawable(context, R.drawable.bg_overlay_ghost)
            setOnClickListener {
                val button = this
                button.isEnabled = false
                AppExecutors.io {
                    val ok = runCatching { AutoBillStore.setStatus(context.applicationContext, event.id, AutoBillStore.STATUS_IGNORED) }.isSuccess
                    AppExecutors.main {
                        button.isEnabled = true
                        if (ok) { refresh(); onChanged() }
                        else android.widget.Toast.makeText(context, "忽略失败，草稿已保留，请重试", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }, LinearLayout.LayoutParams(0, d(44f), 1f).apply { marginEnd = d(8f) })

        // 主按钮：铺满剩余宽度。原来只是个 13sp 的小标签，在深色卡片里很容易被忽略。
        actions.addView(TextView(context).apply {
            text = "确认记入"
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(0xCC3D4A5C.toInt())
            background = ContextCompat.getDrawable(context, R.drawable.bg_overlay_primary)
            setOnClickListener {
                val button = this
                val title = input.text.toString().trim()
                button.isEnabled = false
                button.text = "正在记入…"
                AppExecutors.io {
                    val ok = runCatching { AutoBillStore.confirm(context.applicationContext, event.id, title) }.getOrDefault(false)
                    AppExecutors.main {
                        button.isEnabled = true
                        button.text = "确认记入"
                        android.widget.Toast.makeText(context,
                            if (ok) "已记入账本" else "写入失败，请重试",
                            android.widget.Toast.LENGTH_SHORT).show()
                        if (ok) { refresh(); onChanged() }
                    }
                }
            }
        }, LinearLayout.LayoutParams(0, d(44f), 1.6f))
        card.addView(actions, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = d(8f) })
    }
}
