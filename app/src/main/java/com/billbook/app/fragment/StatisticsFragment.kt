package com.billbook.app.fragment

import android.graphics.Color
import com.billbook.app.util.ThemeManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.billbook.app.databinding.FragmentStatisticsBinding
import com.billbook.app.db.BillDao
import com.billbook.app.model.Bill
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.*
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.github.mikephil.charting.formatter.ValueFormatter
import com.github.mikephil.charting.utils.ColorTemplate
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

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentStatisticsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        com.billbook.app.util.BackgroundApplier.applyToFragment(view, com.billbook.app.util.ThemeManager(requireContext()))
        com.billbook.app.util.FrostGlassApplier.applyToFragment(requireActivity(), view)
        billDao = BillDao(requireContext())

        binding.btnPieToggle.setOnClickListener {
            showPercentage = !showPercentage
            binding.btnPieToggle.text = if (showPercentage) "百分比" else "金额"
            updatePieChart()
        }

        refreshData()
    }

    override fun onResume() {
        super.onResume()
        refreshData()
    }

    fun refreshData() {
        val cal = Calendar.getInstance()
        val year = cal.get(Calendar.YEAR)
        val month = cal.get(Calendar.MONTH) + 1

        binding.tvMonth.text = dateFormat.format(cal.time)

        val (expense, income) = billDao.getMonthSummary(year, month)
        binding.tvTotalExpense.text = String.format("¥%.2f", expense)
        binding.tvTotalIncome.text = String.format("¥%.2f", income)
        binding.tvBalance.text = String.format("¥%.2f", income - expense)

        // 加载图表
        cachedYear = year
        cachedMonth = month
        cachedRankings = billDao.getCategoryRanking(year, month, Bill.TYPE_EXPENSE)
        updatePieChart()
        loadMonthlyTrendChart()
    }

    private fun updatePieChart() {
        if (cachedRankings.isEmpty()) {
            binding.pieChart.setNoDataText("暂无消费数据")
            return
        }

        val total = cachedRankings.sumOf { it.third }
        val entries = if (showPercentage) {
            cachedRankings.map { PieEntry((it.third / total * 100).toFloat(), it.second) }
        } else {
            cachedRankings.map { PieEntry(it.third.toFloat(), it.second) }
        }
        val textColor = requireContext().getColor(com.billbook.app.R.color.text_primary)
        val dataSet = PieDataSet(entries, "").apply {
            colors = ColorTemplate.MATERIAL_COLORS.toList()
            valueTextSize = 12f
            valueTextColor = textColor
        }
        val formatter: ValueFormatter = if (showPercentage) {
            object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String = "%.1f%%".format(value)
            }
        } else {
            object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String = "¥%.0f".format(value)
            }
        }
        val pieData = PieData(dataSet).apply {
            setValueFormatter(formatter)
        }

        binding.pieChart.apply {
            data = pieData
            description.text = "${cachedYear}年${cachedMonth}月消费分类"
            description.textSize = 14f
            isRotationEnabled = true
            setEntryLabelTextSize(11f)
            animateY(800)
            invalidate()
        }
    }

    private fun loadMonthlyTrendChart() {
        val entriesExpense = mutableListOf<Entry>()
        val entriesIncome = mutableListOf<Entry>()
        val labels = mutableListOf<String>()

        for (i in 5 downTo 0) {
            val tempCal = Calendar.getInstance()
            tempCal.add(Calendar.MONTH, -i)
            val y = tempCal.get(Calendar.YEAR)
            val m = tempCal.get(Calendar.MONTH) + 1

            val (expense, income) = billDao.getMonthSummary(y, m)
            entriesExpense.add(Entry((5 - i).toFloat(), expense.toFloat()))
            entriesIncome.add(Entry((5 - i).toFloat(), income.toFloat()))
            labels.add("${m}月")
        }

        val expenseDataSet = LineDataSet(entriesExpense, "支出").apply {
            color = Color.parseColor("#F44336")
            setCircleColor(Color.parseColor("#F44336"))
            lineWidth = 2f
            circleRadius = 4f
            valueTextSize = 10f
        }

        val incomeDataSet = LineDataSet(entriesIncome, "收入").apply {
            color = Color.parseColor("#4CAF50")
            setCircleColor(Color.parseColor("#4CAF50"))
            lineWidth = 2f
            circleRadius = 4f
            valueTextSize = 10f
        }

        binding.lineChart.apply {
            data = LineData(expenseDataSet, incomeDataSet)
            xAxis.valueFormatter = IndexAxisValueFormatter(labels)
            xAxis.position = XAxis.XAxisPosition.BOTTOM
            xAxis.granularity = 1f
            description.text = "近6个月收支趋势"
            animateX(800)
            invalidate()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
