package com.billbook.app

import android.animation.ValueAnimator
import android.view.animation.DecelerateInterpolator
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.os.Bundle
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
import androidx.viewpager2.widget.ViewPager2
import com.billbook.app.adapter.DayPagerAdapter
import com.billbook.app.db.BillDao
import com.billbook.app.fragment.BudgetFragment
import com.billbook.app.fragment.SettingsFragment
import com.billbook.app.fragment.StatisticsFragment
import com.billbook.app.model.Bill
import com.billbook.app.model.ThemePalette
import com.billbook.app.util.AutoContrast
import com.billbook.app.util.ThemeManager
import com.billbook.app.view.DioramaView
import kotlin.math.sin

class MainActivity : AppCompatActivity() {

    private lateinit var themeManager: ThemeManager
    private lateinit var billDao: BillDao
    private lateinit var dayPagerAdapter: DayPagerAdapter
    private var currentNavIndex = 0
    private var currentYear: Int = 0
    private var currentMonth: Int = 0

    // Layer 1
    private lateinit var dioramaLayer: DioramaView

    // Layer 2
    private lateinit var backgroundPlane: FrameLayout

    // Layer 3
    private lateinit var headerLayer: LinearLayout
    private lateinit var tvYearMonth: TextView
    private lateinit var tvHeaderExpense: TextView
    private lateinit var tvHeaderIncome: TextView
    private lateinit var btnFilterAll: TextView
    private lateinit var btnFilterExpense: TextView
    private lateinit var btnFilterIncome: TextView

    // Plane content
    private lateinit var vpDayBills: ViewPager2
    private lateinit var tvEmpty: TextView
    private lateinit var daySummary: LinearLayout
    private lateinit var tvDayDate: TextView
    private lateinit var tvDayExpense: TextView
    private lateinit var tvDayIncome: TextView

    // Gradient overlays
    private lateinit var statusBarGradient: View
    private lateinit var bottomGradient: View

    // WindowInsets controller for status bar icon color
    private lateinit var insetsController: WindowInsetsControllerCompat

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

