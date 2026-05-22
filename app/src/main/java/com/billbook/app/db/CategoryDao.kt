package com.billbook.app.db

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import com.billbook.app.db.DatabaseHelper.Companion.TABLE_CATEGORY
import com.billbook.app.db.DatabaseHelper.Companion.COL_CAT_ID
import com.billbook.app.db.DatabaseHelper.Companion.COL_CAT_NAME
import com.billbook.app.db.DatabaseHelper.Companion.COL_CAT_ICON
import com.billbook.app.db.DatabaseHelper.Companion.COL_CAT_ICON_RES_ID
import com.billbook.app.db.DatabaseHelper.Companion.COL_CAT_TYPE
import com.billbook.app.db.DatabaseHelper.Companion.COL_CAT_SORT
import com.billbook.app.db.DatabaseHelper.Companion.COL_CAT_DEFAULT
import com.billbook.app.model.Bill
import com.billbook.app.model.Category

class CategoryDao(context: Context) {

    private val dbHelper = DatabaseHelper(context)

    fun getAll(type: Int? = null): List<Category> {
        val db = dbHelper.readableDatabase
        val selection = type?.let { "$COL_CAT_TYPE = ?" }
        val args = type?.let { arrayOf(it.toString()) }
        val cursor = db.query(TABLE_CATEGORY, null, selection, args, null, null, "$COL_CAT_SORT ASC")

        val categories = mutableListOf<Category>()
        while (cursor.moveToNext()) {
            categories.add(cursorToCategory(cursor))
        }
        cursor.close()
        return categories
    }

    fun getById(id: Long): Category? {
        val db = dbHelper.readableDatabase
        val cursor = db.query(TABLE_CATEGORY, null, "$COL_CAT_ID = ?", arrayOf(id.toString()), null, null, null)
        return if (cursor.moveToFirst()) {
            cursorToCategory(cursor).also { cursor.close() }
        } else {
            cursor.close()
            null
        }
    }

    fun getByName(name: String, type: Int? = null): Category? {
        val db = dbHelper.readableDatabase
        val selection = if (type != null) "$COL_CAT_NAME = ? AND $COL_CAT_TYPE = ?" else "$COL_CAT_NAME = ?"
        val args = if (type != null) arrayOf(name, type.toString()) else arrayOf(name)
        val cursor = db.query(TABLE_CATEGORY, null, selection, args, null, null, null)
        return if (cursor.moveToFirst()) {
            cursorToCategory(cursor).also { cursor.close() }
        } else {
            cursor.close()
            null
        }
    }

    fun insert(category: Category): Long {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put(COL_CAT_NAME, category.name)
            put(COL_CAT_ICON, category.icon)
            put(COL_CAT_ICON_RES_ID, category.iconResId)
            put(COL_CAT_TYPE, category.type)
            put(COL_CAT_SORT, category.sortOrder)
            put(COL_CAT_DEFAULT, if (category.isDefault) 1 else 0)
        }
        return db.insert(TABLE_CATEGORY, null, values)
    }

    fun update(category: Category): Int {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put(COL_CAT_NAME, category.name)
            put(COL_CAT_ICON, category.icon)
            put(COL_CAT_ICON_RES_ID, category.iconResId)
            put(COL_CAT_SORT, category.sortOrder)
        }
        return db.update(TABLE_CATEGORY, values, "$COL_CAT_ID = ?", arrayOf(category.id.toString()))
    }

    fun delete(id: Long): Int {
        val db = dbHelper.writableDatabase
        return db.delete(TABLE_CATEGORY, "$COL_CAT_ID = ? AND $COL_CAT_DEFAULT = 0", arrayOf(id.toString()))
    }

    private fun cursorToCategory(cursor: Cursor): Category {
        return Category(
            id = cursor.getLong(cursor.getColumnIndexOrThrow(COL_CAT_ID)),
            name = cursor.getString(cursor.getColumnIndexOrThrow(COL_CAT_NAME)) ?: "",
            icon = cursor.getString(cursor.getColumnIndexOrThrow(COL_CAT_ICON)) ?: "",
            iconResId = cursor.getInt(cursor.getColumnIndexOrThrow(COL_CAT_ICON_RES_ID)),
            type = cursor.getInt(cursor.getColumnIndexOrThrow(COL_CAT_TYPE)),
            sortOrder = cursor.getInt(cursor.getColumnIndexOrThrow(COL_CAT_SORT)),
            isDefault = cursor.getInt(cursor.getColumnIndexOrThrow(COL_CAT_DEFAULT)) == 1
        )
    }
}
