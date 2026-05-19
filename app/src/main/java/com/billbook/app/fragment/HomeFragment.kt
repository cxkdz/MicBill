package com.billbook.app.fragment

import com.billbook.app.util.BackgroundApplier
import com.billbook.app.util.ThemeManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.billbook.app.databinding.FragmentHomeBinding
import com.billbook.app.db.BillDao
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private lateinit var billDao: BillDao
    private val dateFormat = SimpleDateFormat("yyyy年MM月", Locale.CHINA)

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        BackgroundApplier.applyToFragment(view, ThemeManager(requireContext()))
        billDao = BillDao(requireContext())
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
