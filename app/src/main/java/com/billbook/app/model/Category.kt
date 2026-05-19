package com.billbook.app.model

data class Category(
    var id: Long = 0,
    var name: String = "",
    var icon: String = "",
    var type: Int = Bill.TYPE_EXPENSE,
    var sortOrder: Int = 0,
    var isDefault: Boolean = false
) {
    companion object {
        // 默认支出分类
        val DEFAULT_EXPENSE_CATEGORIES = listOf(
            Category(name = "餐饮", icon = "🍜", type = Bill.TYPE_EXPENSE, sortOrder = 0, isDefault = true),
            Category(name = "交通", icon = "🚗", type = Bill.TYPE_EXPENSE, sortOrder = 1, isDefault = true),
            Category(name = "购物", icon = "🛒", type = Bill.TYPE_EXPENSE, sortOrder = 2, isDefault = true),
            Category(name = "娱乐", icon = "🎮", type = Bill.TYPE_EXPENSE, sortOrder = 3, isDefault = true),
            Category(name = "居住", icon = "🏠", type = Bill.TYPE_EXPENSE, sortOrder = 4, isDefault = true),
            Category(name = "医疗", icon = "💊", type = Bill.TYPE_EXPENSE, sortOrder = 5, isDefault = true),
            Category(name = "教育", icon = "📚", type = Bill.TYPE_EXPENSE, sortOrder = 6, isDefault = true),
            Category(name = "通讯", icon = "📱", type = Bill.TYPE_EXPENSE, sortOrder = 7, isDefault = true),
            Category(name = "服饰", icon = "👔", type = Bill.TYPE_EXPENSE, sortOrder = 8, isDefault = true),
            Category(name = "红包", icon = "🧧", type = Bill.TYPE_EXPENSE, sortOrder = 9, isDefault = true),
            Category(name = "转账", icon = "💸", type = Bill.TYPE_EXPENSE, sortOrder = 10, isDefault = true),
            Category(name = "其他", icon = "📦", type = Bill.TYPE_EXPENSE, sortOrder = 11, isDefault = true)
        )

        // 默认收入分类
        val DEFAULT_INCOME_CATEGORIES = listOf(
            Category(name = "工资", icon = "💰", type = Bill.TYPE_INCOME, sortOrder = 0, isDefault = true),
            Category(name = "奖金", icon = "🎁", type = Bill.TYPE_INCOME, sortOrder = 1, isDefault = true),
            Category(name = "红包", icon = "🧧", type = Bill.TYPE_INCOME, sortOrder = 2, isDefault = true),
            Category(name = "转账", icon = "💸", type = Bill.TYPE_INCOME, sortOrder = 3, isDefault = true),
            Category(name = "理财", icon = "📈", type = Bill.TYPE_INCOME, sortOrder = 4, isDefault = true),
            Category(name = "其他", icon = "📦", type = Bill.TYPE_INCOME, sortOrder = 5, isDefault = true)
        )
    }
}
