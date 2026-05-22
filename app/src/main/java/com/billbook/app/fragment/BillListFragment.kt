package com.billbook.app.fragment

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.widget.TextView
import com.billbook.app.BillDetailActivity
import com.billbook.app.util.ThemeManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.billbook.app.R
import com.billbook.app.adapter.BillAdapter
import com.billbook.app.databinding.FragmentBillListBinding
import com.billbook.app.db.BillDao
import com.billbook.app.model.Bill
import java.util.Calendar

class BillListFragment : Fragment() {

    private var _binding: FragmentBillListBinding? = null
    private val binding get() = _binding!!
    private lateinit var billDao: BillDao
    private lateinit var adapter: BillAdapter
    private var currentYear: Int = 0
    private var currentMonth: Int = 0

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentBillListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        billDao = BillDao(requireContext())

        val cal = Calendar.getInstance()
        currentYear = cal.get(Calendar.YEAR)
        currentMonth = cal.get(Calendar.MONTH) + 1

        setupRecyclerView()
        setupFilter()
        refreshData()
    }

    private fun setupRecyclerView() {
        adapter = BillAdapter(
            onItemClick = { bill -> showBillDetail(bill) },
            onItemLongClick = { bill -> showDeleteDialog(bill) }
        )
        val layoutManager = LinearLayoutManager(requireContext())
        binding.rvBills.layoutManager = layoutManager
        binding.rvBills.adapter = adapter
        binding.rvBills.addItemDecoration(BillAdapter.DayDividerDecoration())

        // 滚动时更新alpha渐变效果
        binding.rvBills.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                val firstVisible = layoutManager.findFirstVisibleItemPosition()
                if (firstVisible >= 0) {
                    adapter.setTopDayGroup(adapter.getDayGroupForPosition(firstVisible))
                }
            }
        })
    }

    private fun setupFilter() {
        val activity = requireActivity()
        activity.findViewById<View>(R.id.btnFilterAll)?.setOnClickListener { refreshData() }
        activity.findViewById<View>(R.id.btnFilterExpense)?.setOnClickListener { filterByType(Bill.TYPE_EXPENSE) }
        activity.findViewById<View>(R.id.btnFilterIncome)?.setOnClickListener { filterByType(Bill.TYPE_INCOME) }

        activity.findViewById<View>(R.id.tvYearMonth)?.setOnClickListener { showMonthPickerDialog() }
    }

    private fun showMonthPickerDialog() {
        val cal = java.util.Calendar.getInstance()
        cal.set(currentYear, currentMonth - 1, 1, 0, 0, 0)
        com.billbook.app.animation.AnimeDatePickerDialog(requireContext(), cal.timeInMillis) { year, month, _, _, _ ->
            currentYear = year
            currentMonth = month + 1
            refreshData()
        }.show()
    }

    private fun refreshData() {
        val activity = requireActivity()
        activity.findViewById<TextView>(R.id.tvYearMonth)?.text = "${currentYear}年${currentMonth}月"
        val bills = billDao.getByMonth(currentYear, currentMonth)
        adapter.submitList(bills)

    }

    private fun filterByType(type: Int) {
        requireActivity().findViewById<TextView>(R.id.tvYearMonth)?.text = "${currentYear}年${currentMonth}月"
        val bills = billDao.getByMonth(currentYear, currentMonth).filter { it.type == type }
        adapter.submitList(bills)
    }

    private fun showBillDetail(bill: Bill) {
        val intent = Intent(requireContext(), BillDetailActivity::class.java)
        intent.putExtra("bill_id", bill.id)
        startActivity(intent)
    }

    private fun showDeleteDialog(bill: Bill) {
        AlertDialog.Builder(requireContext())
            .setTitle("删除账单")
            .setMessage("确定要删除这条 ${bill.categoryName} ¥${bill.money} 的记录吗？")
            .setPositiveButton("删除") { _, _ ->
                billDao.delete(bill.id)
                refreshData()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    override fun onResume() {
        super.onResume()
        refreshData()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
