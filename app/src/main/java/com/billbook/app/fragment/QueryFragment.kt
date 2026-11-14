package com.billbook.app.fragment

import com.billbook.app.animation.AnimeDatePickerDialog
import com.billbook.app.util.BackgroundApplier
import com.billbook.app.util.FrostGlassApplier
import com.billbook.app.util.ThemeManager
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.billbook.app.adapter.BillAdapter
import com.billbook.app.databinding.FragmentQueryBinding
import com.billbook.app.db.BillDao
import com.billbook.app.model.Bill
import android.graphics.Typeface
import com.billbook.app.util.AppExecutors
import com.billbook.app.util.AutoContrast
import com.github.mikephil.charting.components.Legend
import com.github.mikephil.charting.data.*
import com.github.mikephil.charting.formatter.ValueFormatter
import com.github.mikephil.charting.highlight.Highlight
import com.github.mikephil.charting.listener.OnChartValueSelectedListener
import java.text.SimpleDateFormat
import java.util.*

class QueryFragment : Fragment() {

    private var _binding: FragmentQueryBinding? = null
    private val binding get() = _binding!!
    private lateinit var billDao: BillDao
    private var textColorMode = 0

    // 二次元可爱风配色（高饱和度、区分度高）
    private val animeColors = intArrayOf(
        Color.parseColor("#FF6B8A"), // 粉红
        Color.parseColor("#7ED4FF"), // 天蓝
        Color.parseColor("#FFD166"), // 暖黄
        Color.parseColor("#95E1A3"), // 薄荷绿
        Color.parseColor("#C4A8FF"), // 薰衣草紫
        Color.parseColor("#FFA07A"), // 橘粉
        Color.parseColor("#87CEEB"), // 浅蓝
        Color.parseColor("#FFB6C1"), // 浅粉
        Color.parseColor("#98D8C8"), // 青绿
        Color.parseColor("#F0E68C"), // 卡其黄
        Color.parseColor("#DDA0DD"), // 梅红
        Color.parseColor("#B0E0E6")  // 粉蓝
    )

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA)
    private val calStart = Calendar.getInstance()
    private val calEnd = Calendar.getInstance()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentQueryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val tm = ThemeManager(requireContext())
        textColorMode = tm.getTextColorMode()
        BackgroundApplier.applyToFragment(view, tm, textColorMode)
        FrostGlassApplier.applyToFragment(requireActivity(), view, textColorMode)
        billDao = BillDao(requireContext())

        // 默认查询本月
        calStart.set(Calendar.DAY_OF_MONTH, 1)
        calStart.set(Calendar.HOUR_OF_DAY, 0)
        calStart.set(Calendar.MINUTE, 0)
        calStart.set(Calendar.SECOND, 0)

        updateDateDisplay()
        setupDatePickers()
        setupQueryButton()
        setupBillList()
    }

    // ==================== 账单明细列表 ====================

    private var billAdapter: BillAdapter? = null

    private fun setupBillList() {
        val pageBg = com.billbook.app.util.BudgetThemeApplier.getPageComposite(requireActivity())
        val primary = AutoContrast.textColorFor(pageBg)
        val secondary = AutoContrast.hintTextColorFor(pageBg)
        billAdapter = BillAdapter(
            onItemClick = { bill ->
                startActivity(
                    android.content.Intent(requireContext(), com.billbook.app.BillDetailActivity::class.java)
                        .putExtra("bill_id", bill.id)
                )
            },
            onItemLongClick = { }
        ).apply {
            setTextColors(primary, secondary)
        }
        binding.rvQueryBills.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = billAdapter
        }
    }

    private fun setupDatePickers() {
        binding.tvStartDate.setOnClickListener {
            AnimeDatePickerDialog(requireContext(), calStart.timeInMillis) { year, month, day, _, _ ->
                calStart.set(year, month, day, 0, 0, 0)
                calStart.set(Calendar.MILLISECOND, 0)
                updateDateDisplay()
            }.show()
        }

        binding.tvEndDate.setOnClickListener {
            AnimeDatePickerDialog(requireContext(), calEnd.timeInMillis) { year, month, day, _, _ ->
                calEnd.set(year, month, day, 23, 59, 59)
                calEnd.set(Calendar.MILLISECOND, 999)
                updateDateDisplay()
            }.show()
        }
    }

    private fun updateDateDisplay() {
        binding.tvStartDate.text = "开始：${dateFormat.format(calStart.time)}"
        binding.tvEndDate.text = "结束：${dateFormat.format(calEnd.time)}"
    }

    private fun setupQueryButton() {
        binding.btnQuery.setOnClickListener {
            performQuery()
        }
    }

    private fun performQuery() {
        val startTime = calStart.timeInMillis
        val endTime = calEnd.timeInMillis

        if (startTime > endTime) {
            android.widget.Toast.makeText(requireContext(), "开始日期不能晚于结束日期", android.widget.Toast.LENGTH_SHORT).show()
            return
        }

        // SimpleDateFormat 非线程安全，日期文案在主线程预先算好再带进后台
        val startLabel = dateFormat.format(calStart.time)
        val endLabel = dateFormat.format(calEnd.time)
        val dao = billDao

        AppExecutors.io(onError = {
            if (_binding != null && isAdded) {
                android.widget.Toast.makeText(
                    requireContext(), "查询失败，请重试", android.widget.Toast.LENGTH_SHORT
                ).show()
            }
        }) {
            val allBills = dao.getByDateRange(startTime, endTime)
            if (allBills.isEmpty()) {
                AppExecutors.main {
                    if (_binding == null || !isAdded) return@main
                    binding.cardSummary.visibility = View.GONE
                    binding.cardCategories.visibility = View.GONE
                    binding.cardPie.visibility = View.GONE
                    binding.cardBills.visibility = View.VISIBLE
                    binding.tvQueryEmpty.visibility = View.VISIBLE
                    binding.tvBillsTitle.visibility = View.GONE
                    billAdapter?.submitList(emptyList())
                    binding.pieChartQuery.clear()
                }
                return@io
            }

            var totalExpense = 0.0
            var totalIncome = 0.0
            val categoryMap = mutableMapOf<String, Double>() // 分类→金额

            allBills.forEach { bill ->
                if (bill.type == Bill.TYPE_EXPENSE) {
                    totalExpense += bill.money
                    categoryMap[bill.categoryName] = (categoryMap[bill.categoryName] ?: 0.0) + bill.money
                } else {
                    totalIncome += bill.money
                }
            }

            val sortedCategories = categoryMap.entries.sortedByDescending { it.value }
            val billCount = allBills.size
            val net = totalIncome - totalExpense

            AppExecutors.main {
                if (_binding == null || !isAdded) return@main
                renderSummary(startLabel, endLabel, billCount, totalExpense, totalIncome, net)
                renderCategories(sortedCategories, totalExpense)
                binding.cardBills.visibility = View.VISIBLE
                binding.cardPie.visibility = View.VISIBLE
                binding.tvBillsTitle.visibility = View.VISIBLE
                binding.tvQueryEmpty.visibility = View.GONE
                billAdapter?.submitList(allBills)
                loadQueryPieChart(categoryMap)
            }
        }
    }

    private fun renderSummary(
        startLabel: String, endLabel: String, count: Int,
        expense: Double, income: Double, net: Double
    ) {
        binding.cardSummary.visibility = View.VISIBLE
        binding.tvQueryRange.text = "$startLabel ~ $endLabel · 共 $count 笔"
        binding.tvQueryExpense.text = "¥%.2f".format(expense)
        binding.tvQueryIncome.text = "¥%.2f".format(income)
        binding.tvQueryNet.text = if (net < 0) "-¥%.2f".format(-net) else "¥%.2f".format(net)
        binding.tvQueryNet.setTextColor(
            when {
                net > 0 -> androidx.core.content.ContextCompat.getColor(requireContext(), com.billbook.app.R.color.income_green)
                net < 0 -> androidx.core.content.ContextCompat.getColor(requireContext(), com.billbook.app.R.color.expense_red)
                else -> AutoContrast.textColorFor(
                    com.billbook.app.util.BudgetThemeApplier.getPageComposite(requireActivity())
                )
            }
        )
    }

    /** 分类明细：名称 + 占比 + 金额 + 占比条，颜色与饼图同一套色板 */
    private fun renderCategories(sorted: List<Map.Entry<String, Double>>, totalExpense: Double) {
        binding.listCategories.removeAllViews()
        if (sorted.isEmpty()) {
            binding.cardCategories.visibility = View.GONE
            return
        }
        binding.cardCategories.visibility = View.VISIBLE
        val density = resources.displayMetrics.density
        sorted.forEachIndexed { index, (name, amount) ->
            val row = com.billbook.app.databinding.ItemQueryCategoryBinding.inflate(
                layoutInflater, binding.listCategories, false
            )
            val percent = if (totalExpense > 0) amount / totalExpense * 100 else 0.0
            row.tvCategoryName.text = name
            row.tvCategoryPercent.text = "%.0f%%".format(percent)
            row.tvCategoryAmount.text = "¥%.2f".format(amount)
            val color = animeColors[index % animeColors.size]
            row.pbCategory.apply {
                progress = percent.toInt().coerceIn(0, 100)
                progressTintList = android.content.res.ColorStateList.valueOf(color)
            }
            binding.listCategories.addView(row.root)
        }
        // 底部留白
        binding.listCategories.setPadding(0, 0, 0, (4 * density).toInt())
    }

    private fun loadQueryPieChart(categoryMap: Map<String, Double>) {
        if (categoryMap.isEmpty()) {
            binding.pieChartQuery.apply {
                setNoDataText("暂无消费数据")
                setNoDataTextColor(Color.parseColor("#B0FFFFFF"))
                invalidate()
            }
            return
        }

        val total = categoryMap.values.sum()
        val entries = categoryMap.map { PieEntry((it.value / total * 100).toFloat(), it.key) }

        val pageBg = com.billbook.app.util.BudgetThemeApplier.getPageComposite(requireActivity())
        val isDark = AutoContrast.isDark(pageBg)
        val valueTextColor = when (textColorMode) {
            1 -> Color.parseColor("#E03D4A5C")
            2 -> Color.parseColor("#E0FFFFFF")
            else -> if (isDark) Color.parseColor("#E0FFFFFF") else Color.parseColor("#E03D4A5C")
        }
        val labelColor = when (textColorMode) {
            1 -> Color.parseColor("#C03D4A5C")
            2 -> Color.parseColor("#C0FFFFFF")
            else -> if (isDark) Color.parseColor("#C0FFFFFF") else Color.parseColor("#C03D4A5C")
        }

        val dataSet = PieDataSet(entries, "").apply {
            colors = animeColors.take(entries.size)
            valueTextSize = 11f
            setValueTextColor(Color.WHITE)
            setValueLinePart1Length(0.4f)
            setValueLinePart2Length(0.6f)
            setValueLineColor(Color.parseColor("#50FFFFFF"))
            setValueLineWidth(1.2f)
            sliceSpace = 2f
            selectionShift = 10f
            valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String = "%.1f%%".format(value)
            }
            yValuePosition = PieDataSet.ValuePosition.OUTSIDE_SLICE
        }

        val pieData = PieData(dataSet)

        binding.pieChartQuery.apply {
            data = pieData
            description.isEnabled = false
            isRotationEnabled = true
            rotationAngle = -90f
            isHighlightPerTapEnabled = true

            // 扇形块内部文字
            setDrawEntryLabels(true)
            setEntryLabelColor(Color.WHITE)
            setEntryLabelTextSize(10f)
            setEntryLabelTypeface(Typeface.DEFAULT_BOLD)

            // 中心圆（甜甜圈效果）
            isDrawHoleEnabled = true
            holeRadius = 45f
            transparentCircleRadius = 50f
            setHoleColor(Color.TRANSPARENT)
            setTransparentCircleColor(Color.parseColor("#15FFFFFF"))
            setTransparentCircleAlpha(50)

            // 中心文字
            setDrawCenterText(true)
            centerText = "消费\n占比"
            setCenterTextSize(13f)
            setCenterTextColor(labelColor)
            setCenterTextTypeface(Typeface.DEFAULT_BOLD)

            // 图例
            legend.apply {
                isEnabled = true
                textColor = labelColor
                textSize = 11f
                form = Legend.LegendForm.CIRCLE
                formSize = 10f
                xEntrySpace = 12f
                yEntrySpace = 6f
                verticalAlignment = Legend.LegendVerticalAlignment.BOTTOM
                horizontalAlignment = Legend.LegendHorizontalAlignment.CENTER
                orientation = Legend.LegendOrientation.HORIZONTAL
                setDrawInside(false)
            }

            // 动画
            animateXY(1000, 1000)

            // 点击高亮回调
            setOnChartValueSelectedListener(object : OnChartValueSelectedListener {
                override fun onValueSelected(e: Entry?, h: Highlight?) {}
                override fun onNothingSelected() {}
            })

            invalidate()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