        setTheme(themeManager.getCurrentTheme().styleRes)
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
        setupBottomNav()
        setupFilter()
        applyThemeBackground()

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
        tvHeaderExpense = findViewById(R.id.tvHeaderExpense)
        tvHeaderIncome = findViewById(R.id.tvHeaderIncome)
        btnFilterAll = findViewById(R.id.btnFilterAll)
        btnFilterExpense = findViewById(R.id.btnFilterExpense)
        btnFilterIncome = findViewById(R.id.btnFilterIncome)
        vpDayBills = findViewById(R.id.vpDayBills)
        tvEmpty = findViewById(R.id.tvEmpty)
        daySummary = findViewById(R.id.daySummary)
        tvDayDate = findViewById(R.id.tvDayDate)
        tvDayExpense = findViewById(R.id.tvDayExpense)
        tvDayIncome = findViewById(R.id.tvDayIncome)

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
        applyThemeBackground()
        selectNavItem(currentNavIndex)
        if (currentNavIndex == 0) refreshBillData()
    }

    private fun setupViewPager() {
        dayPagerAdapter = DayPagerAdapter(
            onBillClick = { bill ->
                val intent = Intent(this, BillDetailActivity::class.java)
                intent.putExtra("bill_id", bill.id)
                startActivity(intent)
            },
            onBillLongClick = { bill ->
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
        )
        vpDayBills.adapter = dayPagerAdapter

        // Scroll callback: alpha breathing effect on background plane
        vpDayBills.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {
                val alpha = 1.0f - 0.3f * sin(positionOffset * Math.PI).toFloat()
                backgroundPlane.alpha = alpha
            }

            override fun onPageSelected(position: Int) {
                updateDaySummary(position)
            }
        })
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
            setSegmentActive(1)
            filterByType(Bill.TYPE_EXPENSE)
        }
        btnFilterIncome.setOnClickListener {
            setSegmentActive(2)
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
        val dialogView = layoutInflater.inflate(R.layout.dialog_month_picker, null)
        val yearPicker = dialogView.findViewById<android.widget.NumberPicker>(R.id.pickerYear)
        val monthPicker = dialogView.findViewById<android.widget.NumberPicker>(R.id.pickerMonth)

        yearPicker.minValue = 2000
        yearPicker.maxValue = 2099
        yearPicker.value = currentYear
        yearPicker.wrapSelectorWheel = false

        monthPicker.minValue = 1
        monthPicker.maxValue = 12
        monthPicker.value = currentMonth
        monthPicker.displayedValues = (1..12).map { "${it}月" }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("选择月份")
            .setView(dialogView)
            .setPositiveButton("确定") { _, _ ->
                currentYear = yearPicker.value
                currentMonth = monthPicker.value
                refreshBillData()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun refreshBillData() {
        tvYearMonth.text = "${currentYear}年${currentMonth}月"

        val days = billDao.getDaysInMonth(currentYear, currentMonth)
        dayPagerAdapter.submitDays(days)

        val (expense, income) = billDao.getMonthSummary(currentYear, currentMonth)
        tvHeaderExpense.text = "支出 ¥%.0f".format(expense)
        tvHeaderIncome.text = "收入 ¥%.0f".format(income)

        if (days.isEmpty()) {
            tvEmpty.visibility = View.VISIBLE
            vpDayBills.visibility = View.GONE
            daySummary.visibility = View.GONE
        } else {
            tvEmpty.visibility = View.GONE
            vpDayBills.visibility = View.VISIBLE
            daySummary.visibility = View.VISIBLE
            updateDaySummary(0)
        }
    }

    private fun filterByType(type: Int) {
        val typeName = if (type == Bill.TYPE_EXPENSE) "支出" else "收入"
        tvYearMonth.text = "${currentYear}年${currentMonth}月 [$typeName]"

        val allDays = billDao.getDaysInMonth(currentYear, currentMonth)
        val filteredDays = allDays.mapNotNull { day ->
            val filteredBills = day.bills.filter { it.type == type }
            if (filteredBills.isEmpty()) return@mapNotNull null
            val expense = if (type == Bill.TYPE_EXPENSE) filteredBills.sumOf { it.money } else 0.0
            val income = if (type == Bill.TYPE_INCOME) filteredBills.sumOf { it.money } else 0.0
            day.copy(bills = filteredBills, totalExpense = expense, totalIncome = income)
        }

        dayPagerAdapter.submitDays(filteredDays)

        val totalExpense = filteredDays.sumOf { it.totalExpense }
        val totalIncome = filteredDays.sumOf { it.totalIncome }
        tvHeaderExpense.text = "支出 ¥%.0f".format(totalExpense)
        tvHeaderIncome.text = "收入 ¥%.0f".format(totalIncome)

        if (filteredDays.isEmpty()) {
            tvEmpty.visibility = View.VISIBLE
            vpDayBills.visibility = View.GONE
            daySummary.visibility = View.GONE
        } else {
            tvEmpty.visibility = View.GONE
            vpDayBills.visibility = View.VISIBLE
            daySummary.visibility = View.VISIBLE
            updateDaySummary(0)
        }
    }

    // ==================== Navigation ====================

    private fun setupBottomNav() {
        findViewById<FrameLayout>(R.id.fabAdd).setOnClickListener {
            startActivity(Intent(this, AddBillActivity::class.java))
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
        vpDayBills.visibility = View.VISIBLE
        daySummary.visibility = View.VISIBLE
        tvEmpty.visibility = View.GONE
        headerLayer.visibility = View.VISIBLE
        refreshBillData()
    }

    private fun loadFragment(fragment: Fragment) {
        findViewById<View>(R.id.fragmentContainer).visibility = View.VISIBLE
        vpDayBills.visibility = View.GONE
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
        // Layer 1: Diorama (custom image or procedural scene)
        val dioramaUri = themeManager.getCustomDioramaImageUri()
        if (dioramaUri != null) {
            dioramaLayer.applyCustomImage(dioramaUri)
        } else {
            dioramaLayer.applyPalette(palette)
        }

        // Layer 2: Background Plane glass morphism (or custom image)
        if (themeManager.isCustomBackgroundEnabled()) {
            applyPlaneImageBackground()
        } else {
            applyPlaneTheme(palette)
        }

        // Compute auto-contrast colors
        val planeComposite = computePlaneCompositeColor(palette)
        currentTextColor = AutoContrast.textColorFor(planeComposite)
        currentSecondaryColor = AutoContrast.secondaryTextColorFor(planeComposite)
        val shadowColor = AutoContrast.textShadowColor(planeComposite)

        // Layer 3: Header text + shadows
        tvYearMonth.setTextColor(currentTextColor)
        tvYearMonth.setShadowLayer(6f, 0f, 2f, shadowColor)
        tvHeaderExpense.setTextColor(palette.expenseColor)
        tvHeaderExpense.setShadowLayer(4f, 0f, 1f, shadowColor)
        tvHeaderIncome.setTextColor(palette.incomeColor)
        tvHeaderIncome.setShadowLayer(4f, 0f, 1f, shadowColor)

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

        // Bill item text colors + shadow
        dayPagerAdapter.setTextColors(currentTextColor, currentSecondaryColor, shadowColor)

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
        try {
            val bitmap = android.provider.MediaStore.Images.Media.getBitmap(contentResolver, uri)
            val opacity = themeManager.getBgOpacity()
            val alpha = (255 * opacity / 100).coerceIn(0, 255)
            val cornerRadius = resources.getDimension(R.dimen.plane_corner_radius)

            val output = android.graphics.Bitmap.createBitmap(bitmap.width, bitmap.height, android.graphics.Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(output)
            val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
            val rect = android.graphics.RectF(0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat())
            canvas.drawRoundRect(rect, cornerRadius, cornerRadius, paint)
            paint.xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.SRC_IN)
            canvas.drawBitmap(bitmap, 0f, 0f, paint)

            backgroundPlane.background = android.graphics.drawable.BitmapDrawable(resources, output).apply {
                this.alpha = alpha
            }
        } catch (_: Exception) {
            applyPlaneTheme(themeManager.getEffectivePalette())
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
