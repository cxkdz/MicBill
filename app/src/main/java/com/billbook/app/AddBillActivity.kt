package com.billbook.app

import android.animation.ObjectAnimator
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.view.animation.DecelerateInterpolator
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.billbook.app.adapter.CategoryPagerAdapter
import com.billbook.app.animation.BubbleTipView
import com.billbook.app.animation.GlowButton
import com.billbook.app.databinding.ActivityAddBillBinding
import com.billbook.app.db.BillDao
import com.billbook.app.db.CategoryDao
import com.billbook.app.model.Bill
import com.billbook.app.util.BackgroundApplier
import com.billbook.app.util.ThemeManager
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class AddBillActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAddBillBinding
    private lateinit var billDao: BillDao
    private lateinit var categoryDao: CategoryDao
    private lateinit var themeManager: ThemeManager

    private var billType = Bill.TYPE_EXPENSE
    private var selectedCategoryId = 0L
    private var selectedCategoryName = ""
    private var selectedCategoryIcon = ""
    private var selectedPayType = Bill.PAY_WECHAT
    private var selectedTime = System.currentTimeMillis()
    private var editBillId = 0L

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA)

    private lateinit var payChips: List<TextView>

    override fun onCreate(savedInstanceState: Bundle?) {
        themeManager = ThemeManager(this)
        setTheme(R.style.Theme_BillBook)  // 主题样式已收敛为一套，配色由 ThemePalettes 负责
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        window.setBackgroundDrawableResource(android.R.color.transparent)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }

        binding = ActivityAddBillBinding.inflate(layoutInflater)
        setContentView(binding.root)
        com.billbook.app.util.ThemeSurfaces.apply(binding.root)

        // 顶部栏适配状态栏 insets
        ViewCompat.setOnApplyWindowInsetsListener(binding.topBar) { view, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.statusBars())
            view.updatePadding(top = insets.top)
            windowInsets
        }

        billDao = BillDao(this)
        categoryDao = CategoryDao(this)

        editBillId = intent.getLongExtra("bill_id", 0L)

        payChips = listOf(binding.chipWechat, binding.chipAlipay, binding.chipCash, binding.chipBank)

        setupBackButton()
        setupTitle()
        setupTypeSwitch()
        updateTypeSelection()
        setupCategoryGrid()
        setupAmountInput()
        setupPayType()
        setupTimePicker()
        setupRemarkTags()
        setupSaveButton()
        setupCancelButton()
        setupBackPress()

        if (editBillId > 0) {
            loadBillData()
        } else {
            updateTimeDisplay()
        }
    }

    // ==================== 顶部栏 ====================

    private fun setupBackButton() {
        binding.btnBack.setOnClickListener { playExitAnimation { finish() } }
    }

    private fun setupTitle() {
        binding.tvTitle.text = if (editBillId > 0) "编辑账单" else "记一笔"
    }

    private fun setupBackPress() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                playExitAnimation { finish() }
            }
        })
    }

    // ==================== 收支切换 ====================

    private fun setupTypeSwitch() {
        binding.chipExpense.setOnClickListener {
            billType = Bill.TYPE_EXPENSE
            selectedCategoryId = 0L
            selectedCategoryName = ""
            selectedCategoryIcon = ""
            updateTypeSelection()
            loadCategories()
        }
        binding.chipIncome.setOnClickListener {
            billType = Bill.TYPE_INCOME
            selectedCategoryId = 0L
            selectedCategoryName = ""
            selectedCategoryIcon = ""
            updateTypeSelection()
            loadCategories()
        }
    }

    private fun updateTypeSelection() {
        if (billType == Bill.TYPE_EXPENSE) {
            binding.chipExpense.background = com.billbook.app.util.ThemeSurfaces.chip(binding.chipExpense.context, true)
            binding.chipExpense.setTextColor(0xE0FFFFFF.toInt())
            binding.chipIncome.setBackgroundColor(Color.TRANSPARENT)
            binding.chipIncome.setTextColor(0x50FFFFFF.toInt())
        } else {
            binding.chipIncome.background = com.billbook.app.util.ThemeSurfaces.chip(binding.chipIncome.context, true)
            binding.chipIncome.setTextColor(0xE0FFFFFF.toInt())
            binding.chipExpense.setBackgroundColor(Color.TRANSPARENT)
            binding.chipExpense.setTextColor(0x50FFFFFF.toInt())
        }
    }

    // ==================== 分类选择 ====================

    override fun onResume() {
        super.onResume()
        // 从设置页面返回时重新加载分类
        loadCategories()
    }

    private fun setupCategoryGrid() {
        loadCategories()
    }

    private var categoryPagerAdapter: CategoryPagerAdapter? = null
    private var categoryPageCallback: androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback? = null

    private fun loadCategories() {
        val categories = categoryDao.getAll(billType)
        val pagerAdapter = CategoryPagerAdapter(categories) { category ->
            selectedCategoryId = category.id
            selectedCategoryName = category.name
            selectedCategoryIcon = category.icon
        }
        categoryPagerAdapter = pagerAdapter
        binding.vpCategories.adapter = pagerAdapter
        setupDotIndicator(categories.size)
        categoryPageCallback?.let { binding.vpCategories.unregisterOnPageChangeCallback(it) }
        categoryPageCallback = object : androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                updateDotIndicator(position)
            }
        }
        binding.vpCategories.registerOnPageChangeCallback(categoryPageCallback!!)
    }

    private val dots = mutableListOf<View>()

    private fun setupDotIndicator(totalItems: Int) {
        val pageCount = if (totalItems == 0) 0 else (totalItems + 11) / 12
        binding.layoutDotIndicator.removeAllViews()
        dots.clear()
        binding.layoutDotIndicator.visibility = View.VISIBLE
        val dp = resources.displayMetrics.density
        for (i in 0 until pageCount) {
            val dot = View(this).apply {
                layoutParams = LinearLayout.LayoutParams((8 * dp).toInt(), (8 * dp).toInt()).apply {
                    marginStart = if (i == 0) 0 else (6 * dp).toInt()
                }
                setBackgroundResource(if (i == 0) com.billbook.app.R.drawable.dot_active else com.billbook.app.R.drawable.dot_inactive)
            }
            dots.add(dot)
            binding.layoutDotIndicator.addView(dot)
        }
    }

    private fun updateDotIndicator(activeIndex: Int) {
        dots.forEachIndexed { i, dot ->
            dot.setBackgroundResource(if (i == activeIndex) com.billbook.app.R.drawable.dot_active else com.billbook.app.R.drawable.dot_inactive)
        }
    }

    // ==================== 金额输入 ====================

    private fun setupAmountInput() {
        // 输入提示气泡
        binding.bubbleInputHint.setTipText("点击输入金额")

        val triggerKeyboard = {
            binding.etMoney.requestFocus()
            val imm = getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            imm.showSoftInput(binding.etMoney, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
            // 输入后隐藏提示气泡
            binding.bubbleInputHint.animate().alpha(0f).setDuration(200).withEndAction {
                binding.bubbleInputHint.visibility = View.GONE
            }.start()
        }
        binding.tvAmountDisplay.setOnClickListener { triggerKeyboard() }
        binding.bubbleInputHint.setOnClickListener { triggerKeyboard() }

        // 金额输入监听：同步显示 + 更新气泡
        binding.etMoney.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                val text = s?.toString() ?: ""
                if (text.isNotEmpty()) {
                    binding.tvAmountDisplay.text = text
                    binding.tvAmountDisplay.setTextColor(0xE0FFFFFF.toInt())
                    val amount = text.toDoubleOrNull() ?: 0.0
                    binding.bubbleTip.updateForAmount(amount)
                } else {
                    binding.tvAmountDisplay.text = ""
                    binding.tvAmountDisplay.setTextColor(getColor(R.color.anime_text_hint))
                    binding.bubbleTip.updateForAmount(0.0)
                }
            }
        })
    }

    // ==================== 支付渠道 ====================

    private fun setupPayType() {
        binding.chipWechat.setOnClickListener { selectPayType(Bill.PAY_WECHAT, binding.chipWechat) }
        binding.chipAlipay.setOnClickListener { selectPayType(Bill.PAY_ALIPAY, binding.chipAlipay) }
        binding.chipCash.setOnClickListener { selectPayType(Bill.PAY_CASH, binding.chipCash) }
        binding.chipBank.setOnClickListener { selectPayType(Bill.PAY_BANK, binding.chipBank) }
    }

    private fun selectPayType(payType: String, selected: TextView) {
        selectedPayType = payType
        payChips.forEach { chip ->
            if (chip == selected) {
                chip.background = com.billbook.app.util.ThemeSurfaces.chip(chip.context, true)
                chip.setTextColor(0xE0FFFFFF.toInt())
            } else {
                chip.background = com.billbook.app.util.ThemeSurfaces.chip(chip.context, false)
                chip.setTextColor(0x80FFFFFF.toInt())
            }
        }
    }

    // ==================== 时间选择 ====================

    private fun setupTimePicker() {
        binding.tvTime.setOnClickListener {
            com.billbook.app.animation.AnimeDatePickerDialog(this, selectedTime) { year, month, day, hour, minute ->
                val cal = Calendar.getInstance()
                cal.set(year, month, day, hour, minute, 0)
                selectedTime = cal.timeInMillis
                updateTimeDisplay()
            }.show()
        }
    }

    private fun updateTimeDisplay() {
        binding.tvTime.text = dateFormat.format(selectedTime)
    }

    // ==================== 标题标签 ====================

    private fun setupRemarkTags() {
        val tags = listOf(binding.tag1, binding.tag2, binding.tag3, binding.tag4)
        tags.forEach { tag ->
            tag.setOnClickListener {
                // 点击标签填入标题
                binding.etRemark.setText(tag.text.toString())
                // 选中态动画
                tag.background = com.billbook.app.util.ThemeSurfaces.chip(tag.context, true)
                tag.setTextColor(0xE0FFFFFF.toInt())
                // 其他恢复
                tags.filter { it != tag }.forEach {
                    it.background = com.billbook.app.util.ThemeSurfaces.chip(it.context, false)
                    it.setTextColor(getColor(R.color.anime_text_secondary))
                }
            }
        }
    }

    // ==================== 保存/取消 ====================

    private fun setupSaveButton() {
        (binding.btnSave as GlowButton).setOnClickListener {
            val moneyStr = binding.etMoney.text.toString().trim()
            val money = moneyStr.toDoubleOrNull()

            if (money == null || money <= 0) {
                Toast.makeText(this, "请输入有效金额", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (selectedCategoryId == 0L) {
                Toast.makeText(this, "请选择分类", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // 弹簧动画后保存
            (binding.btnSave as GlowButton).playClickEffect {
                val remark = binding.etRemark.text.toString().trim()

                if (editBillId > 0) {
                    val bill = Bill(
                        id = editBillId,
                        money = money,
                        type = billType,
                        categoryId = selectedCategoryId,
                        categoryName = selectedCategoryName,
                        categoryIcon = selectedCategoryIcon,
                        payType = selectedPayType,
                        billTime = selectedTime,
                        remark = remark
                    )
                    billDao.update(bill)
                    Toast.makeText(this, "修改成功", Toast.LENGTH_SHORT).show()
                } else {
                    val bill = Bill(
                        money = money,
                        type = billType,
                        categoryId = selectedCategoryId,
                        categoryName = selectedCategoryName,
                        categoryIcon = selectedCategoryIcon,
                        payType = selectedPayType,
                        billTime = selectedTime,
                        remark = remark,
                        source = Bill.SOURCE_MANUAL
                    )
                    billDao.insert(bill)
                    Toast.makeText(this, "记账成功", Toast.LENGTH_SHORT).show()
                }

                playExitAnimation { finish() }
            }
        }
    }

    private fun setupCancelButton() {
        binding.btnCancel.setOnClickListener { playExitAnimation { finish() } }
    }

    // ==================== 编辑模式数据回填 ====================

    private fun loadBillData() {
        val bill = billDao.getById(editBillId) ?: return

        // 金额
        binding.etMoney.setText(bill.money.toString())
        binding.tvAmountDisplay.text = bill.money.toString()
        binding.tvAmountDisplay.setTextColor(0xE0FFFFFF.toInt())
        binding.bubbleInputHint.visibility = View.GONE
        binding.bubbleTip.updateForAmount(bill.money)

        // 标题
        binding.etRemark.setText(bill.remark)

        // 类型
        billType = bill.type
        updateTypeSelection()

        // 分类 & 支付 & 时间
        selectedCategoryId = bill.categoryId
        selectedCategoryName = bill.categoryName
        selectedCategoryIcon = bill.categoryIcon
        selectedPayType = bill.payType
        updatePayChipDisplay()
        selectedTime = bill.billTime
        updateTimeDisplay()

        // 回填分类选中态：先告诉分页适配器选中哪个，再翻到它所在的那一页。
        // 顺序不能反 —— ViewPager2 只为已 bind 的页创建子 adapter，
        // 先设 id 才能保证目标页首次 bind 时就是高亮态。
        categoryPagerAdapter?.let { pager ->
            pager.setSelectedCategoryId(bill.categoryId)
            val page = pager.pageIndexFor(bill.categoryId)
            if (page >= 0) binding.vpCategories.setCurrentItem(page, false)
        }
    }

    private fun updatePayChipDisplay() {
        val selected = when (selectedPayType) {
            Bill.PAY_WECHAT -> binding.chipWechat
            Bill.PAY_ALIPAY -> binding.chipAlipay
            Bill.PAY_CASH -> binding.chipCash
            Bill.PAY_BANK -> binding.chipBank
            else -> binding.chipWechat
        }
        payChips.forEach { chip ->
            if (chip == selected) {
                chip.background = com.billbook.app.util.ThemeSurfaces.chip(chip.context, true)
                chip.setTextColor(0xE0FFFFFF.toInt())
            } else {
                chip.background = com.billbook.app.util.ThemeSurfaces.chip(chip.context, false)
                chip.setTextColor(0x80FFFFFF.toInt())
            }
        }
    }

    // ==================== 动画 ====================

    private fun playExitAnimation(onEnd: () -> Unit) {
        ObjectAnimator.ofFloat(binding.root, "alpha", 1f, 0f).apply {
            duration = 200
            interpolator = DecelerateInterpolator()
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    onEnd()
                }
            })
            start()
        }
    }
}
