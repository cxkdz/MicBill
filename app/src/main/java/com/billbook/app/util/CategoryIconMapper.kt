package com.billbook.app.util

import android.content.Context
import com.billbook.app.R

/**
 * 分类图标映射器 - 根据分类名称返回对应的 drawable 资源 ID
 */
object CategoryIconMapper {

    // 支出分类图标映射
    private val expenseIconMap = mapOf(
        "餐饮" to R.drawable.ic_cat_expense_food,
        "交通" to R.drawable.ic_cat_expense_transport,
        "购物" to R.drawable.ic_cat_expense_shopping,
        "娱乐" to R.drawable.ic_cat_expense_entertainment,
        "居住" to R.drawable.ic_cat_expense_home,
        "医疗" to R.drawable.ic_cat_expense_medical,
        "教育" to R.drawable.ic_cat_expense_study,
        "通讯" to R.drawable.ic_cat_expense_communication,
        "服饰" to R.drawable.ic_cat_expense_clothes,
        "红包" to R.drawable.ic_cat_expense_gift,
        "礼金" to R.drawable.ic_cat_expense_gift,
        "转账" to R.drawable.ic_cat_expense_transfer,
        "其他" to R.drawable.ic_cat_expense_other,
        "日用" to R.drawable.ic_cat_expense_daily,
        "蔬菜" to R.drawable.ic_cat_expense_vegetable,
        "水果" to R.drawable.ic_cat_expense_fruit,
        "酒水" to R.drawable.ic_cat_expense_alcohol,
        "汽车" to R.drawable.ic_cat_expense_car,
        "书籍" to R.drawable.ic_cat_expense_book,
        "美容" to R.drawable.ic_cat_expense_beauty,
        "孩子" to R.drawable.ic_cat_expense_child,
        "宠物" to R.drawable.ic_cat_expense_pet,
        "健身" to R.drawable.ic_cat_expense_fitness,
        "游戏" to R.drawable.ic_cat_expense_game,
        "住房" to R.drawable.ic_cat_expense_housing,
        "运动" to R.drawable.ic_cat_expense_sport,
        "旅行" to R.drawable.ic_cat_expense_travel,
        "零食" to R.drawable.ic_cat_expense_snack,
        "咖啡" to R.drawable.ic_cat_expense_coffee
    )

    // 收入分类图标映射
    private val incomeIconMap = mapOf(
        "工资" to R.drawable.ic_cat_income_salary,
        "奖金" to R.drawable.ic_cat_income_bonus,
        "红包" to R.drawable.ic_cat_income_gift,
        "礼金" to R.drawable.ic_cat_income_gift,
        "转账" to R.drawable.ic_cat_income_transfer,
        "理财" to R.drawable.ic_cat_income_finance,
        "其他" to R.drawable.ic_cat_income_other,
        "兼职" to R.drawable.ic_cat_income_parttime,
        "分红" to R.drawable.ic_cat_income_dividend
    )

    // 所有支出图标资源 ID（用于图标选择）
    private val allExpenseIconResIds = listOf(
        R.drawable.ic_cat_expense_food,
        R.drawable.ic_cat_expense_transport,
        R.drawable.ic_cat_expense_shopping,
        R.drawable.ic_cat_expense_entertainment,
        R.drawable.ic_cat_expense_home,
        R.drawable.ic_cat_expense_medical,
        R.drawable.ic_cat_expense_study,
        R.drawable.ic_cat_expense_communication,
        R.drawable.ic_cat_expense_clothes,
        R.drawable.ic_cat_expense_gift,
        R.drawable.ic_cat_expense_transfer,
        R.drawable.ic_cat_expense_other,
        R.drawable.ic_cat_expense_daily,
        R.drawable.ic_cat_expense_vegetable,
        R.drawable.ic_cat_expense_fruit,
        R.drawable.ic_cat_expense_alcohol,
        R.drawable.ic_cat_expense_car,
        R.drawable.ic_cat_expense_book,
        R.drawable.ic_cat_expense_beauty,
        R.drawable.ic_cat_expense_child,
        R.drawable.ic_cat_expense_pet,
        R.drawable.ic_cat_expense_fitness,
        R.drawable.ic_cat_expense_game,
        R.drawable.ic_cat_expense_housing,
        R.drawable.ic_cat_expense_sport,
        R.drawable.ic_cat_expense_travel,
        R.drawable.ic_cat_expense_snack,
        R.drawable.ic_cat_expense_coffee
    )

