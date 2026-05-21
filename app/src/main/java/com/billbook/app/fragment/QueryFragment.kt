package com.billbook.app.fragment

import android.app.DatePickerDialog
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
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.github.mikephil.charting.formatter.PercentFormatter
import com.github.mikephil.charting.utils.ColorTemplate
import java.text.SimpleDateFormat
import java.util.*

class QueryFragment : Fragment() {

    private var _binding: FragmentQueryBinding? = null
    private val binding get() = _binding!!
    private lateinit var billDao: BillDao

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
        val mode = tm.getTextColorMode()
        BackgroundApplier.applyToFragment(view, tm, mode)
        FrostGlassApplier.applyToFragment(requireActivity(), view, mode)
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
            DatePickerDialog(requireContext(), { _, year, month, day ->
                calStart.set(year, month, day, 0, 0, 0)
                updateDateDisplay()
            }, calStart.get(Calendar.YEAR), calStart.get(Calendar.MONTH), calStart.get(Calendar.DAY_OF_MONTH)).show()
        }

        binding.tvEndDate.setOnClickListener {
            DatePickerDialog(requireContext(), { _, year, month, day ->
                calEnd.set(year, month, day, 23, 59, 59)
                updateDateDisplay()
            }, calEnd.get(Calendar.YEAR), calEnd.get(Calendar.MONTH), calEnd.get(Calendar.DAY_OF_MONTH)).show()
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
        val total = categoryMap.values.sum()
        val entries = categoryMap.map { PieEntry((it.value / total * 100).toFloat(), it.key) }
        val textColor = requireContext().getColor(com.billbook.app.R.color.text_primary)
        val dataSet = PieDataSet(entries, "").apply {
            colors = ColorTemplate.MATERIAL_COLORS.toList()
            valueTextSize = 11f
            valueTextColor = textColor
        }
        val pieData = PieData(dataSet).apply {
            setValueFormatter(PercentFormatter())
        }

        binding.pieChartQuery.apply {
            data = pieData
            description.text = "分类支出占比"
            description.textSize = 12f
            isRotationEnabled = true
            setEntryLabelTextSize(10f)
            animateY(800)
            invalidate()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
