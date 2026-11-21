package com.billbook.app.util

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import com.billbook.app.db.DatabaseHelper
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 整库 JSON 备份 / 覆盖式恢复。
 *
 * 覆盖 4 张业务表 + 预算偏好；恢复在**单个事务**内先清空再逐行 insertOrThrow，
 * 任何一行失败都会整体回滚，不会留下半套数据。
 * 列映射按 Cursor.getType / JSON 值类型泛化，以后加字段不必改这里；
 * 恢复时只写当前 schema 里存在的列，兼容旧版本备份的多余字段。
 */
object BackupManager {

    private const val FORMAT = "billbook-backup"
    private const val VERSION = 1
    private const val KEY_META = "meta"
    private const val KEY_SETTINGS = "settings"
    private const val BUDGET_PREFS = "budget_prefs"

    private val TABLES = listOf(
        DatabaseHelper.TABLE_BILL,
        DatabaseHelper.TABLE_CATEGORY,
        DatabaseHelper.TABLE_IMPORT_CONFIG,
        DatabaseHelper.TABLE_BUDGET_CATEGORY
    )

    data class Meta(
        val format: String,
        val version: Int,
        val exportedAt: Long,
        val counts: Map<String, Int>
    )

    data class ExportResult(val billCount: Int, val totalRows: Int)

    /** 导出整库快照到用户选择的 URI */
    fun export(context: Context, uri: Uri): ExportResult {
        val appContext = context.applicationContext
        val db = DatabaseHelper(appContext).readableDatabase

        val root = JSONObject()
        root.put(KEY_META, JSONObject().apply {
            put("format", FORMAT)
            put("version", VERSION)
            put("exportedAt", System.currentTimeMillis())
        })

        var totalRows = 0
        var billCount = 0
        TABLES.forEach { table ->
            val arr = tableToArray(db, table)
            root.put(table, arr)
            totalRows += arr.length()
            if (table == DatabaseHelper.TABLE_BILL) billCount = arr.length()
        }

        val prefs = appContext.getSharedPreferences(BUDGET_PREFS, Context.MODE_PRIVATE)
        root.put(KEY_SETTINGS, JSONObject().apply {
            put("monthlyBudget", prefs.getFloat(BudgetManager.KEY_MONTHLY_BUDGET, 0f).toDouble())
            put("budgetEnabled", prefs.getBoolean(BudgetManager.KEY_BUDGET_ENABLED, false))
            put("alertPercent", prefs.getInt(BudgetManager.KEY_BUDGET_ALERT_PERCENT, 80))
        })

        appContext.contentResolver.openOutputStream(uri)?.use { out ->
            out.write(root.toString(2).toByteArray(Charsets.UTF_8))
        } ?: throw IllegalStateException("无法写入所选文件")

        return ExportResult(billCount, totalRows)
    }

    /** 只读文件头做预览，不碰数据库 */
    fun inspect(context: Context, uri: Uri): Meta {
        val root = readRoot(context.applicationContext, uri)
        val meta = root.getJSONObject(KEY_META)
        val format = meta.optString("format")
        if (format != FORMAT) throw IllegalArgumentException("不是 BillBook 备份文件")
        val version = meta.optInt("version", 0)
        if (version > VERSION) throw IllegalArgumentException("备份版本($version)高于当前支持版本($VERSION)")
        val counts = TABLES.associateWith { root.optJSONArray(it)?.length() ?: 0 }
        return Meta(format, version, meta.optLong("exportedAt", 0L), counts)
    }

    /** 覆盖式恢复：单事务清空 4 表并重建，失败整体回滚 */
    fun restore(context: Context, uri: Uri): Int {
        val appContext = context.applicationContext
        val root = readRoot(appContext, uri)
        val meta = root.getJSONObject(KEY_META)
        if (meta.optString("format") != FORMAT) throw IllegalArgumentException("不是 BillBook 备份文件")
        if (meta.optInt("version", 0) > VERSION) throw IllegalArgumentException("备份版本过高")

        val db = DatabaseHelper(appContext).writableDatabase
        var restored = 0
        db.beginTransaction()
        try {
            TABLES.forEach { table ->
                db.delete(table, null, null)
                val arr = root.optJSONArray(table) ?: JSONArray()
                val validColumns = columnNames(db, table).toSet()
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val values = ContentValues()
                    val keys = obj.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        if (key !in validColumns) continue
                        when (val v = obj.get(key)) {
                            is Long -> values.put(key, v)
                            is Int -> values.put(key, v.toLong())
                            is Double -> values.put(key, v)
                            is Float -> values.put(key, v.toDouble())
                            is String -> values.put(key, v)
                            is Boolean -> values.put(key, v)
                            else -> if (v === JSONObject.NULL) values.putNull(key)
                        }
                    }
                    db.insertOrThrow(table, null, values)
                    restored++
                }
            }

            val settings = root.optJSONObject(KEY_SETTINGS)
            if (settings != null) {
                appContext.getSharedPreferences(BUDGET_PREFS, Context.MODE_PRIVATE)
                    .edit()
                    .putFloat(
                        BudgetManager.KEY_MONTHLY_BUDGET,
                        settings.optDouble("monthlyBudget", 0.0).toFloat()
                    )
                    .putBoolean(BudgetManager.KEY_BUDGET_ENABLED, settings.optBoolean("budgetEnabled", false))
                    .putInt(BudgetManager.KEY_BUDGET_ALERT_PERCENT, settings.optInt("alertPercent", 80))
                    .apply()
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        return restored
    }

    private fun readRoot(context: Context, uri: Uri): JSONObject {
        val text = context.contentResolver.openInputStream(uri)?.use { input ->
            input.readBytes().toString(Charsets.UTF_8)
        } ?: throw IllegalStateException("无法读取所选文件")
        return JSONObject(text)
    }

    private fun tableToArray(db: SQLiteDatabase, table: String): JSONArray {
        val arr = JSONArray()
        db.query(table, null, null, null, null, null, null).use { cursor ->
            while (cursor.moveToNext()) {
                val obj = JSONObject()
                for (name in cursor.columnNames) {
                    val idx = cursor.getColumnIndexOrThrow(name)
                    when (cursor.getType(idx)) {
                        Cursor.FIELD_TYPE_INTEGER -> obj.put(name, cursor.getLong(idx))
                        Cursor.FIELD_TYPE_FLOAT -> obj.put(name, cursor.getDouble(idx))
                        Cursor.FIELD_TYPE_STRING -> obj.put(name, cursor.getString(idx) ?: "")
                        Cursor.FIELD_TYPE_NULL -> obj.put(name, JSONObject.NULL)
                        else -> Unit
                    }
                }
                arr.put(obj)
            }
        }
        return arr
    }

    private fun columnNames(db: SQLiteDatabase, table: String): Array<String> {
        return db.rawQuery("SELECT * FROM $table LIMIT 0", null).use { it.columnNames }
    }

    fun defaultFileName(): String {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.CHINA).format(Date())
        return "MicBill_backup_$stamp.json"
    }
}
