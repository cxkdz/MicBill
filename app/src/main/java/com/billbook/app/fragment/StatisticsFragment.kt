package com.billbook.app.fragment

import android.graphics.Color
import android.graphics.Typeface
import com.billbook.app.util.AppExecutors
import com.billbook.app.util.AutoContrast
import com.billbook.app.util.ThemeManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.billbook.app.databinding.FragmentStatisticsBinding
import com.billbook.app.db.BillDao
import com.billbook.app.model.Bill
import com.github.mikephil.charting.components.Legend
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.*
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.github.mikephil.charting.formatter.ValueFormatter
import com.github.mikephil.charting.highlight.Highlight
import com.github.mikephil.charting.listener.OnChartValueSelectedListener
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class StatisticsFragment : Fragment() {

    private var _binding: FragmentStatisticsBinding? = null
    private val binding get() = _binding!!
    private lateinit var billDao: BillDao
    private val dateFormat = SimpleDateFormat("yyyy年MM月", Locale.CHINA)
    private var showPercentage = true
    private var cachedRankings: List<Triple<String, String, Double>> = emptyList()
    private var cachedYear = 0
    private var cachedMonth = 0
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

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentStatisticsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val tm = ThemeManager(requireContext())
        textColorMode = tm.getTextColorMode()
        com.billbook.app.util.BackgroundApplier.applyToFragment(view, tm, textColorMode)
        com.billbook.app.util.FrostGlassApplier.applyToFragment(requireActivity(), view, textColorMode)
        billDao = BillDao(requireContext())

        binding.btnPieToggle.setOnClickListener {
            showPercentage = !showPercentage
            binding.btnPieToggle.text = if (showPercentage) "百分比" else "金额"
            updatePieChart()
        }
        // 首次刷新交给 onResume，避免 onViewCreated + onResume 连发两次查库
    }

    /** 每次刷新取一个序号，只让最后一次结果落到 UI，防止快速切页时旧数据覆盖新数据 */
    private var refreshSeq = 0

    override fun onResume() {
        super.onResume()
        refreshData()
    }

    fun refreshData() {
        val seq = ++refreshSeq
        val cal = Calendar.getInstance()
        val year = cal.get(Calendar.YEAR)
        val month = cal.get(Calendar.MONTH) + 1

        binding.tvMonth.text = dateFormat.format(cal.time)

        // 在主线程捕获 DAO 引用，避免后台线程直接读 lateinit 字段
        val dao = billDao
        AppExecutors.io(onError = {
            if (_binding != null && isAdded) {
                android.widget.Toast.makeText(
                    requireContext(), "统计加载失败，请重试", android.widget.Toast.LENGTH_SHORT
                ).show()
            }
        }) {
            // 原本 8 次同步查库（1 次汇总 + 1 次排行 + 6 次趋势）全部挤在主线程
            val summary = dao.getMonthSummary(year, month)
            val rankings = dao.getCategoryRanking(year, month, Bill.TYPE_EXPENSE)
            val trend = (5 downTo 0).map { offset ->
                val c = Calendar.getInstance()
                c.add(Calendar.MONTH, -offset)
                val (e, i) = dao.getMonthSummary(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1)
                Triple(c.get(Calendar.MONTH) + 1, e, i)
            }
            AppExecutors.main {
                if (_binding == null || !isAdded || seq != refreshSeq) return@main
                binding.tvTotalExpense.text = String.format("¥%.2f", summary.first)
                binding.tvTotalIncome.text = String.format("¥%.2f", summary.second)
                binding.tvBalance.text = String.format("¥%.2f", summary.second - summary.first)

                cachedYear = year
                cachedMonth = month
                cachedRankings = rankings
                updatePieChart()
                loadMonthlyTrendChart(trend)
            }
        }
    }

    private fun updatePieChart() {
        if (cachedRankings.isEmpty()) {
            binding.pieChart.apply {
                setNoDataText("暂无消费数据")
                setNoDataTextColor(Color.parseColor("#B0FFFFFF"))
                invalidate()
            }
            return
        }

        val total = cachedRankings.sumOf { it.third }
        val entries = if (showPercentage) {
            cachedRankings.map { PieEntry((it.third / total * 100).toFloat(), it.second) }
        } else {
            cachedRankings.map { PieEntry(it.third.toFloat(), it.second) }
        }

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
            valueFormatter = if (showPercentage) {
                object : ValueFormatter() {
                    override fun getFormattedValue(value: Float): String = "%.1f%%".format(value)
                }
            } else {
                object : ValueFormatter() {
                    override fun getFormattedValue(value: Float): String = "¥%.0f".format(value)
                }
            }
            yValuePosition = PieDataSet.ValuePosition.OUTSIDE_SLICE
        }

        val pieData = PieData(dataSet)

        binding.pieChart.apply {
            data = pieData
            description.isEnabled = false // 不显示描述文字
            isRotationEnabled = true
            rotationAngle = -90f // 从顶部开始
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
            centerText = if (showPercentage) "消费\n占比" else "消费\n金额"
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

            // 动画：从中心旋转展开
            animateXY(1000, 1000)

            // 点击高亮回调
            setOnChartValueSelectedListener(object : OnChartValueSelectedListener {
                override fun onValueSelected(e: Entry?, h: Highlight?) {
                    // 高亮时扇形块自动外移（selectionShift 已设置）
                }

                override fun onNothingSelected() {
                    // 取消选择
                }
            })

            invalidate()
        }
    }

    private fun loadMonthlyTrendChart(trend: List<Triple<Int, Double, Double>>) {
        val entriesExpense = mutableListOf<Entry>()
        val entriesIncome = mutableListOf<Entry>()
        val labels = mutableListOf<String>()

        // 数据已在后台查好，这里只负责组装图表，保证 UI 操作都在主线程
        trend.forEachIndexed { index, (m, expense, income) ->
            entriesExpense.add(Entry(index.toFloat(), expense.toFloat()))
            entriesIncome.add(Entry(index.toFloat(), income.toFloat()))
            labels.add("${m}月")
        }

        val pageBg = com.billbook.app.util.BudgetThemeApplier.getPageComposite(requireActivity())
        val isDark = AutoContrast.isDark(pageBg)
        val axisColor = when (textColorMode) {
            1 -> Color.parseColor("#803D4A5C")
            2 -> Color.parseColor("#80FFFFFF")
            else -> if (isDark) Color.parseColor("#80FFFFFF") else Color.parseColor("#803D4A5C")
        }
        val labelColor = when (textColorMode) {
            1 -> Color.parseColor("#C03D4A5C")
            2 -> Color.parseColor("#C0FFFFFF")
            else -> if (isDark) Color.parseColor("#C0FFFFFF") else Color.parseColor("#C03D4A5C")
        }

        // 收支折线随主题配色（语义红/绿 + 主题色轻微染色），不再写死
        val palette = com.billbook.app.util.ThemeManager(requireContext()).getEffectivePalette()
        val expenseColor = palette.expenseColor
        val incomeColor = palette.incomeColor

        val expenseDataSet = LineDataSet(entriesExpense, "支出").apply {
            setColor(expenseColor)
            setCircleColor(expenseColor)
            setLineWidth(2.5f)
            setCircleRadius(4f)
            valueTextSize = 9f
            setValueTextColor(labelColor)
            mode = LineDataSet.Mode.CUBIC_BEZIER
            setDrawFilled(true)
            setFillColor(expenseColor)
            setFillAlpha(30)
        }

        val incomeDataSet = LineDataSet(entriesIncome, "收入").apply {
            setColor(incomeColor)
            setCircleColor(incomeColor)
            setLineWidth(2.5f)
            setCircleRadius(4f)
            valueTextSize = 9f
            setValueTextColor(labelColor)
            mode = LineDataSet.Mode.CUBIC_BEZIER
            setDrawFilled(true)
            setFillColor(incomeColor)
            setFillAlpha(30)
        }

        binding.lineChart.apply {
            data = LineData(expenseDataSet, incomeDataSet)
            description.isEnabled = false

            xAxis.apply {
                valueFormatter = IndexAxisValueFormatter(labels)
                position = XAxis.XAxisPosition.BOTTOM
                granularity = 1f
                textColor = axisColor
                axisLineColor = axisColor
                gridColor = Color.parseColor("#10FFFFFF")
                setDrawGridLines(false)
            }

            axisLeft.apply {
                textColor = axisColor
                axisLineColor = axisColor
                gridColor = Color.parseColor("#10FFFFFF")
                setDrawGridLines(true)
            }

            axisRight.isEnabled = false

            legend.apply {
                textColor = labelColor
                textSize = 11f
                form = Legend.LegendForm.LINE
                formLineWidth = 2f
            }

            isHighlightPerTapEnabled = true
            animateXY(800, 800)
            invalidate()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
