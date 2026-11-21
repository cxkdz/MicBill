package com.billbook.app.autobill

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import com.billbook.app.R
import com.billbook.app.databinding.ActivityConfirmAutoBillBinding
import com.billbook.app.model.Bill
import com.billbook.app.util.ThemeSurfaces
import com.billbook.app.util.AppExecutors
import java.math.BigDecimal
import java.util.Locale

/**
 * 后台弹出的底部确认面板。
 *
 * 存在的理由：银行通知只有金额没有商户，直接入账会把账本写成一堆「其他」。
 * 让用户就地在付款页上面补一个名称，比事后翻「待确认」列表省一次跳转。
 *
 * 30 秒无人处理就自动收起，草稿仍保留在待确认里 —— 不会丢，也不会一直挡屏。
 * 不在锁屏时弹出：支付刚完成时用户往往已经锁屏，此时弹窗没有意义且容易被误触。
 */
class ConfirmAutoBillActivity : AppCompatActivity() {

    companion object {
        private const val EXTRA_DRAFT_ID = "draft_id"
        private const val EXTRA_REVIEW_PENDING = "review_pending"
        private const val AUTO_CLOSE_MS = 30_000L

        /** 正在显示的草稿 id，避免同一条被连续通知重复弹两次 */
        @Volatile
        var showingDraftId: String? = null
            private set

        fun intentFor(context: Context, draftId: String, reviewPending: Boolean = false): Intent =
            Intent(context, ConfirmAutoBillActivity::class.java)
                .setData(Uri.parse("billbook://autobill/$draftId/open"))
                .putExtra(EXTRA_DRAFT_ID, draftId)
                .putExtra(EXTRA_REVIEW_PENDING, reviewPending)
                .addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_NO_ANIMATION
                )
    }

    private lateinit var binding: ActivityConfirmAutoBillBinding
    private val handler = Handler(Looper.getMainLooper())
    private var draftId: String = ""
    private var finished = false
    private var saving = false
    private val queuedIds = linkedSetOf<String>()
    private val initialPendingIds = mutableSetOf<String>()
    private var reviewAllPending = false
    private var autoClosePaused = false
    private val refreshPending = object : Runnable {
        override fun run() {
            if (isFinishing || isDestroyed) return
            updatePendingStatus()
            handler.postDelayed(this, 2_000L)
        }
    }

    private val autoClose = Runnable { closeSilently() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityConfirmAutoBillBinding.inflate(layoutInflater)
        setContentView(binding.root)

        applySheetStyle()
        savedInstanceState?.getStringArrayList("queued_ids")?.let { queuedIds.addAll(it) }
        initialPendingIds.addAll(savedInstanceState?.getStringArrayList("initial_pending_ids")
            ?: AutoBillStore.pending(this).map { it.id })
        reviewAllPending = savedInstanceState?.getBoolean(EXTRA_REVIEW_PENDING)
            ?: intent.getBooleanExtra(EXTRA_REVIEW_PENDING, false)
        autoClosePaused = savedInstanceState != null || reviewAllPending
        val id = savedInstanceState?.getString(EXTRA_DRAFT_ID)
            ?: intent.getStringExtra(EXTRA_DRAFT_ID).orEmpty()
        if (!bindDraft(id)) {
            finish()
            return
        }
        binding.etOverlayAmount.doAfterTextChanged { updateAmountSummary() }
        binding.rgOverlayType.setOnCheckedChangeListener { _, _ -> updateAmountSummary() }
        binding.tvOverlayCountdown.setOnClickListener {
            reviewAllPending = true
            autoClosePaused = true
            handler.removeCallbacks(autoClose)
            updatePendingStatus()
        }
        updatePendingStatus()
        if (!autoClosePaused) handler.postDelayed(autoClose, AUTO_CLOSE_MS)
    }

    /**
     * 面板是 singleInstance：连续两笔待确认时第二笔不会新建实例，而是走这里。
     * 不处理就会「第二笔被吞掉，面板还停在第一笔」。
     */
    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        val id = intent?.getStringExtra(EXTRA_DRAFT_ID).orEmpty()
        if (intent?.getBooleanExtra(EXTRA_REVIEW_PENDING, false) == true) {
            reviewAllPending = true
            autoClosePaused = true
            handler.removeCallbacks(autoClose)
            updatePendingStatus()
        }
        if (id.isBlank() || id == draftId) return
        if (AutoBillStore.pending(this).any { it.id == id }) {
            queuedIds.add(id)
            updatePendingStatus()
            android.widget.Toast.makeText(this, "还有一笔待确认，处理当前账单后显示", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(EXTRA_DRAFT_ID, draftId)
        outState.putStringArrayList("queued_ids", ArrayList(queuedIds))
        outState.putStringArrayList("initial_pending_ids", ArrayList(initialPendingIds))
        outState.putBoolean(EXTRA_REVIEW_PENDING, reviewAllPending)
        super.onSaveInstanceState(outState)
    }

    override fun onResume() {
        super.onResume()
        handler.removeCallbacks(refreshPending)
        handler.post(refreshPending)
        if (!::binding.isInitialized || draftId.isBlank() || saving || finished) return
        val latest = AutoBillStore.pending(this).firstOrNull { it.id == draftId }
        if (latest == null) {
            closeSilently()
            return
        }
        // 从转账详情返回时补上新识别到的信息，保留用户已经输入的内容。
        if (binding.etOverlayAmount.text.isNullOrBlank() && !latest.needsAmount) {
            binding.etOverlayAmount.setText(String.format(Locale.US, "%.2f", latest.amount))
        }
        if (binding.rgOverlayType.checkedRadioButtonId == -1 && !latest.needsDirection) {
            binding.rgOverlayType.check(if (latest.type == Bill.TYPE_EXPENSE) R.id.rbOverlayExpense else R.id.rbOverlayIncome)
        }
        if (binding.etOverlayTitle.text.isNullOrBlank() && latest.merchant.isNotBlank()) {
            binding.etOverlayTitle.setText(latest.merchant)
        }
        updateAmountSummary()
    }

    override fun onPause() {
        handler.removeCallbacks(refreshPending)
        super.onPause()
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        // 开始核对/输入后停止倒计时，避免用户写到一半被关闭。
        if (event.actionMasked == MotionEvent.ACTION_DOWN && ::binding.isInitialized) {
            handler.removeCallbacks(autoClose)
            autoClosePaused = true
            updatePendingStatus()
        }
        return super.dispatchTouchEvent(event)
    }

    /** @return false 表示这条草稿已不存在/已被处理 */
    private fun bindDraft(id: String): Boolean {
        val event = AutoBillStore.loadAll(this)
            .firstOrNull { it.id == id && it.status == AutoBillStore.STATUS_PENDING }
            ?: return false
        draftId = id
        showingDraftId = id
        bind(event)
        return true
    }

    override fun onDestroy() {
        handler.removeCallbacks(autoClose)
        handler.removeCallbacks(refreshPending)
        if (showingDraftId == draftId) showingDraftId = null
        super.onDestroy()
    }

    /** 卡片贴底、只有顶部两角圆角，遮罩盖在整屏上 —— 这样看起来是「从底部滑出」而不是一张浮动卡片 */
    private fun applySheetStyle() {
        val density = resources.displayMetrics.density
        val radius = 22f * density
        val surface = ThemeSurfaces.surface(this, 0f).color
        binding.sheet.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadii = floatArrayOf(radius, radius, radius, radius, 0f, 0f, 0f, 0f)
            setColor(surface)
        }
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
    }

    private fun bind(event: AutoBillStore.Event) {
        binding.etOverlayAmount.error = null
        binding.etOverlayAmount.setText(if (event.needsAmount) "" else String.format(Locale.US, "%.2f", event.amount))
        if (event.needsDirection) binding.rgOverlayType.clearCheck()
        else binding.rgOverlayType.check(if (event.type == Bill.TYPE_EXPENSE) R.id.rbOverlayExpense else R.id.rbOverlayIncome)
        updateAmountSummary()

        val channel = AutoBillParser.channelLabel(event.packageName)
        binding.tvOverlayChannel.text = if (event.note.contains("尾号")) {
            "$channel · 尾号${event.note.substringAfter("尾号").trim()}"
        } else {
            "${channel} · ${event.payType}"
        }

        binding.tvOverlayOriginal.text = listOf(event.title, event.body)
            .filter { it.isNotBlank() }.joinToString("  /  ")
        binding.tvOverlayHint.text = if (event.needsAmount || event.needsDirection) {
            "${event.note}\n微信通知信息不完整，可打开转账详情识别，或在这里补全后记入"
        } else "${event.note}\n核对金额和收支，填名称可自动归类"

        binding.etOverlayTitle.setText(event.merchant)
        binding.etOverlayTitle.setSelection(binding.etOverlayTitle.text.length)
        binding.etOverlayTitle.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                confirm(); true
            } else false
        }

        binding.btnOverlayConfirm.setOnClickListener { confirm() }
        binding.btnOverlayIgnore.setOnClickListener {
            if (saving) return@setOnClickListener
            val idToIgnore = draftId
            val appContext = applicationContext
            setBusy(true)
            AppExecutors.io {
                val ok = runCatching { AutoBillStore.setStatus(appContext, idToIgnore, AutoBillStore.STATUS_IGNORED) }.isSuccess
                AppExecutors.main {
                    if (isDestroyed || isFinishing) return@main
                    setBusy(false)
                    if (ok) showNextOrClose()
                    else android.widget.Toast.makeText(this, "忽略失败，草稿已保留，请重试", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
        // 点遮罩等于「先不管」，草稿留在待确认里
        binding.root.setOnClickListener { closeSilently() }
        binding.sheet.setOnClickListener { /* 吞掉点击，避免穿透到遮罩 */ }
        updatePendingStatus()
    }

    private fun updateAmountSummary() {
        val amount = binding.etOverlayAmount.text.toString().toDoubleOrNull()
        val checkedType = binding.rgOverlayType.checkedRadioButtonId
        binding.tvOverlayAmount.text = when {
            amount == null || !amount.isFinite() || amount <= 0 -> "待补金额"
            checkedType == R.id.rbOverlayIncome -> "+¥%.2f".format(amount)
            checkedType == R.id.rbOverlayExpense -> "-¥%.2f".format(amount)
            else -> "¥%.2f · 待核对收支".format(amount)
        }
    }

    private fun confirm() {
        if (finished || saving) return
        val decimal = binding.etOverlayAmount.text.toString().trim().toBigDecimalOrNull()
        if (decimal == null || decimal <= BigDecimal.ZERO || decimal >= BigDecimal("1000000") ||
            decimal.stripTrailingZeros().scale() > 2) {
            binding.etOverlayAmount.error = "请输入大于 0、小于 100 万且最多两位小数的金额"
            return
        }
        val type = when (binding.rgOverlayType.checkedRadioButtonId) {
            R.id.rbOverlayIncome -> Bill.TYPE_INCOME
            R.id.rbOverlayExpense -> Bill.TYPE_EXPENSE
            else -> {
                android.widget.Toast.makeText(this, "请选择这笔转账是支出还是收入", android.widget.Toast.LENGTH_SHORT).show()
                return
            }
        }
        val title = binding.etOverlayTitle.text.toString().trim()
        val id = draftId
        setBusy(true)
        val appContext = applicationContext
        AppExecutors.io {
            val ok = runCatching { AutoBillStore.confirm(appContext, id, title, decimal.toDouble(), type) }.getOrDefault(false)
            val handled = ok || AutoBillStore.pending(appContext).none { it.id == id }
            AppExecutors.main {
                if (isDestroyed || isFinishing) return@main
                setBusy(false)
                android.widget.Toast.makeText(this,
                    if (ok) "已记入账本" else if (handled) "该草稿已处理" else "写入失败，草稿已保留，请重试",
                    android.widget.Toast.LENGTH_SHORT).show()
                if (handled) showNextOrClose()
            }
        }
    }

    private fun setBusy(busy: Boolean) {
        saving = busy
        handler.removeCallbacks(autoClose)
        binding.btnOverlayConfirm.isEnabled = !busy
        binding.btnOverlayIgnore.isEnabled = !busy
        binding.etOverlayAmount.isEnabled = !busy
        binding.etOverlayTitle.isEnabled = !busy
        binding.rbOverlayExpense.isEnabled = !busy
        binding.rbOverlayIncome.isEnabled = !busy
        binding.tvOverlayConfirmLabel.text = if (busy) "正在处理…" else "确认记入"
    }

    private fun showNextOrClose() {
        val pending = AutoBillStore.pending(this)
        val nextId = queuedIds.firstOrNull { id -> pending.any { it.id == id && id != draftId } }
            ?: pending.filter { it.id != draftId && (reviewAllPending || it.id !in initialPendingIds) }
                .minByOrNull { it.postTime }?.id
        if (nextId != null) {
            queuedIds.remove(nextId)
            if (!bindDraft(nextId)) {
                closeSilently()
                return
            }
            autoClosePaused = true
            handler.removeCallbacks(autoClose)
            updatePendingStatus()
        } else closeSilently()
    }

    private fun updatePendingStatus() {
        if (!::binding.isInitialized) return
        val remaining = AutoBillStore.pending(this).count { it.id != draftId }
        binding.tvOverlayCountdown.text = when {
            remaining > 0 && reviewAllPending -> "还有 $remaining 笔待确认，处理后显示下一笔"
            remaining > 0 -> "还有 $remaining 笔待确认 · 点此逐笔处理"
            autoClosePaused -> "稍后处理可点空白处，草稿会保留"
            else -> "30 秒后自动收进「待确认」"
        }
    }

    private fun closeSilently() {
        if (finished) return
        finished = true
        handler.removeCallbacks(autoClose)
        // 面板没有显示过时直接 finish，不留动画残影
        if (isFinishing) return
        finish()
        overridePendingTransition(0, R.anim.slide_out_bottom)
    }
}
