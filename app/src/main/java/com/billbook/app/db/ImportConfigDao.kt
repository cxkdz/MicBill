package com.billbook.app.db

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import com.billbook.app.db.DatabaseHelper.Companion.TABLE_IMPORT_CONFIG
import com.billbook.app.db.DatabaseHelper.Companion.COL_CFG_ID
import com.billbook.app.db.DatabaseHelper.Companion.COL_CFG_KEYWORD
import com.billbook.app.db.DatabaseHelper.Companion.COL_CFG_CATEGORY_ID
import com.billbook.app.db.DatabaseHelper.Companion.COL_CFG_CATEGORY_NAME
import com.billbook.app.db.DatabaseHelper.Companion.COL_CFG_ENABLED
import com.billbook.app.model.ImportConfig

class ImportConfigDao(context: Context) {

    private val dbHelper = DatabaseHelper(context)

    fun getAll(): List<ImportConfig> {
        val db = dbHelper.readableDatabase
        val cursor = db.query(TABLE_IMPORT_CONFIG, null, null, null, null, null, null)
        val configs = mutableListOf<ImportConfig>()
        while (cursor.moveToNext()) {
            configs.add(cursorToConfig(cursor))
        }
        cursor.close()
        return configs
    }

    fun getAllEnabled(): List<ImportConfig> {
        val db = dbHelper.readableDatabase
        val cursor = db.query(
            TABLE_IMPORT_CONFIG, null,
            "$COL_CFG_ENABLED = 1",
            null, null, null, null
        )
        val configs = mutableListOf<ImportConfig>()
        while (cursor.moveToNext()) {
            configs.add(cursorToConfig(cursor))
        }
        cursor.close()
        return configs
    }

    fun insert(config: ImportConfig): Long {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put(COL_CFG_KEYWORD, config.merchantKeyword)
            put(COL_CFG_CATEGORY_ID, config.mapToCategoryId)
            put(COL_CFG_CATEGORY_NAME, config.mapToCategoryName)
            put(COL_CFG_ENABLED, if (config.isEnabled) 1 else 0)
        }
        return db.insert(TABLE_IMPORT_CONFIG, null, values)
    }

    fun update(config: ImportConfig): Int {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put(COL_CFG_KEYWORD, config.merchantKeyword)
            put(COL_CFG_CATEGORY_ID, config.mapToCategoryId)
            put(COL_CFG_CATEGORY_NAME, config.mapToCategoryName)
            put(COL_CFG_ENABLED, if (config.isEnabled) 1 else 0)
        }
        return db.update(TABLE_IMPORT_CONFIG, values, "$COL_CFG_ID = ?", arrayOf(config.id.toString()))
    }

    fun delete(id: Long): Int {
        val db = dbHelper.writableDatabase
        return db.delete(TABLE_IMPORT_CONFIG, "$COL_CFG_ID = ?", arrayOf(id.toString()))
    }

    private fun cursorToConfig(cursor: Cursor): ImportConfig {
        return ImportConfig(
            id = cursor.getLong(cursor.getColumnIndexOrThrow(COL_CFG_ID)),
            merchantKeyword = cursor.getString(cursor.getColumnIndexOrThrow(COL_CFG_KEYWORD)) ?: "",
            mapToCategoryId = cursor.getLong(cursor.getColumnIndexOrThrow(COL_CFG_CATEGORY_ID)),
            mapToCategoryName = cursor.getString(cursor.getColumnIndexOrThrow(COL_CFG_CATEGORY_NAME)) ?: "",
            isEnabled = cursor.getInt(cursor.getColumnIndexOrThrow(COL_CFG_ENABLED)) == 1
        )
    }
}
