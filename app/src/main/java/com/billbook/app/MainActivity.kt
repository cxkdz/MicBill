package com.billbook.app

import android.animation.ValueAnimator
import android.view.animation.DecelerateInterpolator
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.billbook.app.adapter.BillAdapter
import com.billbook.app.adapter.DayPagerAdapter
import com.billbook.app.animation.ExpandAnimationController
import com.billbook.app.db.BillDao
import com.billbook.app.fragment.BudgetFragment
import com.billbook.app.fragment.SettingsFragment
import com.billbook.app.fragment.StatisticsFragment
import com.billbook.app.model.Bill
import com.billbook.app.model.ThemePalette
import com.billbook.app.util.AutoContrast
import com.billbook.app.util.BackgroundApplier
import com.billbook.app.util.ThemeManager
import com.billbook.app.view.DioramaView
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.sin

class MainActivity : AppCompatActivity() {

    private lateinit var themeManager: ThemeManager
    private lateinit var billDao: BillDao
    private lateinit var dayPagerAdapter: DayPagerAdapter
    private var currentNavIndex = 0
    private var currentYear: Int = 0
    private var currentMonth: Int = 0

    /**
     * 用户当前正在看的那一天。
     * 刷新定位按「日期」而不是「页码」——账单增删会让页数变化，
     * 用页码做锚点必然漂到前一天；用日期做锚点才能稳定停在同一天。
     */
    private var currentDayStart: Long? = null
    private val dayHeaderFormat = SimpleDateFormat("MM月dd日 EEEE", Locale.CHINA)

    /** App 上次在前台的公历日，用来识别「跨天回前台」 */
    private var lastActiveDay: String = ""
    private val dayKeyFormat = SimpleDateFormat("yyyyMMdd", Locale.CHINA)

    // Layer 1
    private lateinit var dioramaLayer: DioramaView

    // Layer 2
    private lateinit var backgroundPlane: FrameLayout

    // Layer 3
    private lateinit var headerLayer: LinearLayout
    private lateinit var tvYearMonth: TextView
    private lateinit var btnFilterAll: TextView
    private lateinit var btnFilterExpense: TextView
    private lateinit var btnFilterIncome: TextView

    // Plane content
    private lateinit var vpDayBills: ViewPager2
    private lateinit var rvVerticalBills: RecyclerView
    private lateinit var verticalBillAdapter: BillAdapter
    private lateinit var tvEmpty: TextView
    private lateinit var daySummary: LinearLayout
    private lateinit var tvDayDate: TextView
    private lateinit var tvDayExpense: TextView
    private lateinit var tvDayIncome: TextView

    // Gradient overlays
    private lateinit var statusBarGradient: View
    private lateinit var bottomGradient: View

    // 雪花动画
    private lateinit var snowfallView: View

    // WindowInsets controller for status bar icon color
    private lateinit var insetsController: WindowInsetsControllerCompat

    // 展开动画控制器
    private var expandController: ExpandAnimationController? = null

    // Current theme state for animation
    private var currentTextColor: Int = Color.parseColor("#1A1A2E")
    private var currentSecondaryColor: Int = Color.parseColor("#801A1A2E")

    override fun onCreate(savedInstanceState: Bundle?) {
        themeManager = ThemeManager(this)

        if (themeManager.isDarkMode()) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        }

        setTheme(R.style.Theme_BillBook)  // 主题样式已收敛为一套，配色由 ThemePalettes 负责
        super.onCreate(savedInstanceState)

        // Edge-to-Edge: 内容延伸到状态栏和导航栏区域
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT

        // WindowInsetsController: 控制状态栏图标深浅色
        insetsController = WindowInsetsControllerCompat(window, window.decorView)

        setContentView(R.layout.activity_main)

        val cal = java.util.Calendar.getInstance()
        currentYear = cal.get(java.util.Calendar.YEAR)
        currentMonth = cal.get(java.util.Calendar.MONTH) + 1

        billDao = BillDao(this)

        initViews()
        setupWindowInsets()
        setupGradientOverlay()
        setupViewPager()
        setupVerticalBillList()
        setupBottomNav()
        setupFilter()
        applyThemeBackground()
        setupExpandOverlay()

        // 首帧兜底：部分机型在 measure 之前设置的 BitmapDrawable 会被后续 background 覆盖，
        // 表现就是「首次启动人物没了，切一次主题才出现」。只在确实没拿到图时补一次。
        backgroundPlane.post {
            if (backgroundPlane.background !is android.graphics.drawable.BitmapDrawable) {
                applyForegroundLayer(themeManager.getEffectivePalette())
            }
        }

