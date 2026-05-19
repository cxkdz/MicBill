package com.billbook.app.model

data class BudgetCategory(
    var id: Long = 0,
    var name: String = "",           // 分类名称，如"餐饮"、"交通"
    var icon: String = "",           // 图标 emoji
    var budgetAmount: Double = 0.0,  // 该分类的预算金额
    var yearMonth: String = "",      // 年月，格式 "2024-01"
    var sortOrder: Int = 0           // 排序
) {
    companion object {
        // 常用的预算分类建议
        val SUGGESTED_CATEGORIES = listOf(
            "餐饮" to "🍜",
            "交通" to "🚗",
            "购物" to "🛒",
            "娱乐" to "🎮",
            "居住" to "🏠",
            "医疗" to "💊",
            "教育" to "📚",
            "通讯" to "📱",
            "服饰" to "👔",
            "社交" to "🎭",
            "健身" to "💪",
            "宠物" to "🐱",
            "美容" to "💄",
            "旅行" to "✈️"
        )
    }
}
