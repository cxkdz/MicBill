package com.billbook.app.model

import com.billbook.app.R

data class Category(
    var id: Long = 0,
    var name: String = "",
    var icon: String = "",  // 保留字段兼容数据库
    var iconResId: Int = 0, // 新增：drawable 资源 ID
    var type: Int = Bill.TYPE_EXPENSE,
    var sortOrder: Int = 0,
    var isDefault: Boolean = false
) {
    companion object {
        // 默认支出分类
        val DEFAULT_EXPENSE_CATEGORIES = listOf(
            Category(name = "餐饮", iconResId = R.drawable.ic_cat_expense_food, type = Bill.TYPE_EXPENSE, sortOrder = 0, isDefault = true),
            Category(name = "交通", iconResId = R.drawable.ic_cat_expense_transport, type = Bill.TYPE_EXPENSE, sortOrder = 1, isDefault = true),
            Category(name = "购物", iconResId = R.drawable.ic_cat_expense_shopping, type = Bill.TYPE_EXPENSE, sortOrder = 2, isDefault = true),
            Category(name = "娱乐", iconResId = R.drawable.ic_cat_expense_entertainment, type = Bill.TYPE_EXPENSE, sortOrder = 3, isDefault = true),
            Category(name = "居住", iconResId = R.drawable.ic_cat_expense_home, type = Bill.TYPE_EXPENSE, sortOrder = 4, isDefault = true),
            Category(name = "医疗", iconResId = R.drawable.ic_cat_expense_medical, type = Bill.TYPE_EXPENSE, sortOrder = 5, isDefault = true),
            Category(name = "教育", iconResId = R.drawable.ic_cat_expense_study, type = Bill.TYPE_EXPENSE, sortOrder = 6, isDefault = true),
            Category(name = "通讯", iconResId = R.drawable.ic_cat_expense_communication, type = Bill.TYPE_EXPENSE, sortOrder = 7, isDefault = true),
            Category(name = "服饰", iconResId = R.drawable.ic_cat_expense_clothes, type = Bill.TYPE_EXPENSE, sortOrder = 8, isDefault = true),
            Category(name = "红包", iconResId = R.drawable.ic_cat_expense_gift, type = Bill.TYPE_EXPENSE, sortOrder = 9, isDefault = true),
            Category(name = "转账", iconResId = R.drawable.ic_cat_expense_transfer, type = Bill.TYPE_EXPENSE, sortOrder = 10, isDefault = true),
            Category(name = "其他", iconResId = R.drawable.ic_cat_expense_other, type = Bill.TYPE_EXPENSE, sortOrder = 11, isDefault = true)
        )

        // 默认收入分类
        val DEFAULT_INCOME_CATEGORIES = listOf(
            Category(name = "工资", iconResId = R.drawable.ic_cat_income_salary, type = Bill.TYPE_INCOME, sortOrder = 0, isDefault = true),
            Category(name = "奖金", iconResId = R.drawable.ic_cat_income_bonus, type = Bill.TYPE_INCOME, sortOrder = 1, isDefault = true),
            Category(name = "红包", iconResId = R.drawable.ic_cat_income_gift, type = Bill.TYPE_INCOME, sortOrder = 2, isDefault = true),
            Category(name = "转账", iconResId = R.drawable.ic_cat_income_transfer, type = Bill.TYPE_INCOME, sortOrder = 3, isDefault = true),
            Category(name = "理财", iconResId = R.drawable.ic_cat_income_finance, type = Bill.TYPE_INCOME, sortOrder = 4, isDefault = true),
            Category(name = "其他", iconResId = R.drawable.ic_cat_income_other, type = Bill.TYPE_INCOME, sortOrder = 5, isDefault = true)
        )
    }
}
