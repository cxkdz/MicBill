package com.billbook.app.db

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import com.billbook.app.model.BudgetCategory

class BudgetCategoryDao(context: Context) {

    private val dbHelper = DatabaseHelper(context)

    companion object {
        const val TABLE = "tb_budget_category"
        const val COL_ID = "id"
        const val COL_NAME = "name"
        const val COL_ICON = "icon"
        const val COL_BUDGET_AMOUNT = "budget_amount"
        const val COL_YEAR_MONTH = "year_month"
        const val COL_SORT_ORDER = "sort_order"
    }

    fun insert(category: BudgetCategory): Long {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put(COL_NAME, category.name)
            put(COL_ICON, category.icon)
            put(COL_BUDGET_AMOUNT, category.budgetAmount)
            put(COL_YEAR_MONTH, category.yearMonth)
            put(COL_SORT_ORDER, category.sortOrder)
        }
        return db.insert(TABLE, null, values)
    }

    fun update(category: BudgetCategory): Int {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put(COL_NAME, category.name)
            put(COL_ICON, category.icon)
            put(COL_BUDGET_AMOUNT, category.budgetAmount)
            put(COL_SORT_ORDER, category.sortOrder)
        }
        return db.update(TABLE, values, "$COL_ID = ?", arrayOf(category.id.toString()))
    }

    fun delete(id: Long): Int {
        val db = dbHelper.writableDatabase
        return db.delete(TABLE, "$COL_ID = ?", arrayOf(id.toString()))
    }

    fun deleteByYearMonth(yearMonth: String): Int {
        val db = dbHelper.writableDatabase
        return db.delete(TABLE, "$COL_YEAR_MONTH = ?", arrayOf(yearMonth))
    }

    fun getById(id: Long): BudgetCategory? {
        val db = dbHelper.readableDatabase
        val cursor = db.query(TABLE, null, "$COL_ID = ?", arrayOf(id.toString()), null, null, null)
        return if (cursor.moveToFirst()) {
            cursorToCategory(cursor).also { cursor.close() }
        } else {
            cursor.close()
            null
        }
    }

    fun getByYearMonth(yearMonth: String): List<BudgetCategory> {
        val db = dbHelper.readableDatabase
        val cursor = db.query(
            TABLE, null,
            "$COL_YEAR_MONTH = ?",
            arrayOf(yearMonth),
            null, null,
            "$COL_SORT_ORDER ASC"
        )
        val categories = mutableListOf<BudgetCategory>()
        while (cursor.moveToNext()) {
            categories.add(cursorToCategory(cursor))
        }
        cursor.close()
        return categories
    }

    fun getSumByYearMonth(yearMonth: String): Double {
        val db = dbHelper.readableDatabase
        val cursor = db.rawQuery(
            "SELECT SUM($COL_BUDGET_AMOUNT) FROM $TABLE WHERE $COL_YEAR_MONTH = ?",
            arrayOf(yearMonth)
        )
        var sum = 0.0
        if (cursor.moveToFirst()) {
            sum = cursor.getDouble(0)
        }
        cursor.close()
        return sum
    }

    fun existsByNameAndYearMonth(name: String, yearMonth: String): Boolean {
        val db = dbHelper.readableDatabase
        val cursor = db.rawQuery(
            "SELECT COUNT(*) FROM $TABLE WHERE $COL_NAME = ? AND $COL_YEAR_MONTH = ?",
            arrayOf(name, yearMonth)
        )
        cursor.moveToFirst()
        val count = cursor.getInt(0)
        cursor.close()
        return count > 0
    }

    private fun cursorToCategory(cursor: Cursor): BudgetCategory {
        return BudgetCategory(
            id = cursor.getLong(cursor.getColumnIndexOrThrow(COL_ID)),
            name = cursor.getString(cursor.getColumnIndexOrThrow(COL_NAME)) ?: "",
            icon = cursor.getString(cursor.getColumnIndexOrThrow(COL_ICON)) ?: "",
            budgetAmount = cursor.getDouble(cursor.getColumnIndexOrThrow(COL_BUDGET_AMOUNT)),
            yearMonth = cursor.getString(cursor.getColumnIndexOrThrow(COL_YEAR_MONTH)) ?: "",
            sortOrder = cursor.getInt(cursor.getColumnIndexOrThrow(COL_SORT_ORDER))
        )
    }
}