    // 所有收入图标资源 ID（用于图标选择）
    private val allIncomeIconResIds = listOf(
        R.drawable.ic_cat_income_salary,
        R.drawable.ic_cat_income_bonus,
        R.drawable.ic_cat_income_gift,
        R.drawable.ic_cat_income_transfer,
        R.drawable.ic_cat_income_finance,
        R.drawable.ic_cat_income_other,
        R.drawable.ic_cat_income_parttime,
        R.drawable.ic_cat_income_dividend
    )

    // 所有通用图标资源 ID（用于图标选择）
    val allMiscIconResIds = listOf(
        R.drawable.ic_misc_star,
        R.drawable.ic_misc_diamond,
        R.drawable.ic_misc_letter,
        R.drawable.ic_misc_package,
        R.drawable.ic_misc_lightbulb,
        R.drawable.ic_misc_heart,
        R.drawable.ic_misc_flower,
        R.drawable.ic_misc_camera,
        R.drawable.ic_misc_calendar,
        R.drawable.ic_misc_key,
        R.drawable.ic_misc_earphone,
        R.drawable.ic_misc_gift,
        R.drawable.ic_misc_more,
        R.drawable.ic_misc_tag,
        R.drawable.ic_misc_coffee,
        R.drawable.ic_misc_watch,
        R.drawable.ic_misc_bag,
        R.drawable.ic_misc_bill,
        R.drawable.ic_misc_flag
    )

    /**
     * 根据分类名称和类型获取图标资源 ID
     * @param categoryName 分类名称
     * @param type 分类类型 (0=支出, 1=收入)
     * @return drawable 资源 ID，如果未找到则返回 0
     */
    fun getIconResId(categoryName: String, type: Int): Int {
        return when (type) {
            0 -> expenseIconMap[categoryName] ?: R.drawable.ic_cat_expense_other
            1 -> incomeIconMap[categoryName] ?: R.drawable.ic_cat_income_other
            else -> 0
        }
    }

    /**
     * 获取所有支出图标资源 ID（包括未映射的）
     */
    fun getAllExpenseIconResIds(): List<Int> {
        return allExpenseIconResIds
    }

    /**
     * 获取所有收入图标资源 ID（包括未映射的）
     */
    fun getAllIncomeIconResIds(): List<Int> {
        return allIncomeIconResIds
    }

    /**
     * 统一图标解析入口：新数据存的是 drawable 资源名，存量数据存的是 emoji 字符串。
     * 资源名解析失败（emoji 或脏数据）时按分类名称映射，因此不需要 DB schema 迁移。
     */
    fun resolveIcon(context: Context, categoryName: String, storedIcon: String, type: Int = 0): Int {
        if (storedIcon.isNotBlank() && storedIcon.all { it.isLetterOrDigit() || it == '_' }) {
            val id = context.resources.getIdentifier(storedIcon, "drawable", context.packageName)
            if (id != 0) return id
        }
        return getIconResId(categoryName, type)
    }

    /** 存库用稳定资源名，不存 resId（aapt 资源 ID 会随构建变化） */
    fun iconName(context: Context, resId: Int): String = try {
        context.resources.getResourceEntryName(resId)
    } catch (_: Exception) {
        ""
    }

    /** 带中文标签的全部候选图标，用于自定义分类的图标选择器 */
    fun labeledIcons(): List<Pair<Int, String>> =
        (expenseIconMap.entries + incomeIconMap.entries)
            .distinctBy { it.value }
            .map { it.value to it.key }
}
