package com.billbook.app.util

import android.content.Context
import android.content.SharedPreferences
import com.billbook.app.db.BillDao
import com.billbook.app.db.BudgetCategoryDao
import com.billbook.app.model.Bill
import com.billbook.app.model.BudgetCategory
import java.util.Calendar

/**
 * 预算管理器 - 支持总预算和分类预算
 */
class BudgetManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("budget_prefs", Context.MODE_PRIVATE)
    private val billDao = BillDao(context)
    private val budgetCategoryDao = BudgetCategoryDao(context)
    private val context = context

    companion object {
        const val KEY_MONTHLY_BUDGET = "monthly_budget"
        const val KEY_BUDGET_ENABLED = "budget_enabled"
        const val KEY_BUDGET_ALERT_PERCENT = "budget_alert_percent"
    }

    data class BudgetInfo(
        val budget: Double,
        val spent: Double,
        val remaining: Double,
        val percentUsed: Float,
        val isOverBudget: Boolean,
        val daysRemaining: Int,
        val dailyBudget: Double
    )

    data class CategoryBudgetInfo(
        val category: BudgetCategory,
        val spent: Double,
        val remaining: Double,
        val percentUsed: Float,
        val isOverBudget: Boolean
    )

    fun getCurrentYearMonth(): String {
        val cal = Calendar.getInstance()
        return "${cal.get(Calendar.YEAR)}-${String.format("%02d", cal.get(Calendar.MONTH) + 1)}"
    }

    fun setBudget(amount: Double) {
        prefs.edit()
            .putFloat(KEY_MONTHLY_BUDGET, amount.toFloat())
            .putBoolean(KEY_BUDGET_ENABLED, true)
            .apply()
    }

    fun getBudget(): Double {
        return prefs.getFloat(KEY_MONTHLY_BUDGET, 0f).toDouble()
    }

    fun isBudgetEnabled(): Boolean {
        return prefs.getBoolean(KEY_BUDGET_ENABLED, false) && getBudget() > 0
    }

    fun setAlertPercent(percent: Int) {
        prefs.edit().putInt(KEY_BUDGET_ALERT_PERCENT, percent).apply()
    }

    fun getAlertPercent(): Int {
        return prefs.getInt(KEY_BUDGET_ALERT_PERCENT, 80)
    }

    // ========== 分类预算管理 ==========

    /**
     * 添加分类预算
     * @return 添加结果消息
     */
    fun addCategoryBudget(name: String, icon: String, amount: Double): String {
        val yearMonth = getCurrentYearMonth()
        val totalBudget = getBudget()

        // 检查是否超过总预算
        val currentSum = budgetCategoryDao.getSumByYearMonth(yearMonth)
        if (currentSum + amount > totalBudget) {
            val remaining = totalBudget - currentSum
            return "分类预算总和不能超过总预算！当前还可分配：¥%.2f".format(remaining)
        }

        // 检查是否重名
        if (budgetCategoryDao.existsByNameAndYearMonth(name, yearMonth)) {
            return "该分类已存在"
        }

        val category = BudgetCategory(
            name = name,
            icon = icon,
            budgetAmount = amount,
            yearMonth = yearMonth,
            sortOrder = budgetCategoryDao.getByYearMonth(yearMonth).size
        )

        budgetCategoryDao.insert(category)
        return "添加成功"
    }

    /**
     * 更新分类预算金额
     */
    fun updateCategoryBudget(id: Long, newAmount: Double): String {
        val yearMonth = getCurrentYearMonth()
        val totalBudget = getBudget()
        val category = budgetCategoryDao.getById(id) ?: return "分类不存在"

        // 计算除去当前分类后的已分配金额
        val currentSum = budgetCategoryDao.getSumByYearMonth(yearMonth)
        val otherSum = currentSum - category.budgetAmount

        if (otherSum + newAmount > totalBudget) {
            val remaining = totalBudget - otherSum
            return "分类预算总和不能超过总预算！当前还可分配：¥%.2f".format(remaining)
        }

        category.budgetAmount = newAmount
        budgetCategoryDao.update(category)
        return "更新成功"
    }

    /**
     * 删除分类预算
     */
    fun deleteCategoryBudget(id: Long) {
        budgetCategoryDao.delete(id)
    }

    /**
     * 获取所有分类预算及其消费情况
     */
    fun getCategoryBudgetsInfo(): List<CategoryBudgetInfo> {
        val yearMonth = getCurrentYearMonth()
        val categories = budgetCategoryDao.getByYearMonth(yearMonth)

        val cal = Calendar.getInstance()
        val year = cal.get(Calendar.YEAR)
        val month = cal.get(Calendar.MONTH) + 1

        return categories.map { category ->
            // 查询该分类本月的消费
            val spent = getCategorySpent(category.name, year, month)
            val remaining = category.budgetAmount - spent
            val percentUsed = if (category.budgetAmount > 0) (spent / category.budgetAmount * 100).toFloat() else 0f

            CategoryBudgetInfo(
                category = category,
                spent = spent,
                remaining = remaining,
                percentUsed = percentUsed,
                isOverBudget = remaining < 0
            )
        }
    }

    /**
     * 获取分类预算已分配总额
     */
    fun getAllocatedAmount(): Double {
        return budgetCategoryDao.getSumByYearMonth(getCurrentYearMonth())
    }

    /**
     * 获取可分配余额
     */
    fun getAvailableToAllocate(): Double {
        return getBudget() - getAllocatedAmount()
    }

    /**
     * 查询某个分类本月的消费
     */
    private fun getCategorySpent(categoryName: String, year: Int, month: Int): Double {
        val db = com.billbook.app.db.DatabaseHelper(context).readableDatabase
        val cal = Calendar.getInstance()
        cal.set(year, month - 1, 1, 0, 0, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val startTime = cal.timeInMillis
        cal.add(Calendar.MONTH, 1)
        val endTime = cal.timeInMillis

        val cursor = db.rawQuery(
            "SELECT SUM(money) FROM tb_bill WHERE category_name = ? AND type = 0 AND bill_time >= ? AND bill_time < ?",
            arrayOf(categoryName, startTime.toString(), endTime.toString())
        )
        var sum = 0.0
        if (cursor.moveToFirst()) {
            sum = cursor.getDouble(0)
        }
        cursor.close()
        return sum
    }

    // ========== 总预算信息 ==========

    fun getBudgetInfo(): BudgetInfo {
        val budget = getBudget()
        val cal = Calendar.getInstance()
        val year = cal.get(Calendar.YEAR)
        val month = cal.get(Calendar.MONTH) + 1

        val (expense, _) = billDao.getMonthSummary(year, month)
        val remaining = budget - expense
        val percentUsed = if (budget > 0) (expense / budget * 100).toFloat() else 0f

        val today = cal.get(Calendar.DAY_OF_MONTH)
        val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        val daysRemaining = daysInMonth - today

        val dailyBudget = if (remaining > 0 && daysRemaining > 0) {
            remaining / daysRemaining
        } else 0.0

        return BudgetInfo(
            budget = budget,
            spent = expense,
            remaining = remaining,
            percentUsed = percentUsed,
            isOverBudget = remaining < 0,
            daysRemaining = daysRemaining,
            dailyBudget = dailyBudget
        )
    }

    fun clearBudget() {
        // 同时清除分类预算
        budgetCategoryDao.deleteByYearMonth(getCurrentYearMonth())
        prefs.edit().clear().apply()
    }
}
