package com.billbook.app.db

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import com.billbook.app.db.DatabaseHelper.Companion.TABLE_BILL
import com.billbook.app.db.DatabaseHelper.Companion.COL_ID
import com.billbook.app.db.DatabaseHelper.Companion.COL_MONEY
import com.billbook.app.db.DatabaseHelper.Companion.COL_TYPE
import com.billbook.app.db.DatabaseHelper.Companion.COL_CATEGORY_ID
import com.billbook.app.db.DatabaseHelper.Companion.COL_CATEGORY_NAME
import com.billbook.app.db.DatabaseHelper.Companion.COL_CATEGORY_ICON
import com.billbook.app.db.DatabaseHelper.Companion.COL_PAY_TYPE
import com.billbook.app.db.DatabaseHelper.Companion.COL_BILL_TIME
import com.billbook.app.db.DatabaseHelper.Companion.COL_REMARK
import com.billbook.app.db.DatabaseHelper.Companion.COL_SOURCE
import com.billbook.app.db.DatabaseHelper.Companion.COL_IMPORT_BATCH
import com.billbook.app.db.DatabaseHelper.Companion.COL_CREATED_AT
import com.billbook.app.model.Bill

class BillDao(context: Context) {

    private val dbHelper = DatabaseHelper(context)

    fun insert(bill: Bill): Long {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put(COL_MONEY, bill.money)
            put(COL_TYPE, bill.type)
            put(COL_CATEGORY_ID, bill.categoryId)
            put(COL_CATEGORY_NAME, bill.categoryName)
            put(COL_CATEGORY_ICON, bill.categoryIcon)
            put(COL_PAY_TYPE, bill.payType)
            put(COL_BILL_TIME, bill.billTime)
            put(COL_REMARK, bill.remark)
            put(COL_SOURCE, bill.source)
            put(COL_IMPORT_BATCH, bill.importBatchId)
            put(COL_CREATED_AT, System.currentTimeMillis())
        }
        return db.insert(TABLE_BILL, null, values)
    }

    fun insertBatch(bills: List<Bill>): Int {
        val db = dbHelper.writableDatabase
        var count = 0
        db.beginTransaction()
        try {
            bills.forEach { bill ->
                val values = ContentValues().apply {
                    put(COL_MONEY, bill.money)
                    put(COL_TYPE, bill.type)
                    put(COL_CATEGORY_ID, bill.categoryId)
                    put(COL_CATEGORY_NAME, bill.categoryName)
                    put(COL_CATEGORY_ICON, bill.categoryIcon)
                    put(COL_PAY_TYPE, bill.payType)
                    put(COL_BILL_TIME, bill.billTime)
                    put(COL_REMARK, bill.remark)
                    put(COL_SOURCE, bill.source)
                    put(COL_IMPORT_BATCH, bill.importBatchId)
                    put(COL_CREATED_AT, System.currentTimeMillis())
                }
                db.insert(TABLE_BILL, null, values)
                count++
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        return count
    }

    fun update(bill: Bill): Int {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put(COL_MONEY, bill.money)
            put(COL_TYPE, bill.type)
            put(COL_CATEGORY_ID, bill.categoryId)
            put(COL_CATEGORY_NAME, bill.categoryName)
            put(COL_CATEGORY_ICON, bill.categoryIcon)
            put(COL_PAY_TYPE, bill.payType)
            put(COL_BILL_TIME, bill.billTime)
            put(COL_REMARK, bill.remark)
        }
        return db.update(TABLE_BILL, values, "$COL_ID = ?", arrayOf(bill.id.toString()))
    }

    fun delete(id: Long): Int {
        val db = dbHelper.writableDatabase
        return db.delete(TABLE_BILL, "$COL_ID = ?", arrayOf(id.toString()))
    }

    fun getById(id: Long): Bill? {
        val db = dbHelper.readableDatabase
        val cursor = db.query(TABLE_BILL, null, "$COL_ID = ?", arrayOf(id.toString()), null, null, null)
        return if (cursor.moveToFirst()) {
            cursorToBill(cursor).also { cursor.close() }
        } else {
            cursor.close()
            null
        }
    }

    // 查询指定月份账单
    fun getByMonth(year: Int, month: Int): List<Bill> {
        val db = dbHelper.readableDatabase
        val cal = java.util.Calendar.getInstance()
        cal.set(year, month - 1, 1, 0, 0, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        val startTime = cal.timeInMillis

        cal.add(java.util.Calendar.MONTH, 1)
        val endTime = cal.timeInMillis

        val cursor = db.query(
            TABLE_BILL, null,
            "$COL_BILL_TIME >= ? AND $COL_BILL_TIME < ?",
            arrayOf(startTime.toString(), endTime.toString()),
            null, null,
            "$COL_BILL_TIME DESC"
        )

        val bills = mutableListOf<Bill>()
        while (cursor.moveToNext()) {
            bills.add(cursorToBill(cursor))
        }
        cursor.close()
        return bills
    }

    // 按渠道查询
    fun getByPayType(payType: String, startTime: Long, endTime: Long): List<Bill> {
        val db = dbHelper.readableDatabase
        val cursor = db.query(
            TABLE_BILL, null,
            "$COL_PAY_TYPE = ? AND $COL_BILL_TIME >= ? AND $COL_BILL_TIME < ?",
            arrayOf(payType, startTime.toString(), endTime.toString()),
            null, null,
            "$COL_BILL_TIME DESC"
        )
        val bills = mutableListOf<Bill>()
        while (cursor.moveToNext()) {
            bills.add(cursorToBill(cursor))
        }
        cursor.close()
        return bills
    }

    // 查询月度收支汇总
    fun getMonthSummary(year: Int, month: Int): Pair<Double, Double> {
        val db = dbHelper.readableDatabase
        val cal = java.util.Calendar.getInstance()
        cal.set(year, month - 1, 1, 0, 0, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        val startTime = cal.timeInMillis
        cal.add(java.util.Calendar.MONTH, 1)
        val endTime = cal.timeInMillis

        var totalExpense = 0.0
        var totalIncome = 0.0

        val cursor = db.rawQuery(
            "SELECT $COL_TYPE, SUM($COL_MONEY) FROM $TABLE_BILL WHERE $COL_BILL_TIME >= ? AND $COL_BILL_TIME < ? GROUP BY $COL_TYPE",
            arrayOf(startTime.toString(), endTime.toString())
        )
        while (cursor.moveToNext()) {
            val type = cursor.getInt(0)
            val sum = cursor.getDouble(1)
            if (type == Bill.TYPE_EXPENSE) totalExpense = sum
            else totalIncome = sum
        }
        cursor.close()
        return Pair(totalExpense, totalIncome)
    }

    // 查询分类消费排行
    fun getCategoryRanking(year: Int, month: Int, type: Int = Bill.TYPE_EXPENSE): List<Triple<String, String, Double>> {
        val db = dbHelper.readableDatabase
        val cal = java.util.Calendar.getInstance()
        cal.set(year, month - 1, 1, 0, 0, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        val startTime = cal.timeInMillis
        cal.add(java.util.Calendar.MONTH, 1)
        val endTime = cal.timeInMillis

        val cursor = db.rawQuery(
            "SELECT $COL_CATEGORY_NAME, SUM($COL_MONEY) as total FROM $TABLE_BILL " +
                    "WHERE $COL_BILL_TIME >= ? AND $COL_BILL_TIME < ? AND $COL_TYPE = ? " +
                    "GROUP BY $COL_CATEGORY_NAME ORDER BY total DESC",
            arrayOf(startTime.toString(), endTime.toString(), type.toString())
        )

        val result = mutableListOf<Triple<String, String, Double>>()
        while (cursor.moveToNext()) {
            val name = cursor.getString(0)
            val total = cursor.getDouble(1)
            result.add(Triple("", name, total))
        }
        cursor.close()
        return result
    }

    // 自定义日期范围查询
    fun getByDateRange(startTime: Long, endTime: Long): List<Bill> {
        val db = dbHelper.readableDatabase
        val cursor = db.query(
            TABLE_BILL, null,
            "$COL_BILL_TIME >= ? AND $COL_BILL_TIME <= ?",
            arrayOf(startTime.toString(), endTime.toString()),
            null, null,
            "$COL_BILL_TIME DESC"
        )
        val bills = mutableListOf<Bill>()
        while (cursor.moveToNext()) {
            bills.add(cursorToBill(cursor))
        }
        cursor.close()
        return bills
    }

    // 自定义日期范围分类汇总
    fun getCategorySummaryByDateRange(startTime: Long, endTime: Long, type: Int = Bill.TYPE_EXPENSE): List<Triple<String, String, Double>> {
        val db = dbHelper.readableDatabase
        val cursor = db.rawQuery(
            "SELECT $COL_CATEGORY_NAME, SUM($COL_MONEY) as total FROM $TABLE_BILL " +
                    "WHERE $COL_BILL_TIME >= ? AND $COL_BILL_TIME <= ? AND $COL_TYPE = ? " +
                    "GROUP BY $COL_CATEGORY_NAME ORDER BY total DESC",
            arrayOf(startTime.toString(), endTime.toString(), type.toString())
        )
        val result = mutableListOf<Triple<String, String, Double>>()
        while (cursor.moveToNext()) {
            val name = cursor.getString(0)
            val total = cursor.getDouble(1)
            result.add(Triple("", name, total))
        }
        cursor.close()
        return result
    }

    // 获取某月有账单的日期列表（按日期倒序）
    fun getDaysInMonth(year: Int, month: Int): List<DayBills> {
        val bills = getByMonth(year, month)
        if (bills.isEmpty()) return emptyList()

        val cal = java.util.Calendar.getInstance()
        val headerFormat = java.text.SimpleDateFormat("MM月dd日 EEEE", java.util.Locale.CHINA)

        return bills.groupBy { bill ->
            cal.timeInMillis = bill.billTime
            cal.get(java.util.Calendar.DAY_OF_YEAR)
        }.map { (_, dayBills) ->
            val firstBill = dayBills.first()
            cal.timeInMillis = firstBill.billTime
            val expense = dayBills.filter { it.type == Bill.TYPE_EXPENSE }.sumOf { it.money }
            val income = dayBills.filter { it.type == Bill.TYPE_INCOME }.sumOf { it.money }
            DayBills(
                dateText = headerFormat.format(cal.time),
                dayStart = firstBill.billTime,
                bills = dayBills.sortedByDescending { it.billTime },
                totalExpense = expense,
                totalIncome = income
            )
        }.sortedByDescending { it.dayStart }
    }

    data class DayBills(
        val dateText: String,
        val dayStart: Long,
        val bills: List<Bill>,
        val totalExpense: Double,
        val totalIncome: Double
    )

    // 检查是否已存在（去重用）
    fun existsByTimeAndMoney(money: Double, billTime: Long, payType: String): Boolean {
        val db = dbHelper.readableDatabase
        val cursor = db.rawQuery(
            "SELECT COUNT(*) FROM $TABLE_BILL WHERE $COL_MONEY = ? AND $COL_BILL_TIME = ? AND $COL_PAY_TYPE = ?",
            arrayOf(money.toString(), billTime.toString(), payType)
        )
        cursor.moveToFirst()
        val count = cursor.getInt(0)
        cursor.close()
        return count > 0
    }

    private fun cursorToBill(cursor: Cursor): Bill {
        return Bill(
            id = cursor.getLong(cursor.getColumnIndexOrThrow(COL_ID)),
            money = cursor.getDouble(cursor.getColumnIndexOrThrow(COL_MONEY)),
            type = cursor.getInt(cursor.getColumnIndexOrThrow(COL_TYPE)),
            categoryId = cursor.getLong(cursor.getColumnIndexOrThrow(COL_CATEGORY_ID)),
            categoryName = cursor.getString(cursor.getColumnIndexOrThrow(COL_CATEGORY_NAME)) ?: "",
            categoryIcon = cursor.getString(cursor.getColumnIndexOrThrow(COL_CATEGORY_ICON)) ?: "",
            payType = cursor.getString(cursor.getColumnIndexOrThrow(COL_PAY_TYPE)) ?: "",
            billTime = cursor.getLong(cursor.getColumnIndexOrThrow(COL_BILL_TIME)),
            remark = cursor.getString(cursor.getColumnIndexOrThrow(COL_REMARK)) ?: "",
            source = cursor.getString(cursor.getColumnIndexOrThrow(COL_SOURCE)) ?: "",
            importBatchId = cursor.getString(cursor.getColumnIndexOrThrow(COL_IMPORT_BATCH)) ?: ""
        )
    }
}
