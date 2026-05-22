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

        // 查询该时间范围内的所有账单
        val allBills = billDao.getByDateRange(startTime, endTime)

        if (allBills.isEmpty()) {
            binding.tvQueryResult.text = "该时间段内无账单数据"
            binding.pieChartQuery.clear()
            return
        }

        // 计算总支出和总收入
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

        // 显示汇总结果
        val sb = StringBuilder()
        sb.appendLine("📊 查询结果")
        sb.appendLine("━━━━━━━━━━━━━━━━")
        sb.appendLine("📅 ${dateFormat.format(calStart.time)} ~ ${dateFormat.format(calEnd.time)}")
        sb.appendLine("📝 共 ${allBills.size} 笔账单")
        sb.appendLine("")
        sb.appendLine("💸 总支出：¥%.2f".format(totalExpense))
        sb.appendLine("💰 总收入：¥%.2f".format(totalIncome))
        sb.appendLine("📊 净收支：¥%.2f".format(totalIncome - totalExpense))
        sb.appendLine("")
        sb.appendLine("📂 分类支出明细：")
        sb.appendLine("━━━━━━━━━━━━━━━━")

        categoryMap.entries.sortedByDescending { it.value }.forEach { (name, amount) ->
            val percent = if (totalExpense > 0) amount / totalExpense * 100 else 0.0
            sb.appendLine("  $name：¥%.2f (%.1f%%)".format(amount, percent))
        }

        binding.tvQueryResult.text = sb.toString()

        // 绘制饼图
        loadQueryPieChart(categoryMap)
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
