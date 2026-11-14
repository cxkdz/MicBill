package com.billbook.app.dialog

import android.content.Context
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.billbook.app.db.ImportConfigDao
import com.billbook.app.model.ImportConfig
import com.billbook.app.util.AnimeDialogHelper

/**
 * 商户关键词 → 分类 映射规则的编辑入口。
 *
 * 复用 ImportConfigDao 已有的 getAll/insert/update/delete，UI 全部由 AnimeDialogHelper
 * 代码构建，不新增布局文件。规则在账单导入时由 WeChatBillParser / AlipayBillParser 消费。
 */
object ImportRuleDialog {

    fun show(context: Context) {
        showList(context, ImportConfigDao(context.applicationContext))
    }

    private fun showList(context: Context, dao: ImportConfigDao) {
        val rules = dao.getAll()
        val items = buildList {
            add("＋ 新增规则")
            rules.forEach { r ->
                val state = if (r.isEnabled) "启用" else "停用"
                add("${r.merchantKeyword}  →  ${r.mapToCategoryName}   [$state]")
            }
        }
        lateinit var dialog: AlertDialog
        dialog = AnimeDialogHelper.showListDialog(
            context, "自动归类规则（${rules.size} 条）", items
        ) { index ->
            dialog.dismiss()
            if (index == 0) {
                showEdit(context, dao, null)
            } else {
                showActions(context, dao, rules[index - 1])
            }
        }
    }

    private fun showActions(context: Context, dao: ImportConfigDao, rule: ImportConfig) {
        val items = listOf("编辑", if (rule.isEnabled) "停用" else "启用", "删除")
        lateinit var dialog: AlertDialog
        dialog = AnimeDialogHelper.showListDialog(
            context, "${rule.merchantKeyword} → ${rule.mapToCategoryName}", items
        ) { index ->
            dialog.dismiss()
            when (index) {
                0 -> showEdit(context, dao, rule)
                1 -> {
                    dao.update(rule.copy(isEnabled = !rule.isEnabled))
                    toast(context, "已更新")
                    showList(context, dao)
                }
                2 -> {
                    dao.delete(rule.id)
                    toast(context, "已删除")
                    showList(context, dao)
                }
            }
        }
    }

    private fun showEdit(context: Context, dao: ImportConfigDao, rule: ImportConfig?) {
        val keyword = AnimeDialogHelper.createEditText(context, "商户关键词，如 美团", rule?.merchantKeyword ?: "")
        val category = AnimeDialogHelper.createEditText(context, "映射到分类名，如 餐饮", rule?.mapToCategoryName ?: "")
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(keyword)
            addView(category)
        }
        AnimeDialogHelper.show(
            context = context,
            title = if (rule == null) "新增规则" else "编辑规则",
            message = "导入账单时，商户名或商品名包含关键词就归到指定分类",
            customView = container,
            positiveText = "保存",
            negativeText = "取消",
            onPositive = {
                val k = keyword.text.toString().trim()
                val c = category.text.toString().trim()
                if (k.isEmpty() || c.isEmpty()) {
                    toast(context, "关键词和分类名都不能为空")
                } else {
                    if (rule == null) {
                        dao.insert(ImportConfig(merchantKeyword = k, mapToCategoryName = c))
                    } else {
                        dao.update(rule.copy(merchantKeyword = k, mapToCategoryName = c))
                    }
                    toast(context, "已保存")
                    showList(context, dao)
                }
            }
        )
    }

    private fun toast(context: Context, message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }
}
