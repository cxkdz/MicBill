package com.billbook.app.fragment

import com.billbook.app.util.AnimeDialogHelper
import android.graphics.Color
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.billbook.app.R
import com.billbook.app.databinding.FragmentBudgetBinding
import com.billbook.app.databinding.ItemBudgetCategoryBinding
import com.billbook.app.model.BudgetCategory
import com.billbook.app.util.AutoContrast
import com.billbook.app.util.BackgroundApplier
import com.billbook.app.util.BudgetManager
import com.billbook.app.util.BudgetThemeApplier
import com.billbook.app.util.CategoryIconMapper
import com.billbook.app.util.ThemeManager

class BudgetFragment : Fragment() {

    private var _binding: FragmentBudgetBinding? = null
    private val binding get() = _binding!!
    private lateinit var budgetManager: BudgetManager
    private var pageBg: Int = 0
    private var textColorMode: Int = 0

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentBudgetBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val tm = ThemeManager(requireContext())
        textColorMode = tm.getTextColorMode()
        BackgroundApplier.applyToFragment(view, tm, textColorMode)
        budgetManager = BudgetManager(requireContext())

        pageBg = BudgetThemeApplier.getPageComposite(requireActivity())

        // 磨砂玻璃卡片阴影（轻薄，避免遮挡半透明效果）
        binding.layoutBudgetInfo.elevation = 3f * resources.displayMetrics.density

        // 气泡上浮动画 — 仅在预算卡片区域内
        binding.layoutBudgetInfo.post {
            val cardLoc = IntArray(2)
            val bubbleLoc = IntArray(2)
            binding.layoutBudgetInfo.getLocationOnScreen(cardLoc)
            binding.cardBubbleView.getLocationOnScreen(bubbleLoc)
            val left = (cardLoc[0] - bubbleLoc[0]).toFloat()
            val top = (cardLoc[1] - bubbleLoc[1]).toFloat()
            val right = left + binding.layoutBudgetInfo.width
            val bottom = top + binding.layoutBudgetInfo.height
            binding.cardBubbleView.setTargetBounds(left, top, right, bottom)
            binding.cardBubbleView.start()
        }

