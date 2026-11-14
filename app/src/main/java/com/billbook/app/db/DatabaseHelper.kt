package com.billbook.app.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class DatabaseHelper(context: Context) : SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {

    companion object {
        const val DB_NAME = "billbook.db"
        const val DB_VERSION = 5

        // 账单表
        const val TABLE_BILL = "tb_bill"
        const val COL_ID = "id"
        const val COL_MONEY = "money"
        const val COL_TYPE = "type"
        const val COL_CATEGORY_ID = "category_id"
        const val COL_CATEGORY_NAME = "category_name"
        const val COL_CATEGORY_ICON = "category_icon"
        const val COL_PAY_TYPE = "pay_type"
        const val COL_BILL_TIME = "bill_time"
        const val COL_REMARK = "remark"
        const val COL_SOURCE = "source"
        const val COL_IMPORT_BATCH = "import_batch_id"
        const val COL_CREATED_AT = "created_at"

        // 分类表
        const val TABLE_CATEGORY = "tb_category"
        const val COL_CAT_ID = "id"
        const val COL_CAT_NAME = "name"
        const val COL_CAT_ICON = "icon"
        const val COL_CAT_ICON_RES_ID = "icon_res_id"
        const val COL_CAT_TYPE = "type"
        const val COL_CAT_SORT = "sort_order"
        const val COL_CAT_DEFAULT = "is_default"

        // 导入配置表
        const val TABLE_IMPORT_CONFIG = "tb_import_config"
        const val COL_CFG_ID = "id"
        const val COL_CFG_KEYWORD = "merchant_keyword"
        const val COL_CFG_CATEGORY_ID = "map_category_id"
        const val COL_CFG_CATEGORY_NAME = "map_category_name"
        const val COL_CFG_ENABLED = "is_enabled"

        // 预算分类表
        const val TABLE_BUDGET_CATEGORY = "tb_budget_category"
        const val COL_BUDGET_CAT_ID = "id"
        const val COL_BUDGET_CAT_NAME = "name"
        const val COL_BUDGET_CAT_ICON = "icon"
        const val COL_BUDGET_CAT_AMOUNT = "budget_amount"
        const val COL_BUDGET_CAT_YEAR_MONTH = "year_month"
        const val COL_BUDGET_CAT_SORT = "sort_order"
    }

    override fun onCreate(db: SQLiteDatabase) {
        // 创建账单表
        db.execSQL("""
            CREATE TABLE $TABLE_BILL (
                $COL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_MONEY REAL NOT NULL,
                $COL_TYPE INTEGER NOT NULL DEFAULT 0,
                $COL_CATEGORY_ID INTEGER NOT NULL DEFAULT 0,
                $COL_CATEGORY_NAME TEXT NOT NULL DEFAULT '',
                $COL_CATEGORY_ICON TEXT DEFAULT '',
                $COL_PAY_TYPE TEXT NOT NULL DEFAULT '微信',
                $COL_BILL_TIME INTEGER NOT NULL,
                $COL_REMARK TEXT DEFAULT '',
                $COL_SOURCE TEXT DEFAULT '手动录入',
                $COL_IMPORT_BATCH TEXT DEFAULT '',
                $COL_CREATED_AT INTEGER NOT NULL DEFAULT 0
            )
        """)

        // 创建索引
        db.execSQL("CREATE INDEX idx_bill_time ON $TABLE_BILL($COL_BILL_TIME)")
        db.execSQL("CREATE INDEX idx_bill_type ON $TABLE_BILL($COL_TYPE)")
        db.execSQL("CREATE INDEX idx_bill_import ON $TABLE_BILL($COL_IMPORT_BATCH)")

        // 创建分类表
        db.execSQL("""
            CREATE TABLE $TABLE_CATEGORY (
                $COL_CAT_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_CAT_NAME TEXT NOT NULL,
                $COL_CAT_ICON TEXT DEFAULT '',
                $COL_CAT_ICON_RES_ID INTEGER DEFAULT 0,
                $COL_CAT_TYPE INTEGER NOT NULL DEFAULT 0,
                $COL_CAT_SORT INTEGER DEFAULT 0,
                $COL_CAT_DEFAULT INTEGER DEFAULT 0
            )
        """)

        // 创建导入配置表
        db.execSQL("""
            CREATE TABLE $TABLE_IMPORT_CONFIG (
                $COL_CFG_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_CFG_KEYWORD TEXT NOT NULL,
                $COL_CFG_CATEGORY_ID INTEGER DEFAULT 0,
                $COL_CFG_CATEGORY_NAME TEXT DEFAULT '',
                $COL_CFG_ENABLED INTEGER DEFAULT 1
            )
        """)

        // 创建预算分类表
        db.execSQL("""
            CREATE TABLE $TABLE_BUDGET_CATEGORY (
                $COL_BUDGET_CAT_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_BUDGET_CAT_NAME TEXT NOT NULL,
                $COL_BUDGET_CAT_ICON TEXT DEFAULT '',
                $COL_BUDGET_CAT_AMOUNT REAL NOT NULL DEFAULT 0,
                $COL_BUDGET_CAT_YEAR_MONTH TEXT NOT NULL,
                $COL_BUDGET_CAT_SORT INTEGER DEFAULT 0
            )
        """)

        db.execSQL("CREATE INDEX idx_budget_ym ON $TABLE_BUDGET_CATEGORY($COL_BUDGET_CAT_YEAR_MONTH)")

        // 插入默认分类数据
        insertDefaultCategories(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            // 创建预算分类表
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS $TABLE_BUDGET_CATEGORY (
                    $COL_BUDGET_CAT_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                    $COL_BUDGET_CAT_NAME TEXT NOT NULL,
                    $COL_BUDGET_CAT_ICON TEXT DEFAULT '',
                    $COL_BUDGET_CAT_AMOUNT REAL NOT NULL DEFAULT 0,
                    $COL_BUDGET_CAT_YEAR_MONTH TEXT NOT NULL,
                    $COL_BUDGET_CAT_SORT INTEGER DEFAULT 0
                )
            """)
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_budget_ym ON $TABLE_BUDGET_CATEGORY($COL_BUDGET_CAT_YEAR_MONTH)")
        }
        if (oldVersion < 3) {
            db.execSQL("ALTER TABLE $TABLE_BILL ADD COLUMN $COL_CATEGORY_ICON TEXT DEFAULT ''")
            // 回填已有账单的分类图标
            db.execSQL("UPDATE $TABLE_BILL SET $COL_CATEGORY_ICON = (SELECT $COL_CAT_ICON FROM $TABLE_CATEGORY WHERE $TABLE_CATEGORY.$COL_CAT_NAME = $TABLE_BILL.$COL_CATEGORY_NAME AND $TABLE_CATEGORY.$COL_CAT_TYPE = $TABLE_BILL.$COL_TYPE) WHERE $COL_CATEGORY_ICON = '' OR $COL_CATEGORY_ICON IS NULL")
        }
        if (oldVersion < 4) {
            // 添加新的支出分类
            val newExpenseCategories = listOf(
                Triple("日用", "", 12), Triple("蔬菜", "", 13), Triple("水果", "", 14),
                Triple("酒水", "", 15), Triple("汽车", "", 16), Triple("书籍", "", 17),
                Triple("美容", "", 18), Triple("孩子", "", 19), Triple("宠物", "", 20),
                Triple("健身", "", 21), Triple("游戏", "", 22), Triple("住房", "", 23),
                Triple("运动", "", 24), Triple("旅行", "", 25), Triple("零食", "", 26),
                Triple("咖啡", "", 27)
            )
            newExpenseCategories.forEach { (name, icon, sort) ->
                db.execSQL(
                    "INSERT OR IGNORE INTO $TABLE_CATEGORY ($COL_CAT_NAME, $COL_CAT_ICON, $COL_CAT_TYPE, $COL_CAT_SORT, $COL_CAT_DEFAULT) VALUES (?, ?, 0, ?, 1)",
                    arrayOf(name, icon, sort)
                )
            }
            // 添加新的收入分类
            val newIncomeCategories = listOf(
                Triple("礼金", "", 6), Triple("兼职", "", 7), Triple("分红", "", 8)
            )
            newIncomeCategories.forEach { (name, icon, sort) ->
                db.execSQL(
                    "INSERT OR IGNORE INTO $TABLE_CATEGORY ($COL_CAT_NAME, $COL_CAT_ICON, $COL_CAT_TYPE, $COL_CAT_SORT, $COL_CAT_DEFAULT) VALUES (?, ?, 1, ?, 1)",
                    arrayOf(name, icon, sort)
                )
            }
        }
        if (oldVersion < 5) {
            // 添加图标资源ID字段
            db.execSQL("ALTER TABLE $TABLE_CATEGORY ADD COLUMN $COL_CAT_ICON_RES_ID INTEGER DEFAULT 0")
        }
    }

    // 分类图标统一由 CategoryIconMapper 按名称解析为新 drawable；
    // 这里不再写入 emoji 字符串，存量数据的 emoji 由 resolveIcon 兼容回退。
    private fun insertDefaultCategories(db: SQLiteDatabase) {
        val defaultExpenseCategories = listOf(
            Triple("餐饮", "", 0), Triple("交通", "", 1), Triple("购物", "", 2),
            Triple("娱乐", "", 3), Triple("居住", "", 4), Triple("医疗", "", 5),
            Triple("教育", "", 6), Triple("通讯", "", 7), Triple("服饰", "", 8),
            Triple("红包", "", 9), Triple("转账", "", 10), Triple("其他", "", 11),
            Triple("日用", "", 12), Triple("蔬菜", "", 13), Triple("水果", "", 14),
            Triple("酒水", "", 15), Triple("汽车", "", 16), Triple("书籍", "", 17),
            Triple("美容", "", 18), Triple("孩子", "", 19), Triple("宠物", "", 20),
            Triple("健身", "", 21), Triple("游戏", "", 22), Triple("住房", "", 23),
            Triple("运动", "", 24), Triple("旅行", "", 25), Triple("零食", "", 26),
            Triple("咖啡", "", 27)
        )

        val defaultIncomeCategories = listOf(
            Triple("工资", "", 0), Triple("奖金", "", 1), Triple("红包", "", 2),
            Triple("转账", "", 3), Triple("理财", "", 4), Triple("其他", "", 5),
            Triple("礼金", "", 6), Triple("兼职", "", 7), Triple("分红", "", 8)
        )

        defaultExpenseCategories.forEach { (name, icon, sort) ->
            db.execSQL(
                "INSERT INTO $TABLE_CATEGORY ($COL_CAT_NAME, $COL_CAT_ICON, $COL_CAT_TYPE, $COL_CAT_SORT, $COL_CAT_DEFAULT) VALUES (?, ?, 0, ?, 1)",
                arrayOf(name, icon, sort)
            )
        }

        defaultIncomeCategories.forEach { (name, icon, sort) ->
            db.execSQL(
                "INSERT INTO $TABLE_CATEGORY ($COL_CAT_NAME, $COL_CAT_ICON, $COL_CAT_TYPE, $COL_CAT_SORT, $COL_CAT_DEFAULT) VALUES (?, ?, 1, ?, 1)",
                arrayOf(name, icon, sort)
            )
        }

        // 插入默认微信商户→分类映射规则
        val defaultMappings = listOf(
            "美团" to "餐饮", "饿了么" to "餐饮", "肯德基" to "餐饮",
            "麦当劳" to "餐饮", "星巴克" to "餐饮", "瑞幸" to "餐饮",
            "滴滴" to "交通", "高德" to "交通", "地铁" to "交通",
            "淘宝" to "购物", "京东" to "购物", "拼多多" to "购物",
            "天猫" to "购物", "唯品会" to "购物",
            "电影" to "娱乐", "游戏" to "娱乐", "腾讯" to "娱乐",
            "物业" to "居住", "电费" to "居住", "水费" to "居住",
            "医院" to "医疗", "药房" to "医疗", "药店" to "医疗",
            "话费" to "通讯", "中国移动" to "通讯", "中国联通" to "通讯"
        )

        defaultMappings.forEach { (keyword, categoryName) ->
            db.execSQL(
                "INSERT INTO $TABLE_IMPORT_CONFIG ($COL_CFG_KEYWORD, $COL_CFG_CATEGORY_NAME, $COL_CFG_ENABLED) VALUES (?, ?, 1)",
                arrayOf(keyword, categoryName)
            )
        }
    }
}
