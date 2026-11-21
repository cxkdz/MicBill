package com.billbook.app.autobill

import com.billbook.app.db.CategoryDao
import com.billbook.app.model.Category
import com.billbook.app.model.ImportConfig

/**
 * 商户文本 → 分类。
 *
 * 原来这份逻辑是两个账单 Parser 各自的 private 方法，自动记账链路复用不了。
 * 抽到这里后，文件导入与通知捕获共用同一套「自动归类规则」，改一处即全生效。
 */
object CategoryMatcher {

    fun match(
        searchText: String,
        rules: List<ImportConfig>,
        categoryDao: CategoryDao,
        billType: Int
    ): Category? {
        for (rule in rules) {
            // 空关键词会让 contains("") 恒为真，导致第一条规则永远命中
            val keyword = rule.merchantKeyword.trim()
            val target = rule.mapToCategoryName.trim()
            if (target.isEmpty()) continue
            val hit = (keyword.isNotEmpty() && searchText.contains(keyword)) ||
                searchText.contains(target)
            if (hit) {
                val category = categoryDao.getByName(target, billType)
                if (category != null) return category
            }
        }
        return categoryDao.getByName("其他", billType)
    }
}