        if (savedInstanceState == null) {
            selectNavItem(0)
            showBillPage()
        }
    }

    private fun initViews() {
        dioramaLayer = findViewById(R.id.dioramaLayer)
        backgroundPlane = findViewById(R.id.backgroundPlane)
        headerLayer = findViewById(R.id.headerLayer)
        statusBarGradient = findViewById(R.id.statusBarGradient)
        bottomGradient = findViewById(R.id.bottomGradient)
        tvYearMonth = findViewById(R.id.tvYearMonth)
        btnFilterAll = findViewById(R.id.btnFilterAll)
        btnFilterExpense = findViewById(R.id.btnFilterExpense)
        btnFilterIncome = findViewById(R.id.btnFilterIncome)
        vpDayBills = findViewById(R.id.vpDayBills)
        rvVerticalBills = findViewById(R.id.rvVerticalBills)
        tvEmpty = findViewById(R.id.tvEmpty)
        daySummary = findViewById(R.id.daySummary)
        tvDayDate = findViewById(R.id.tvDayDate)
        tvDayExpense = findViewById(R.id.tvDayExpense)
        tvDayIncome = findViewById(R.id.tvDayIncome)
        snowfallView = findViewById(R.id.snowfallView)

        // CRITICAL: pivot at bottom edge for correct 3D rotation
        backgroundPlane.post {
            backgroundPlane.pivotY = backgroundPlane.height.toFloat()
            backgroundPlane.cameraDistance = resources.displayMetrics.density * 15000f
            backgroundPlane.rotationX = themeManager.getTiltAngle().toFloat()
        }
    }

    /**
     * WindowInsets 适配：headerLayer 顶部 padding = 状态栏高度（刘海/挖孔自动适配）
     * bottomNav 底部 padding = 导航栏高度（手势条区域）
     */
    private fun setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(headerLayer) { view, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.statusBars())
            view.updatePadding(top = insets.top)
            windowInsets
        }
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.bottomNav)) { view, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.navigationBars())
            view.updatePadding(bottom = insets.bottom)
            windowInsets
        }
    }

    private fun setupGradientOverlay() {
        applyGradientOverlay()
    }

    private fun applyGradientOverlay() {
        val isDark = themeManager.isDarkMode()
        val startColor = if (isDark) Color.parseColor("#B3000000") else Color.parseColor("#CC000000")
        statusBarGradient.background = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(startColor, Color.TRANSPARENT)
        )
        val bottomEndColor = if (isDark) Color.parseColor("#66000000") else Color.parseColor("#80000000")
        bottomGradient.background = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(Color.TRANSPARENT, bottomEndColor)
        )
    }

    /**
     * 根据调色板天空亮度决定状态栏图标深浅色
     */
    private fun updateStatusBarIconColor(palette: ThemePalette) {
        val isDarkBg = AutoContrast.isDark(palette.dioramaSkyTop)
        insetsController.isAppearanceLightStatusBars = !isDarkBg
        insetsController.isAppearanceLightNavigationBars = true
    }

    fun updateTiltPreview(angle: Float) {
        if (::backgroundPlane.isInitialized) {
            backgroundPlane.rotationX = angle
        }
    }

    override fun onResume() {
        super.onResume()
        themeManager = ThemeManager(this)

        // App 常驻后台没被杀时，第二天回前台不该还停在昨天的锚点上。
        val todayKey = dayKeyFormat.format(java.util.Date())
        val crossedDay = lastActiveDay.isNotEmpty() && lastActiveDay != todayKey
        lastActiveDay = todayKey

        applyThemeBackground()
        selectNavItem(currentNavIndex)
        if (currentNavIndex == 0) {
            if (crossedDay) {
                val now = Calendar.getInstance()
                currentYear = now.get(Calendar.YEAR)
                currentMonth = now.get(Calendar.MONTH) + 1
                refreshBillData(preferDayStart = startOfToday())
            } else {
                refreshBillData()
            }
        }
    }

    // 横向 / 竖向两套列表共用同一套点击行为，避免改一处漏一处
    private fun openBillDetail(bill: Bill) {
        startActivity(
            Intent(this, BillDetailActivity::class.java).putExtra("bill_id", bill.id)
        )
    }

    private fun confirmDeleteBill(bill: Bill) {
        AlertDialog.Builder(this)
            .setTitle("删除账单")
            .setMessage("确定要删除这条 ${bill.categoryName} ¥${bill.money} 的记录吗？")
            .setPositiveButton("删除") { _, _ ->
                billDao.delete(bill.id)
                refreshBillData()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun setupViewPager() {
        dayPagerAdapter = DayPagerAdapter(
            onBillClick = ::openBillDetail,
            onBillLongClick = ::confirmDeleteBill
        )
        vpDayBills.adapter = dayPagerAdapter

        // Scroll callback: alpha breathing effect on background plane
        vpDayBills.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {
                val alpha = 1.0f - 0.3f * sin(positionOffset * Math.PI).toFloat()
                backgroundPlane.alpha = alpha
            }

            override fun onPageSelected(position: Int) {
                val days = dayPagerAdapter.getCurrentDays()
                if (position in days.indices) currentDayStart = days[position].dayStart
                updateDaySummary(position)
            }
        })
    }

    /** 竖向模式：复用带日期表头的 BillAdapter，一天一组、连续下滑 */
    private fun setupVerticalBillList() {
        verticalBillAdapter = BillAdapter(
            onItemClick = ::openBillDetail,
            onItemLongClick = ::confirmDeleteBill
        ).apply {
            // 竖向是普通账单流，不套用「卡片堆叠」的渐隐缩放
            setStackedEffectEnabled(false)
        }
        rvVerticalBills.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = verticalBillAdapter
            addItemDecoration(BillAdapter.DayDividerDecoration())
        }
    }

    private fun updateDaySummary(position: Int) {
        val days = dayPagerAdapter.getCurrentDays()
        if (position in days.indices) {
            val day = days[position]
            tvDayDate.text = day.dateText
            tvDayExpense.text = "-¥%.2f".format(day.totalExpense)
            tvDayIncome.text = "+¥%.2f".format(day.totalIncome)
        }
    }

    private fun setupFilter() {
        btnFilterAll.setOnClickListener {
            setSegmentActive(0)
            refreshBillData()
        }
        btnFilterExpense.setOnClickListener {
            setSegmentActive(2)
            filterByType(Bill.TYPE_EXPENSE)
        }
        btnFilterIncome.setOnClickListener {
            setSegmentActive(1)
            filterByType(Bill.TYPE_INCOME)
        }
        tvYearMonth.setOnClickListener {
            showMonthPickerDialog()
        }
        setSegmentActive(0)
    }

    private fun setSegmentActive(index: Int) {
        val palette = themeManager.getEffectivePalette()
        val buttons = listOf(btnFilterAll, btnFilterIncome, btnFilterExpense)
        buttons.forEachIndexed { i, btn ->
            if (i == index) {
                btn.setBackgroundResource(R.drawable.bg_segment_active)
                btn.setTextColor(palette.segmentActiveText)
            } else {
                btn.background = null
                btn.setTextColor(palette.segmentInactiveText)
            }
        }
    }

    private fun showMonthPickerDialog() {
        val cal = java.util.Calendar.getInstance()
        cal.set(currentYear, currentMonth - 1, 1, 0, 0, 0)
        com.billbook.app.animation.AnimeDatePickerDialog(this, cal.timeInMillis, showDayToggle = true) { year, month, _, _, _ ->
            currentYear = year
            currentMonth = month + 1
            refreshBillData()
        }.show()
    }

    /**
     * @param preferDayStart 强制定位到这一天（刚记完账时传那笔账的时间）；
     *                       null 表示保持用户当前浏览的那天，首次进入则回落到今天。
     */
    private fun refreshBillData(preferDayStart: Long? = null) {
        tvYearMonth.text = "${currentYear}年${currentMonth}月"

        val days = billDao.getDaysInMonth(currentYear, currentMonth).toMutableList()

        // 当前月永远补一页「今天」：今天还没记账，也应该能停在今天，
        // 而不是被留在最后一条账单所在的昨天。
        if (isCurrentMonth()) {
            val today = startOfToday()
            if (days.none { isSameDay(it.dayStart, today) }) {
                days.add(
                    BillDao.DayBills(
                        dateText = dayHeaderFormat.format(java.util.Date(today)),
                        dayStart = today,
                        bills = emptyList(),
                        totalExpense = 0.0,
                        totalIncome = 0.0
                    )
                )
                days.sortBy { it.dayStart }
            }
        }

        applyDays(days, preferDayStart)
    }

    /** 统一收口：按浏览方式切换容器 → 按日期定位 → 立刻回填汇总 → 决定空态。 */
    private fun applyDays(days: List<BillDao.DayBills>, preferDayStart: Long?) {
        dayPagerAdapter.submitDays(days)
        val vertical = themeManager.isVerticalBillMode()
        val allBills = days.flatMap { it.bills }

        // 两种模式共用同一个日期锚点，切换浏览方式时不会丢掉「正在看哪天」
        val wanted = preferDayStart ?: currentDayStart ?: startOfToday()
        val anchor = days.indexOfFirst { isSameDay(it.dayStart, wanted) }

        if (vertical) {
            vpDayBills.visibility = View.GONE
            rvVerticalBills.visibility = View.VISIBLE
            // 竖向模式的每日收支放在日期表头里，顶部日汇总栏不再重复
            daySummary.visibility = View.GONE
            verticalBillAdapter.submitList(allBills)
            tvEmpty.visibility = if (allBills.isEmpty()) View.VISIBLE else View.GONE
            currentDayStart = if (anchor >= 0) days[anchor].dayStart else null
            return
        }

        rvVerticalBills.visibility = View.GONE
        if (days.isEmpty()) {
            tvEmpty.visibility = View.VISIBLE
            vpDayBills.visibility = View.GONE
            daySummary.visibility = View.GONE
            currentDayStart = null
            return
        }

        val target = if (anchor >= 0) anchor else days.lastIndex
        // 先定位，再同步汇总。不能只靠 onPageSelected：页码没变化时它不会回调，
        // 而 submitDays 之后汇总数据已经是新的，手动回填才不会「慢一拍」。
        vpDayBills.setCurrentItem(target, false)
        updateDaySummary(target)
        currentDayStart = days[target].dayStart

        tvEmpty.visibility = if (days[target].bills.isEmpty()) View.VISIBLE else View.GONE
        vpDayBills.visibility = View.VISIBLE
        daySummary.visibility = View.VISIBLE
    }

    private fun startOfToday(): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun isSameDay(a: Long, b: Long): Boolean {
        val ca = Calendar.getInstance().apply { timeInMillis = a }
        val cb = Calendar.getInstance().apply { timeInMillis = b }
        return ca.get(Calendar.YEAR) == cb.get(Calendar.YEAR) &&
            ca.get(Calendar.DAY_OF_YEAR) == cb.get(Calendar.DAY_OF_YEAR)
    }

    private fun isCurrentMonth(): Boolean {
        val now = Calendar.getInstance()
        return currentYear == now.get(Calendar.YEAR) && currentMonth == now.get(Calendar.MONTH) + 1
    }

    private fun filterByType(type: Int) {
        tvYearMonth.text = "${currentYear}年${currentMonth}月"

        val allDays = billDao.getDaysInMonth(currentYear, currentMonth)
        val filteredDays = allDays.mapNotNull { day ->
            val filteredBills = day.bills.filter { it.type == type }
            if (filteredBills.isEmpty()) return@mapNotNull null
            val expense = if (type == Bill.TYPE_EXPENSE) filteredBills.sumOf { it.money } else 0.0
            val income = if (type == Bill.TYPE_INCOME) filteredBills.sumOf { it.money } else 0.0
            day.copy(bills = filteredBills, totalExpense = expense, totalIncome = income)
        }

        dayPagerAdapter.submitDays(filteredDays)

        if (filteredDays.isEmpty()) {
            tvEmpty.visibility = View.VISIBLE
            vpDayBills.visibility = View.GONE
            daySummary.visibility = View.GONE
            currentDayStart = null
            return
        }

        val wanted = currentDayStart ?: startOfToday()
        var target = filteredDays.indexOfFirst { isSameDay(it.dayStart, wanted) }
        if (target < 0) target = filteredDays.lastIndex
        vpDayBills.setCurrentItem(target, false)
        updateDaySummary(target)
        currentDayStart = filteredDays[target].dayStart

        tvEmpty.visibility = View.GONE
        vpDayBills.visibility = View.VISIBLE
        daySummary.visibility = View.VISIBLE
    }

    // ==================== Navigation ====================

    private fun setupBottomNav() {
        val fabAdd = findViewById<FrameLayout>(R.id.fabAdd)
        fabAdd.setOnClickListener {
            expandController?.expand() ?: run {
                startActivity(Intent(this, AddBillActivity::class.java))
            }
        }
        findViewById<LinearLayout>(R.id.navItemBills).setOnClickListener {
            selectNavItem(0)
            showBillPage()
        }
        findViewById<LinearLayout>(R.id.navItemStats).setOnClickListener {
            selectNavItem(1)
            loadFragment(StatisticsFragment())
        }
        findViewById<LinearLayout>(R.id.navItemBudget).setOnClickListener {
            selectNavItem(3)
            loadFragment(BudgetFragment())
        }
        findViewById<LinearLayout>(R.id.navItemSettings).setOnClickListener {
            selectNavItem(4)
            loadFragment(SettingsFragment())
        }
    }

    private fun showBillPage() {
        findViewById<View>(R.id.fragmentContainer).visibility = View.GONE
        val vertical = themeManager.isVerticalBillMode()
        vpDayBills.visibility = if (vertical) View.GONE else View.VISIBLE
        rvVerticalBills.visibility = if (vertical) View.VISIBLE else View.GONE
        daySummary.visibility = if (vertical) View.GONE else View.VISIBLE
        snowfallView.visibility = View.VISIBLE
        tvEmpty.visibility = View.GONE
        headerLayer.visibility = View.VISIBLE
        refreshBillData()
    }

    // ==================== 展开动画 ====================

    private fun setupExpandOverlay() {
        val fabAdd = findViewById<FrameLayout>(R.id.fabAdd)
        val overlayView = LayoutInflater.from(this).inflate(R.layout.layout_expand_overlay, null)
        com.billbook.app.util.ThemeSurfaces.apply(overlayView)

        // 添加 overlay 到根布局
        val rootView = findViewById<FrameLayout>(android.R.id.content)
        rootView.addView(overlayView, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))

        // 创建动画控制器
        expandController = ExpandAnimationController(
            overlayRoot = overlayView.findViewById(R.id.expandOverlayRoot),
            dimBackground = overlayView.findViewById(R.id.dimBackground),
            expandContainer = overlayView.findViewById(R.id.expandContainer),
            expandContent = overlayView.findViewById(R.id.expandContent),
            mainContentRoot = backgroundPlane,
            fabAdd = fabAdd,
            resources = resources
        )

        // 顶部栏适配状态栏 insets
        val topBar = overlayView.findViewById<View>(R.id.topBar)
        ViewCompat.setOnApplyWindowInsetsListener(topBar) { view, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.statusBars())
            view.updatePadding(top = insets.top)
            windowInsets
        }

        // 设置关闭按钮
        overlayView.findViewById<ImageView>(R.id.btnExpandClose).setOnClickListener {
            expandController?.collapse()
        }

        // 设置记账表单
        setupOverlayForm(overlayView)
    }

    private fun setupOverlayForm(overlayView: View) {
        val billDao = BillDao(this)
        val categoryDao = com.billbook.app.db.CategoryDao(this)
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA)

        var billType = Bill.TYPE_EXPENSE
        var selectedCategoryId = 0L
        var selectedCategoryName = ""
        var selectedCategoryIcon = ""
        var selectedPayType = Bill.PAY_WECHAT
        var selectedTime = System.currentTimeMillis()

        val chipExpense = overlayView.findViewById<TextView>(R.id.chipExpense)
        val chipIncome = overlayView.findViewById<TextView>(R.id.chipIncome)
        val etMoney = overlayView.findViewById<android.widget.EditText>(R.id.etMoney)
        val tvAmountDisplay = overlayView.findViewById<TextView>(R.id.tvAmountDisplay)
        val bubbleTip = overlayView.findViewById<com.billbook.app.animation.BubbleTipView>(R.id.bubbleTip)
        val bubbleInputHint = overlayView.findViewById<com.billbook.app.animation.BubbleTipView>(R.id.bubbleInputHint)
        val vpCategories = overlayView.findViewById<androidx.viewpager2.widget.ViewPager2>(R.id.vpCategories)
        val layoutDotIndicator = overlayView.findViewById<android.widget.LinearLayout>(R.id.layoutDotIndicator)
        val tvTime = overlayView.findViewById<TextView>(R.id.tvTime)
        val etRemark = overlayView.findViewById<android.widget.EditText>(R.id.etRemark)
        val btnSave = overlayView.findViewById<com.billbook.app.animation.GlowButton>(R.id.btnSave)
        val btnCancel = overlayView.findViewById<TextView>(R.id.btnCancel)

        // 支付渠道 TextViews
        val chipWechat = overlayView.findViewById<TextView>(R.id.chipWechat)
        val chipAlipay = overlayView.findViewById<TextView>(R.id.chipAlipay)
        val chipCash = overlayView.findViewById<TextView>(R.id.chipCash)
        val chipBank = overlayView.findViewById<TextView>(R.id.chipBank)
        val payChips = listOf(chipWechat, chipAlipay, chipCash, chipBank)

        fun updatePayChipSelection(selected: TextView) {
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

        chipWechat.setOnClickListener { selectedPayType = Bill.PAY_WECHAT; updatePayChipSelection(chipWechat) }
        chipAlipay.setOnClickListener { selectedPayType = Bill.PAY_ALIPAY; updatePayChipSelection(chipAlipay) }
        chipCash.setOnClickListener { selectedPayType = Bill.PAY_CASH; updatePayChipSelection(chipCash) }
        chipBank.setOnClickListener { selectedPayType = Bill.PAY_BANK; updatePayChipSelection(chipBank) }

        // 收支切换
        fun updateTypeSelection() {
            if (billType == Bill.TYPE_EXPENSE) {
                chipExpense.background = com.billbook.app.util.ThemeSurfaces.chip(chipExpense.context, true)
                chipExpense.setTextColor(0xE0FFFFFF.toInt())
                chipIncome.setBackgroundColor(Color.TRANSPARENT)
                chipIncome.setTextColor(getColor(R.color.anime_text_hint))
            } else {
                chipIncome.background = com.billbook.app.util.ThemeSurfaces.chip(chipIncome.context, true)
                chipIncome.setTextColor(0xE0FFFFFF.toInt())
                chipExpense.setBackgroundColor(Color.TRANSPARENT)
                chipExpense.setTextColor(getColor(R.color.anime_text_hint))
            }
        }

        val categoryDots = mutableListOf<View>()
        var categoryPagerAdapter: com.billbook.app.adapter.CategoryPagerAdapter? = null
        var categoryPageCallback: androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback? = null

        fun updateCategoryDots(activeIndex: Int) {
            categoryDots.forEachIndexed { i, dot ->
                dot.setBackgroundResource(if (i == activeIndex) R.drawable.dot_active else R.drawable.dot_inactive)
            }
        }

        fun setupCategoryDots(totalItems: Int) {
            val pageCount = if (totalItems == 0) 0 else (totalItems + 11) / 12
            layoutDotIndicator.removeAllViews()
            categoryDots.clear()
            layoutDotIndicator.visibility = View.VISIBLE
            val dp = resources.displayMetrics.density
            for (i in 0 until pageCount) {
                val dot = View(this).apply {
                    layoutParams = android.widget.LinearLayout.LayoutParams((8 * dp).toInt(), (8 * dp).toInt()).apply {
                        marginStart = if (i == 0) 0 else (6 * dp).toInt()
                    }
                    setBackgroundResource(if (i == 0) R.drawable.dot_active else R.drawable.dot_inactive)
                }
                categoryDots.add(dot)
                layoutDotIndicator.addView(dot)
            }
        }

        fun loadCategories() {
            val categories = categoryDao.getAll(billType)
            val pagerAdapter = com.billbook.app.adapter.CategoryPagerAdapter(categories) { category ->
                selectedCategoryId = category.id
                selectedCategoryName = category.name
                selectedCategoryIcon = category.icon
            }
            categoryPagerAdapter = pagerAdapter
            vpCategories.adapter = pagerAdapter
            vpCategories.setPageTransformer(object : androidx.viewpager2.widget.ViewPager2.PageTransformer {
                override fun transformPage(page: View, position: Float) {
                    page.scaleY = 1f - 0.15f * kotlin.math.abs(position)
                    page.scaleX = 1f - 0.15f * kotlin.math.abs(position)
                    page.alpha = 1f - 0.4f * kotlin.math.abs(position)
                }
            })
            setupCategoryDots(categories.size)
            categoryPageCallback?.let { vpCategories.unregisterOnPageChangeCallback(it) }
            categoryPageCallback = object : androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    updateCategoryDots(position)
                }
            }
            vpCategories.registerOnPageChangeCallback(categoryPageCallback!!)
        }

        chipExpense.setOnClickListener {
            billType = Bill.TYPE_EXPENSE
            updateTypeSelection()
            loadCategories()
        }
        chipIncome.setOnClickListener {
            billType = Bill.TYPE_INCOME
            updateTypeSelection()
            loadCategories()
        }

        updateTypeSelection()
        loadCategories()

        // 输入提示气泡
        bubbleInputHint.setTipText("点击输入金额")

        // 金额输入：点击显示区域或气泡触发键盘
        val triggerKeyboard = {
            etMoney.requestFocus()
            val imm = getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            imm.showSoftInput(etMoney, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
            // 输入后隐藏提示气泡
            bubbleInputHint.animate().alpha(0f).setDuration(200).withEndAction {
                bubbleInputHint.visibility = View.GONE
            }.start()
        }
        tvAmountDisplay.setOnClickListener { triggerKeyboard() }
        bubbleInputHint.setOnClickListener { triggerKeyboard() }

        // 金额输入监听：同步显示 + 更新气泡
        etMoney.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                val text = s?.toString() ?: ""
                if (text.isNotEmpty()) {
                    tvAmountDisplay.text = text
                    tvAmountDisplay.setTextColor(0xE0FFFFFF.toInt())
                    val amount = text.toDoubleOrNull() ?: 0.0
                    bubbleTip.updateForAmount(amount)
                } else {
                    tvAmountDisplay.text = ""
                    tvAmountDisplay.setTextColor(getColor(R.color.anime_text_hint))
                    bubbleTip.updateForAmount(0.0)
                }
            }
        })

        // 时间选择 — 二次元沉浸式日期选择器
        tvTime.text = dateFormat.format(selectedTime)
        tvTime.setOnClickListener {
            com.billbook.app.animation.AnimeDatePickerDialog(
                context = this,
                initialTimeMillis = selectedTime
            ) { year, month, day, hour, minute ->
                val cal = Calendar.getInstance()
                cal.set(year, month, day, hour, minute, 0)
                selectedTime = cal.timeInMillis
                tvTime.text = dateFormat.format(selectedTime)
            }.show()
        }

        // 保存
        btnSave.setOnClickListener {
            val moneyStr = etMoney.text.toString().trim()
            val money = moneyStr.toDoubleOrNull()

            if (money == null || money <= 0) {
                android.widget.Toast.makeText(this, "请输入有效金额", android.widget.Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (selectedCategoryId == 0L) {
                android.widget.Toast.makeText(this, "请选择分类", android.widget.Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // 弹簧动画后保存
            btnSave.playClickEffect {
                val remark = etRemark.text.toString().trim()
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
                android.widget.Toast.makeText(this, "记账成功", android.widget.Toast.LENGTH_SHORT).show()

                // 清空表单并关闭
                etMoney.text.clear()
                etRemark.text.clear()
                selectedCategoryId = 0L
                expandController?.collapse()

                // 记完直接跳到这笔账所在的那天（通常是今天），并立刻重算当日汇总。
                // 若用户把日期改到了别的月份，顺带把首页月份切过去，否则看不到刚记的账。
                if (currentNavIndex == 0) {
                    val cal = Calendar.getInstance().apply { timeInMillis = bill.billTime }
                    currentYear = cal.get(Calendar.YEAR)
                    currentMonth = cal.get(Calendar.MONTH) + 1
                    refreshBillData(preferDayStart = bill.billTime)
                }
            }
        }

        // 取消按钮
        btnCancel.setOnClickListener {
            expandController?.collapse()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (expandController?.state == ExpandAnimationController.State.EXPANDED ||
            expandController?.state == ExpandAnimationController.State.EXPANDING) {
            expandController?.collapse()
        } else {
            super.onBackPressed()
        }
    }

    private fun loadFragment(fragment: Fragment) {
        findViewById<View>(R.id.fragmentContainer).visibility = View.VISIBLE
        vpDayBills.visibility = View.GONE
        rvVerticalBills.visibility = View.GONE
        snowfallView.visibility = View.GONE
        daySummary.visibility = View.GONE
        tvEmpty.visibility = View.GONE
        headerLayer.visibility = View.GONE

        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .commit()
    }

    private fun selectNavItem(index: Int) {
        currentNavIndex = index
        val palette = themeManager.getEffectivePalette()
        val planeComposite = computePlaneCompositeColor(palette)
        val activeColor = AutoContrast.textColorFor(planeComposite)
        val hintColor = AutoContrast.hintTextColorFor(planeComposite)

        updateNavItem(R.id.navIconBills, R.id.navLabelBills, index == 0, activeColor, hintColor)
        updateNavItem(R.id.navIconStats, R.id.navLabelStats, index == 1, activeColor, hintColor)
        updateNavItem(R.id.navIconBudget, R.id.navLabelBudget, index == 3, activeColor, hintColor)
        updateNavItem(R.id.navIconSettings, R.id.navLabelSettings, index == 4, activeColor, hintColor)
    }

    private fun updateNavItem(iconId: Int, labelId: Int, isSelected: Boolean, activeColor: Int, defaultColor: Int) {
        val icon = findViewById<ImageView>(iconId) ?: return
        val label = findViewById<TextView>(labelId) ?: return

        if (isSelected) {
            icon.setColorFilter(activeColor)
            label.setTextColor(activeColor)
            label.paint.isFakeBoldText = true
        } else {
            icon.setColorFilter(defaultColor)
            label.setTextColor(defaultColor)
            label.paint.isFakeBoldText = false
        }
    }

    // ==================== Theme System ====================

    private fun applyThemeBackground(animate: Boolean = false) {
        val palette = themeManager.getEffectivePalette()

        if (animate) {
            animateThemeTransition(palette)
            return
        }

        applyPaletteImmediate(palette)
    }

    private fun applyPaletteImmediate(palette: ThemePalette) {
        // Layer 1「背景」：用户自定义图优先，否则用预设的纯色渐变
        val dioramaUri = themeManager.getCustomDioramaImageUri()
        if (dioramaUri != null) {
            dioramaLayer.applyCustomImage(dioramaUri)
        } else {
            dioramaLayer.applyGradient(palette.dioramaSkyTop, palette.dioramaSkyBottom)
        }

        // Layer 2「前景」：自定义图 > 预设角色图 > 玻璃平面
        applyForegroundLayer(palette)
        // 个别机型首帧会把 alpha 留在 0（跟随页面切换的呼吸动效残值），这里强制归位
        backgroundPlane.alpha = 1f

        // 记账浮层（FAB 展开）容器跟随当前主题的深色变体
        expandController?.applyPalette(palette)

        // Compute auto-contrast colors
        val planeComposite = computePlaneCompositeColor(palette)
        currentTextColor = AutoContrast.textColorFor(planeComposite)
        currentSecondaryColor = AutoContrast.secondaryTextColorFor(planeComposite)
        val shadowColor = AutoContrast.textShadowColor(planeComposite)

        // Layer 3: Header text + shadows
        tvYearMonth.setTextColor(currentTextColor)
        tvYearMonth.setShadowLayer(6f, 0f, 2f, shadowColor)

        // Day summary text
        tvDayDate.setTextColor(currentSecondaryColor)
        tvDayDate.setShadowLayer(3f, 0f, 1f, shadowColor)
        tvDayExpense.setTextColor(palette.expenseColor)
        tvDayExpense.setShadowLayer(3f, 0f, 1f, shadowColor)
        tvDayIncome.setTextColor(palette.incomeColor)
        tvDayIncome.setShadowLayer(3f, 0f, 1f, shadowColor)

        // Segment control
        setSegmentActive(if (currentNavIndex == 0) 0 else -1)

        // Bottom nav
        selectNavItem(currentNavIndex)

        // Bill item text colors + shadow（横向 / 竖向两套列表共用）
        dayPagerAdapter.setTextColors(currentTextColor, currentSecondaryColor, shadowColor)
        verticalBillAdapter.setTextColors(currentTextColor, currentSecondaryColor, shadowColor)

        // Status bar icon color + gradient overlay
        updateStatusBarIconColor(palette)
        applyGradientOverlay()
    }

    private fun applyPlaneTheme(palette: ThemePalette) {
        val isDark = themeManager.isDarkMode()
        val glassAlpha = if (isDark) (palette.planeGlassAlpha * 1.5f).toInt().coerceAtMost(255) else palette.planeGlassAlpha

        val glassColor = Color.argb(
            glassAlpha,
            Color.red(palette.planePrimary),
            Color.green(palette.planePrimary),
            Color.blue(palette.planePrimary)
        )

        val isDarkScene = AutoContrast.isDark(palette.dioramaGround)
        val borderColor = if (isDarkScene) {
            Color.argb(palette.planeBorderAlpha, 255, 255, 255)
        } else {
            Color.argb(palette.planeBorderAlpha / 2, 0, 0, 0)
        }

        val border = GradientDrawable().apply {
            setColor(Color.TRANSPARENT)
            cornerRadius = resources.getDimension(R.dimen.plane_corner_radius)
            setStroke((0.5f * resources.displayMetrics.density).toInt(), borderColor)
        }
        val fill = GradientDrawable().apply {
            setColor(glassColor)
            cornerRadius = resources.getDimension(R.dimen.plane_corner_radius)
        }
        val layerDrawable = LayerDrawable(arrayOf(border, fill))
        layerDrawable.setLayerInset(1, 1, 1, 1, 1)
        backgroundPlane.background = layerDrawable
    }

    private fun applyPlaneImageBackground() {
        val uri = themeManager.getCustomBackgroundUri() ?: return
        val metrics = resources.displayMetrics
        val bitmap = com.billbook.app.util.BitmapUtils.decodeSampledUri(
            this, uri, metrics.widthPixels, metrics.heightPixels
        )
        if (bitmap == null) {
            applyPlaneTheme(themeManager.getEffectivePalette())
            return
        }
        applyPlaneBitmap(bitmap)
    }

    /** Layer 2 前景层：自定义图 > 预设角色图 > 玻璃平面 */
    private fun applyForegroundLayer(palette: ThemePalette) {
        if (themeManager.isCustomBackgroundEnabled()) {
            appliedFgResId = 0
            applyPlaneImageBackground()
        } else if (palette.foregroundResId != 0) {
            if (appliedFgResId != palette.foregroundResId) {
                appliedFgResId = palette.foregroundResId
                // 冷启动时立刻解码会跟布局、图片资源抢内存，失败就退化成空玻璃板
                // （用户看到的现象：首次安装没有人物，切一次主题才出现）。
                // 放到首帧之后再解码，顺带避开 measure 之前设置背景被覆盖的问题。
                backgroundPlane.post { applyPlanePresetImage(palette.foregroundResId) }
            }
        } else {
            appliedFgResId = 0
            applyPlaneTheme(palette)
        }
    }

    /** 当前已经应用到平面上的预设前景图，避免动画每帧重复解码 */
    private var appliedFgResId = 0

    /** 预设自带的角色图作为「前景」 */
    private fun applyPlanePresetImage(resId: Int) {
        val metrics = resources.displayMetrics
        // 冷启动时堆内存最紧张，解码失败会退化成空玻璃板（看起来就是「人物没了」）。
        // 失败就降一级采样重试，实在不行才回落到玻璃平面。
        var bitmap = com.billbook.app.util.BitmapUtils.decodeSampledResource(
            resources, resId, metrics.widthPixels, metrics.heightPixels
        )
        if (bitmap == null) {
            bitmap = com.billbook.app.util.BitmapUtils.decodeSampledResource(
                resources, resId, metrics.widthPixels / 2, metrics.heightPixels / 2, extraSample = 2
            )
        }
        if (bitmap == null) {
            // 解码失败：清掉标记，下次 onResume 还会再试一次
            appliedFgResId = 0
            applyPlaneTheme(themeManager.getEffectivePalette())
            return
        }
        applyPlaneBitmap(bitmap)
    }

    /**
     * 前景图统一处理：圆角裁剪 → 半透明蒙版（保证账单文字可读）→ 沿用「前景透明度」滑杆。
     * 蒙版用 SRC_ATOP，只在已绘制的圆角区域内叠加，不会越界。
     */
    private fun applyPlaneBitmap(bitmap: android.graphics.Bitmap) {
        try {
            val opacity = themeManager.getBgOpacity()
            val alpha = (255 * opacity / 100).coerceIn(0, 255)
            val cornerRadius = resources.getDimension(R.dimen.plane_corner_radius)

            val output = android.graphics.Bitmap.createBitmap(
                bitmap.width, bitmap.height, android.graphics.Bitmap.Config.ARGB_8888
            )
            val canvas = android.graphics.Canvas(output)
            val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
            val rect = android.graphics.RectF(0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat())
            canvas.drawRoundRect(rect, cornerRadius, cornerRadius, paint)
            paint.xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.SRC_IN)
            canvas.drawBitmap(bitmap, 0f, 0f, paint)

            val overlay = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(64, 18, 20, 34)
                xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.SRC_ATOP)
            }
            canvas.drawRoundRect(rect, cornerRadius, cornerRadius, overlay)

            backgroundPlane.background = android.graphics.drawable.BitmapDrawable(resources, output).apply {
                this.alpha = alpha
            }
        } catch (_: Throwable) {
            // 内存吃紧时圆角蒙版可能创建失败（OOM 是 Error 不是 Exception）。
            // 宁可显示未裁圆角的人物，也不要退成一块空玻璃板。
            try {
                val alpha = (255 * themeManager.getBgOpacity() / 100).coerceIn(0, 255)
                backgroundPlane.background =
                    android.graphics.drawable.BitmapDrawable(resources, bitmap).apply { this.alpha = alpha }
            } catch (_: Throwable) {
                appliedFgResId = 0
                applyPlaneTheme(themeManager.getEffectivePalette())
            }
        }
    }

    private fun computePlaneCompositeColor(palette: ThemePalette): Int {
        val alpha = palette.planeGlassAlpha / 255f
        val isDark = themeManager.isDarkMode()
        val effectiveAlpha = if (isDark) (alpha * 1.5f).coerceAtMost(1f) else alpha

        val r = (Color.red(palette.planePrimary) * effectiveAlpha + Color.red(palette.dioramaGround) * (1 - effectiveAlpha)).toInt()
        val g = (Color.green(palette.planePrimary) * effectiveAlpha + Color.green(palette.dioramaGround) * (1 - effectiveAlpha)).toInt()
        val b = (Color.blue(palette.planePrimary) * effectiveAlpha + Color.blue(palette.dioramaGround) * (1 - effectiveAlpha)).toInt()
        return Color.rgb(r.coerceIn(0, 255), g.coerceIn(0, 255), b.coerceIn(0, 255))
    }

    private fun animateThemeTransition(targetPalette: ThemePalette) {
        val startTextColor = currentTextColor
        val startSecondaryColor = currentSecondaryColor
        val startPlaneColor = Color.argb(
            targetPalette.planeGlassAlpha,
            Color.red(targetPalette.planePrimary),
            Color.green(targetPalette.planePrimary),
            Color.blue(targetPalette.planePrimary)
        )

        val animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 450L
            interpolator = DecelerateInterpolator()
            addUpdateListener { anim ->
                val fraction = anim.animatedValue as Float
                applyPaletteImmediate(targetPalette)
                backgroundPlane.alpha = 1f
            }
        }
        animator.start()
    }

    private fun lightenColor(color: Int, factor: Float): Int {
        val r = Color.red(color)
        val g = Color.green(color)
        val b = Color.blue(color)
        return Color.rgb(
            (r + (255 - r) * factor).toInt().coerceAtMost(255),
            (g + (255 - g) * factor).toInt().coerceAtMost(255),
            (b + (255 - b) * factor).toInt().coerceAtMost(255)
        )
    }

    private fun darkenColor(color: Int, factor: Float): Int {
        val r = (Color.red(color) * factor).toInt()
        val g = (Color.green(color) * factor).toInt()
        val b = (Color.blue(color) * factor).toInt()
        return Color.rgb(r, g, b)
    }
}
