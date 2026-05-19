package com.billbook.app.fragment

import android.app.AlertDialog
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.billbook.app.R
import com.billbook.app.databinding.FragmentBudgetBinding
import com.billbook.app.databinding.ItemBudgetCategoryBinding
import com.billbook.app.model.BudgetCategory
import com.billbook.app.util.BackgroundApplier
import com.billbook.app.util.BudgetManager
import com.billbook.app.util.ThemeManager

class BudgetFragment : Fragment() {

    private var _binding: FragmentBudgetBinding? = null
    private val binding get() = _binding!!
    private lateinit var budgetManager: BudgetManager

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentBudgetBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        BackgroundApplier.applyToFragment(view, ThemeManager(requireContext()))
        budgetManager = BudgetManager(requireContext())

        setupBudgetInput()
        setupCategoryBudget()
        refreshAll()
    }

    private fun setupBudgetInput() {
        binding.btnSetBudget.setOnClickListener {
            showBudgetInputDialog()
        }

        binding.btnClearBudget.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle("确认清除")
                .setMessage("清除总预算将同时删除所有分类预算，确定要清除吗？")
                .setPositiveButton("确定") { _, _ ->
                    budgetManager.clearBudget()
                    refreshAll()
                    Toast.makeText(requireContext(), "预算已清除", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("取消", null)
                .show()
        }
    }

    private fun setupCategoryBudget() {
        binding.btnAddCategory.setOnClickListener {
            if (!budgetManager.isBudgetEnabled()) {
                Toast.makeText(requireContext(), "请先设置总预算", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            showAddCategoryDialog()
        }
    }

    private fun showBudgetInputDialog() {
        val input = EditText(requireContext()).apply {
            hint = "请输入本月预算金额"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            val currentBudget = budgetManager.getBudget()
            if (currentBudget > 0) {
                setText(currentBudget.toString())
            }
        }

        AlertDialog.Builder(requireContext())
            .setTitle("设置本月总预算")
            .setView(input)
            .setPositiveButton("确定") { _, _ ->
                val amount = input.text.toString().toDoubleOrNull()
                if (amount != null && amount > 0) {
                    budgetManager.setBudget(amount)
                    refreshAll()
                    Toast.makeText(requireContext(), "总预算已设置：¥%.2f".format(amount), Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(requireContext(), "请输入有效金额", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showAddCategoryDialog() {
        val available = budgetManager.getAvailableToAllocate()
        if (available <= 0) {
            Toast.makeText(requireContext(), "预算已全部分配完毕", Toast.LENGTH_SHORT).show()
            return
        }

        // 显示分类选择
        val categories = BudgetCategory.SUGGESTED_CATEGORIES.map { "${it.second} ${it.first}" }.toTypedArray()

        AlertDialog.Builder(requireContext())
            .setTitle("选择预算分类")
            .setItems(categories) { _, which ->
                val (name, icon) = BudgetCategory.SUGGESTED_CATEGORIES[which]
                showSetCategoryAmountDialog(name, icon, null)
            }
            .setNeutralButton("自定义") { _, _ ->
                showCustomCategoryDialog()
            }
            .show()
    }

    private fun showCustomCategoryDialog() {
        val input = EditText(requireContext()).apply {
            hint = "输入分类名称（如：健身、宠物）"
        }

        AlertDialog.Builder(requireContext())
            .setTitle("自定义分类")
            .setView(input)
            .setPositiveButton("下一步") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    showIconSelectDialog(name)
                } else {
                    Toast.makeText(requireContext(), "请输入分类名称", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showIconSelectDialog(categoryName: String) {
        val icons = arrayOf("🍜", "🚗", "🛒", "🎮", "🏠", "💊", "📚", "📱", "👔", "🎭", "💪", "🐱", "💄", "✈️", "🎵", "🎬", "⚽", "🎯", "💼", "🔧")

        AlertDialog.Builder(requireContext())
            .setTitle("选择图标")
            .setItems(icons) { _, which ->
                showSetCategoryAmountDialog(categoryName, icons[which], null)
            }
            .show()
    }

    private fun showSetCategoryAmountDialog(name: String, icon: String, existingCategory: BudgetCategory?) {
        val available = if (existingCategory != null) {
            budgetManager.getAvailableToAllocate() + existingCategory.budgetAmount
        } else {
            budgetManager.getAvailableToAllocate()
        }

        val input = EditText(requireContext()).apply {
            hint = "可分配金额：¥%.2f".format(available)
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            existingCategory?.let {
                setText(it.budgetAmount.toString())
            }
        }

        val title = if (existingCategory != null) "修改预算金额" else "设置预算金额"

        AlertDialog.Builder(requireContext())
            .setTitle(title)
            .setMessage("分类：$icon $name\n可分配余额：¥%.2f".format(available))
            .setView(input)
            .setPositiveButton("确定") { _, _ ->
                val amount = input.text.toString().toDoubleOrNull()
                if (amount == null || amount <= 0) {
                    Toast.makeText(requireContext(), "请输入有效金额", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                if (amount > available) {
                    Toast.makeText(requireContext(), "金额超出可分配范围", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                if (existingCategory != null) {
                    val result = budgetManager.updateCategoryBudget(existingCategory.id, amount)
                    Toast.makeText(requireContext(), result, Toast.LENGTH_SHORT).show()
                } else {
                    val result = budgetManager.addCategoryBudget(name, icon, amount)
                    Toast.makeText(requireContext(), result, Toast.LENGTH_SHORT).show()
                }
                refreshAll()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showEditCategoryDialog(info: BudgetManager.CategoryBudgetInfo) {
        val options = arrayOf("修改金额", "删除分类")

        AlertDialog.Builder(requireContext())
            .setTitle("${info.category.icon} ${info.category.name}")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showSetCategoryAmountDialog(info.category.name, info.category.icon, info.category)
                    1 -> {
                        AlertDialog.Builder(requireContext())
                            .setTitle("确认删除")
                            .setMessage("确定要删除「${info.category.name}」分类预算吗？")
                            .setPositiveButton("删除") { _, _ ->
                                budgetManager.deleteCategoryBudget(info.category.id)
                                refreshAll()
                                Toast.makeText(requireContext(), "已删除", Toast.LENGTH_SHORT).show()
                            }
                            .setNegativeButton("取消", null)
                            .show()
                    }
                }
            }
            .show()
    }

    private fun refreshAll() {
        refreshBudgetInfo()
        refreshCategoryList()
    }

    private fun refreshBudgetInfo() {
        if (!budgetManager.isBudgetEnabled()) {
            binding.layoutBudgetInfo.visibility = View.GONE
            binding.tvNoBudget.visibility = View.VISIBLE
            binding.layoutAllocatedInfo.visibility = View.GONE
            binding.btnAddCategory.isEnabled = false
            return
        }

        binding.layoutBudgetInfo.visibility = View.VISIBLE
        binding.tvNoBudget.visibility = View.GONE
        binding.btnAddCategory.isEnabled = true

        val info = budgetManager.getBudgetInfo()

        binding.tvBudgetAmount.text = "总预算：¥%.2f".format(info.budget)
        binding.tvSpentAmount.text = "已花费：¥%.2f".format(info.spent)
        binding.tvRemainingAmount.text = "剩余：¥%.2f".format(info.remaining)

        binding.progressBudget.max = 100
        binding.progressBudget.progress = info.percentUsed.toInt().coerceAtMost(100)

        val percentText = "已使用 %.1f%%".format(info.percentUsed)
        binding.tvPercent.text = percentText

        when {
            info.isOverBudget -> {
                binding.tvStatus.text = "已超预算！超出 ¥%.2f".format(-info.remaining)
                binding.tvStatus.setTextColor(resources.getColor(android.R.color.holo_red_dark, null))
                binding.progressBudget.progressTintList = ColorStateList.valueOf(
                    resources.getColor(android.R.color.holo_red_dark, null)
                )
            }
            info.percentUsed >= budgetManager.getAlertPercent() -> {
                binding.tvStatus.text = "预算即将用完，注意控制消费"
                binding.tvStatus.setTextColor(resources.getColor(android.R.color.holo_orange_dark, null))
                binding.progressBudget.progressTintList = ColorStateList.valueOf(
                    resources.getColor(android.R.color.holo_orange_dark, null)
                )
            }
            else -> {
                binding.tvStatus.text = "预算充足，继续加油！"
                binding.tvStatus.setTextColor(resources.getColor(android.R.color.holo_green_dark, null))
                binding.progressBudget.progressTintList = ColorStateList.valueOf(
                    resources.getColor(android.R.color.holo_green_dark, null)
                )
            }
        }

        if (info.dailyBudget > 0) {
            binding.tvDailyBudget.text = "建议每日消费：¥%.2f（剩余%d天）".format(info.dailyBudget, info.daysRemaining)
            binding.tvDailyBudget.visibility = View.VISIBLE
        } else {
            binding.tvDailyBudget.visibility = View.GONE
        }

        // 显示已分配信息
        val allocated = budgetManager.getAllocatedAmount()
        val available = budgetManager.getAvailableToAllocate()
        binding.layoutAllocatedInfo.visibility = View.VISIBLE
        binding.tvAllocated.text = "已分配：¥%.2f".format(allocated)
        binding.tvAvailable.text = "可分配：¥%.2f".format(available)
    }

    private fun refreshCategoryList() {
        binding.layoutCategoryList.removeAllViews()

        val categoryInfos = budgetManager.getCategoryBudgetsInfo()

        if (categoryInfos.isEmpty()) {
            binding.tvNoCategory.visibility = View.VISIBLE
            binding.tvNoCategory.text = if (budgetManager.isBudgetEnabled()) {
                "暂无分类预算\n点击上方按钮添加"
            } else {
                "暂无分类预算\n设置总预算后可添加分类"
            }
            return
        }

        binding.tvNoCategory.visibility = View.GONE

        for (info in categoryInfos) {
            val itemView = LayoutInflater.from(context).inflate(R.layout.item_budget_category, binding.layoutCategoryList, false)
            val itemBinding = ItemBudgetCategoryBinding.bind(itemView)

            itemBinding.tvCategoryIcon.text = info.category.icon
            itemBinding.tvCategoryName.text = info.category.name
            itemBinding.tvCategoryBudget.text = "¥%.2f".format(info.category.budgetAmount)
            itemBinding.tvCategorySpent.text = "已消费：¥%.2f".format(info.spent)
            itemBinding.tvCategoryRemaining.text = "剩余：¥%.2f".format(info.remaining)

            itemBinding.progressCategory.max = 100
            itemBinding.progressCategory.progress = info.percentUsed.toInt().coerceAtMost(100)

            when {
                info.isOverBudget -> {
                    itemBinding.tvCategoryStatus.text = "已超支！"
                    itemBinding.tvCategoryStatus.setTextColor(resources.getColor(android.R.color.holo_red_dark, null))
                    itemBinding.progressCategory.progressTintList = ColorStateList.valueOf(
                        resources.getColor(android.R.color.holo_red_dark, null)
                    )
                }
                info.percentUsed >= 80 -> {
                    itemBinding.tvCategoryStatus.text = "即将用完"
                    itemBinding.tvCategoryStatus.setTextColor(resources.getColor(android.R.color.holo_orange_dark, null))
                    itemBinding.progressCategory.progressTintList = ColorStateList.valueOf(
                        resources.getColor(android.R.color.holo_orange_dark, null)
                    )
                }
                else -> {
                    itemBinding.tvCategoryStatus.text = "预算充足"
                    itemBinding.tvCategoryStatus.setTextColor(resources.getColor(android.R.color.holo_green_dark, null))
                    itemBinding.progressCategory.progressTintList = ColorStateList.valueOf(
                        resources.getColor(android.R.color.holo_green_dark, null)
                    )
                }
            }

            itemBinding.btnEdit.setOnClickListener {
                showEditCategoryDialog(info)
            }

            itemBinding.btnDelete.setOnClickListener {
                AlertDialog.Builder(requireContext())
                    .setTitle("确认删除")
                    .setMessage("确定要删除「${info.category.name}」分类预算吗？")
                    .setPositiveButton("删除") { _, _ ->
                        budgetManager.deleteCategoryBudget(info.category.id)
                        refreshAll()
                        Toast.makeText(requireContext(), "已删除", Toast.LENGTH_SHORT).show()
                    }
                    .setNegativeButton("取消", null)
                    .show()
            }

            binding.layoutCategoryList.addView(itemView)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
