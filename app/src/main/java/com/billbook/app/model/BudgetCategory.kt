package com.billbook.app.model

import com.billbook.app.R

data class BudgetCategory(
    var id: Long = 0,
    var name: String = "",           // 分类名称，如"餐饮"、"交通"
    var icon: String = "",           // 图标：drawable 资源名；存量数据可能仍是 emoji，由 CategoryIconMapper.resolveIcon 兼容
    var budgetAmount: Double = 0.0,  // 该分类的预算金额
    var yearMonth: String = "",      // 年月，格式 "2024-01"
    var sortOrder: Int = 0           // 排序
) {
    companion object {
        // 常用的预算分类建议：(分类名, 图标资源 ID)，与记账分类共用同一套三渲二图标
        val SUGGESTED_CATEGORIES: List<Pair<String, Int>> = listOf(
            "餐饮" to R.drawable.ic_cat_expense_food,
            "交通" to R.drawable.ic_cat_expense_transport,
            "购物" to R.drawable.ic_cat_expense_shopping,
            "娱乐" to R.drawable.ic_cat_expense_entertainment,
            "居住" to R.drawable.ic_cat_expense_home,
            "医疗" to R.drawable.ic_cat_expense_medical,
            "教育" to R.drawable.ic_cat_expense_study,
            "通讯" to R.drawable.ic_cat_expense_communication,
            "服饰" to R.drawable.ic_cat_expense_clothes,
            "社交" to R.drawable.ic_cat_expense_gift,
            "健身" to R.drawable.ic_cat_expense_fitness,
            "宠物" to R.drawable.ic_cat_expense_pet,
            "美容" to R.drawable.ic_cat_expense_beauty,
            "旅行" to R.drawable.ic_cat_expense_travel
        )
    }
}