        setupBudgetInput()
        setupCategoryBudget()
        refreshAll()
    }

    private fun setupBudgetInput() {
        binding.btnSetBudget.setOnClickListener {
            showBudgetInputDialog()
        }

        binding.btnClearBudget.setOnClickListener {
            AnimeDialogHelper.show(
                context = requireContext(),
                title = "确认清除",
                message = "清除总预算将同时删除所有分类预算，确定要清除吗？",
                positiveText = "确定",
                negativeText = "取消",
                onPositive = {
                    budgetManager.clearBudget()
                    refreshAll()
                    Toast.makeText(requireContext(), "预算已清除", Toast.LENGTH_SHORT).show()
                }
            )
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
        val dp = resources.displayMetrics.density
        val currentBudget = budgetManager.getBudget()
        val input = AnimeDialogHelper.createEditText(
            context = requireContext(),
            hint = "请输入本月预算金额",
            prefill = if (currentBudget > 0) currentBudget.toString() else ""
        ).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
        }

        val container = android.widget.LinearLayout(requireContext()).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding((24 * dp).toInt(), (8 * dp).toInt(), (24 * dp).toInt(), 0)
            addView(input)
        }

        AnimeDialogHelper.show(
            context = requireContext(),
            title = "设置本月总预算",
            customView = container,
            positiveText = "确定",
            negativeText = "取消",
            onPositive = {
                val amount = input.text.toString().toDoubleOrNull()
                if (amount != null && amount > 0) {
                    budgetManager.setBudget(amount)
                    refreshAll()
                    Toast.makeText(requireContext(), "总预算已设置：¥%.2f".format(amount), Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(requireContext(), "请输入有效金额", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    private fun showAddCategoryDialog() {
        val available = budgetManager.getAvailableToAllocate()
        if (available <= 0) {
            Toast.makeText(requireContext(), "预算已全部分配完毕", Toast.LENGTH_SHORT).show()
            return
        }

        val categories = BudgetCategory.SUGGESTED_CATEGORIES.map { Pair(it.second, it.first) }

        AnimeDialogHelper.showIconListDialog(
            context = requireContext(),
            title = "选择预算分类",
            items = categories,
            onItemSelected = { index ->
                val (name, icon) = BudgetCategory.SUGGESTED_CATEGORIES[index]
                showSetCategoryAmountDialog(name, icon, null)
            },
            neutralText = "自定义",
            onNeutral = { showCustomCategoryDialog() }
        )
    }

    private fun showCustomCategoryDialog() {
        val dp = resources.displayMetrics.density
        val input = AnimeDialogHelper.createEditText(
            context = requireContext(),
            hint = "输入分类名称（如：健身、宠物）"
        )

        val container = android.widget.LinearLayout(requireContext()).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding((24 * dp).toInt(), (8 * dp).toInt(), (24 * dp).toInt(), 0)
            addView(input)
        }

        AnimeDialogHelper.show(
            context = requireContext(),
            title = "自定义分类",
            customView = container,
            positiveText = "下一步",
            negativeText = "取消",
            onPositive = {
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    showIconSelectDialog(name)
                } else {
                    Toast.makeText(requireContext(), "请输入分类名称", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    private fun showIconSelectDialog(categoryName: String) {
        val icons = listOf("🍜", "🚗", "🛒", "🎮", "🏠", "💊", "📚", "📱", "👔", "🎭", "💪", "🐱", "💄", "✈️", "🎵", "🎬", "⚽", "🎯", "💼", "🔧")

        AnimeDialogHelper.showListDialog(
            context = requireContext(),
            title = "选择图标",
            items = icons,
            onItemSelected = { which ->
                showSetCategoryAmountDialog(categoryName, icons[which], null)
            }
        )
    }

    private fun showSetCategoryAmountDialog(name: String, icon: String, existingCategory: BudgetCategory?) {
        val dp = resources.displayMetrics.density
        val available = if (existingCategory != null) {
            budgetManager.getAvailableToAllocate() + existingCategory.budgetAmount
        } else {
            budgetManager.getAvailableToAllocate()
        }

        val input = AnimeDialogHelper.createEditText(
            context = requireContext(),
            hint = "可分配金额：¥%.2f".format(available),
            prefill = existingCategory?.budgetAmount?.toString() ?: ""
        ).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
        }

        val container = android.widget.LinearLayout(requireContext()).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding((24 * dp).toInt(), (8 * dp).toInt(), (24 * dp).toInt(), 0)
            addView(input)
        }

        val title = if (existingCategory != null) "修改预算金额" else "设置预算金额"

        AnimeDialogHelper.show(
            context = requireContext(),
            title = title,
            message = "分类：$icon $name\n可分配余额：¥%.2f".format(available),
            customView = container,
            positiveText = "确定",
            negativeText = "取消",
            onPositive = {
                val amount = input.text.toString().toDoubleOrNull()
                if (amount == null || amount <= 0) {
                    Toast.makeText(requireContext(), "请输入有效金额", Toast.LENGTH_SHORT).show()
                    return@show
                }

                if (amount > available) {
                    Toast.makeText(requireContext(), "金额超出可分配范围", Toast.LENGTH_SHORT).show()
                    return@show
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
        )
    }

    private fun showEditCategoryDialog(info: BudgetManager.CategoryBudgetInfo) {
        val options = listOf("修改金额", "删除分类")

        AnimeDialogHelper.showListDialog(
            context = requireContext(),
            title = "${info.category.icon} ${info.category.name}",
            items = options,
            onItemSelected = { which ->
                when (which) {
                    0 -> showSetCategoryAmountDialog(info.category.name, info.category.icon, info.category)
                    1 -> {
                        AnimeDialogHelper.show(
                            context = requireContext(),
                            title = "确认删除",
                            message = "确定要删除「${info.category.name}」分类预算吗？",
                            positiveText = "删除",
                            negativeText = "取消",
                            onPositive = {
                                budgetManager.deleteCategoryBudget(info.category.id)
                                refreshAll()
                                Toast.makeText(requireContext(), "已删除", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                }
            }
        )
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

        val isDarkCard = AutoContrast.isDark(pageBg)
        val statusColorRed = if (isDarkCard) 0xFFFF6B6B.toInt() else resources.getColor(android.R.color.holo_red_dark, null)
        val statusColorOrange = if (isDarkCard) 0xFFFFB84D.toInt() else resources.getColor(android.R.color.holo_orange_dark, null)
        val statusColorGreen = if (isDarkCard) 0xFF6BCB77.toInt() else resources.getColor(android.R.color.holo_green_dark, null)

        when {
            info.isOverBudget -> {
                binding.tvStatus.text = "即使没钱，也要好好照顾自己"
                binding.tvStatus.setTextColor(statusColorRed)
                binding.progressBudget.progressTintList = ColorStateList.valueOf(statusColorRed)
            }
            info.percentUsed >= 80 -> {
                binding.tvStatus.text = "要没钱了哦，我们一起节约呢"
                binding.tvStatus.setTextColor(statusColorOrange)
                binding.progressBudget.progressTintList = ColorStateList.valueOf(statusColorOrange)
            }
            else -> {
                binding.tvStatus.text = "资金满满，大牛一直陪着你"
                binding.tvStatus.setTextColor(statusColorGreen)
                binding.progressBudget.progressTintList = ColorStateList.valueOf(statusColorGreen)
            }
        }
        binding.progressBudget.progressBackgroundTintList = ColorStateList.valueOf(
            if (textColorMode == 2) Color.WHITE else 0xFF16162A.toInt()
        )

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

        // 自动对比度：卡片背景 + 文字颜色
        BudgetThemeApplier.apply(binding.layoutBudgetInfo, pageBg, textColorMode)
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

            // 使用 CategoryIconMapper 获取图标资源 ID
            val iconResId = CategoryIconMapper.getIconResId(info.category.name, 0) // 预算默认为支出类型
            if (iconResId != 0) {
                itemBinding.ivCategoryIcon.setImageResource(iconResId)
                itemBinding.ivCategoryIcon.visibility = View.VISIBLE
                itemBinding.tvCategoryIcon.visibility = View.GONE
            } else {
                // 兼容旧的 emoji 图标
                itemBinding.tvCategoryIcon.text = info.category.icon
                itemBinding.tvCategoryIcon.visibility = View.VISIBLE
                itemBinding.ivCategoryIcon.visibility = View.GONE
            }

            itemBinding.tvCategoryName.text = info.category.name
            itemBinding.tvCategoryBudget.text = "¥%.2f".format(info.category.budgetAmount)
            itemBinding.tvCategorySpent.text = "已消费：¥%.2f".format(info.spent)
            itemBinding.tvCategoryRemaining.text = "剩余：¥%.2f".format(info.remaining)

            itemBinding.progressCategory.max = 100
            itemBinding.progressCategory.progress = info.percentUsed.toInt().coerceAtMost(100)

            val isDarkItemCard = AutoContrast.isDark(pageBg)
            val itemStatusRed = if (isDarkItemCard) 0xFFFF6B6B.toInt() else resources.getColor(android.R.color.holo_red_dark, null)
            val itemStatusOrange = if (isDarkItemCard) 0xFFFFB84D.toInt() else resources.getColor(android.R.color.holo_orange_dark, null)
            val itemStatusGreen = if (isDarkItemCard) 0xFF6BCB77.toInt() else resources.getColor(android.R.color.holo_green_dark, null)

            when {
                info.isOverBudget -> {
                    itemBinding.tvCategoryStatus.text = "已超支！"
                    itemBinding.tvCategoryStatus.setTextColor(itemStatusRed)
                    itemBinding.progressCategory.progressTintList = ColorStateList.valueOf(itemStatusRed)
                }
                info.percentUsed >= 80 -> {
                    itemBinding.tvCategoryStatus.text = "即将用完"
                    itemBinding.tvCategoryStatus.setTextColor(itemStatusOrange)
                    itemBinding.progressCategory.progressTintList = ColorStateList.valueOf(itemStatusOrange)
                }
                else -> {
                    itemBinding.tvCategoryStatus.text = "预算充足"
                    itemBinding.tvCategoryStatus.setTextColor(itemStatusGreen)
                    itemBinding.progressCategory.progressTintList = ColorStateList.valueOf(itemStatusGreen)
                }
            }
            itemBinding.progressCategory.progressBackgroundTintList = ColorStateList.valueOf(
                if (textColorMode == 2) Color.WHITE else 0xFF16162A.toInt()
            )

            itemBinding.btnEdit.setOnClickListener {
                showEditCategoryDialog(info)
            }

            itemBinding.btnDelete.setOnClickListener {
                AnimeDialogHelper.show(
                    context = requireContext(),
                    title = "确认删除",
                    message = "确定要删除「${info.category.name}」分类预算吗？",
                    positiveText = "删除",
                    negativeText = "取消",
                    onPositive = {
                        budgetManager.deleteCategoryBudget(info.category.id)
                        refreshAll()
                        Toast.makeText(requireContext(), "已删除", Toast.LENGTH_SHORT).show()
                    }
                )
            }

            // 自动对比度：卡片背景 + 文字颜色（磨砂玻璃）
            BudgetThemeApplier.applyCategoryCard(itemView, pageBg, textColorMode)
            (itemView as? androidx.cardview.widget.CardView)?.cardElevation = 2f * resources.displayMetrics.density
            binding.layoutCategoryList.addView(itemView)
        }
    }

    override fun onDestroyView() {
        binding.cardBubbleView.stop()
        super.onDestroyView()
        _binding = null
    }
}
