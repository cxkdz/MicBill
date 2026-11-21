package com.billbook.app.util

import android.content.Context
import android.net.Uri
import com.billbook.app.db.CategoryDao
import com.billbook.app.db.ImportConfigDao
import com.billbook.app.model.Bill
import com.billbook.app.model.ImportConfig
import org.apache.poi.ss.usermodel.*
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.UUID

/**
 * 微信账单解析器（支持CSV和Excel格式）
 *
 * 微信账单格式说明：
 * - CSV: 前16行为文件头，第17行为表头，第18行起为数据
 * - Excel: 第1行为表头，第2行起为数据
 * - 列: 交易时间,交易类型,交易对方,商品,收/支,金额(元),支付方式,当前状态,交易单号,商户单号,备注
 */
class WeChatBillParser(private val context: Context) {

    companion object {
        // 列索引
        const val COL_TIME = 0          // 交易时间
        const val COL_TYPE = 1          // 交易类型
        const val COL_COUNTERPARTY = 2  // 交易对方
        const val COL_PRODUCT = 3       // 商品
        const val COL_DIRECTION = 4     // 收/支
        const val COL_AMOUNT = 5        // 金额(元)
        const val COL_PAY_METHOD = 6    // 支付方式
        const val COL_STATUS = 7        // 当前状态
        const val COL_ORDER_NO = 8      // 交易单号
        const val COL_MERCHANT_NO = 9   // 商户单号
        const val COL_REMARK = 10       // 备注

        // 要过滤的交易类型
        val FILTERED_TYPES = setOf("转账", "微信红包", "零钱充值", "零钱提现", "理财通")

        // 时间格式
        val DATE_FORMAT = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA)
        val DATE_FORMAT_EXCEL = SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.CHINA)
    }

    data class ParseResult(
        val bills: List<Bill>,
        val totalCount: Int,
        val successCount: Int,
        val skipCount: Int,
        val errorMessages: List<String>
    )

    /**
     * 解析微信账单文件（自动检测格式）
     * @param uri 文件URI
     * @param skipDuplicate 是否跳过已存在的记录
     * @param filterTypes 是否过滤非消费类交易
     */
    fun parse(uri: Uri, skipDuplicate: Boolean = true, filterTypes: Boolean = true): ParseResult {
        val fileName = getFileName(uri)
        return when {
            fileName.endsWith(".xlsx", true) || fileName.endsWith(".xls", true) -> {
                parseExcel(uri, skipDuplicate, filterTypes)
            }
            else -> {
                parseCsv(uri, skipDuplicate, filterTypes)
            }
        }
    }

    /**
     * 获取文件名
     */
    private fun getFileName(uri: Uri): String {
        var fileName = ""
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && nameIndex >= 0) {
                fileName = cursor.getString(nameIndex) ?: ""
            }
        }
        return fileName
    }

    /**
     * 解析Excel格式的微信账单
     */
    private fun parseExcel(uri: Uri, skipDuplicate: Boolean, filterTypes: Boolean): ParseResult {
        val bills = mutableListOf<Bill>()
        val errors = mutableListOf<String>()
        var skipCount = 0
        var totalCount = 0
        val batchId = UUID.randomUUID().toString()

        val importConfigDao = ImportConfigDao(context)
        val mappingRules = importConfigDao.getAllEnabled()
        val categoryDao = CategoryDao(context)

        try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return ParseResult(
                emptyList(), 0, 0, 0, listOf("无法打开文件")
            )

            val workbook = XSSFWorkbook(inputStream)
            val sheet = workbook.getSheetAt(0)

            // 找到表头行
            var headerRowIndex = -1
            for (i in 0..sheet.lastRowNum) {
                val row = sheet.getRow(i) ?: continue
                val firstCell = row.getCell(0)?.toString() ?: ""
                if (firstCell.contains("交易时间")) {
                    headerRowIndex = i
                    break
                }
            }

            if (headerRowIndex == -1) {
                workbook.close()
                return ParseResult(emptyList(), 0, 0, 0, listOf("未找到表头行"))
            }

            // 从表头下一行开始解析数据
            for (i in (headerRowIndex + 1)..sheet.lastRowNum) {
                val row = sheet.getRow(i) ?: continue
                totalCount++

                try {
                    val transType = getCellString(row, COL_TYPE)
                    val direction = getCellString(row, COL_DIRECTION)
                    val status = getCellString(row, COL_STATUS)

                    // 过滤非消费类交易
                    if (filterTypes && transType in FILTERED_TYPES) {
                        skipCount++
                        continue
                    }

                    // 过滤退款/失败的交易
                    if (status != "支付成功" && status != "已转账" && status != "已收钱" && status != "已存入零钱") {
                        skipCount++
                        continue
                    }

                    // 过滤"不计收支"
                    if (direction == "/" || direction == "不计收支") {
                        skipCount++
                        continue
                    }

                    // 解析金额
                    val amountStr = getCellString(row, COL_AMOUNT)
                        .replace("¥", "")
                        .replace(",", "")
                        .trim()
                    val amount = amountStr.toDoubleOrNull()
                    if (amount == null || amount <= 0) {
                        errors.add("第${i + 1}行：金额解析失败[$amountStr]")
                        skipCount++
                        continue
                    }

                    // 判断收入/支出
                    val billType = when (direction) {
                        "支出" -> Bill.TYPE_EXPENSE
                        "收入" -> Bill.TYPE_INCOME
                        else -> Bill.TYPE_EXPENSE
                    }

                    // 解析时间
                    val timeStr = getCellString(row, COL_TIME)
                    val billTime = parseTime(timeStr)

                    // 解析商户名和备注
                    val counterparty = getCellString(row, COL_COUNTERPARTY)
                    val product = getCellString(row, COL_PRODUCT)

                    // 自动匹配分类
                    val matchedCategory = matchCategory(counterparty, product, mappingRules, categoryDao, billType)

                    // 去重检查
                    if (skipDuplicate) {
                        val exists = com.billbook.app.db.BillDao(context).existsByTimeAndMoney(amount, billTime, Bill.PAY_WECHAT)
                        if (exists) {
                            skipCount++
                            continue
                        }
                    }

                    val bill = Bill(
                        money = amount,
                        type = billType,
                        categoryId = matchedCategory?.id ?: 0,
                        categoryName = matchedCategory?.name ?: "其他",
                        payType = Bill.PAY_WECHAT,
                        billTime = billTime,
                        remark = if (product.isNotEmpty()) "$counterparty - $product" else counterparty,
                        source = Bill.SOURCE_WECHAT_IMPORT,
                        importBatchId = batchId
                    )

                    bills.add(bill)
                } catch (e: Exception) {
                    errors.add("第${i + 1}行：解析异常 - ${e.message}")
                    skipCount++
                }
            }

            workbook.close()
        } catch (e: Exception) {
            errors.add("文件读取异常：${e.message}")
        }

        return ParseResult(
            bills = bills,
            totalCount = totalCount,
            successCount = bills.size,
            skipCount = skipCount,
            errorMessages = errors
        )
    }

    /**
     * 解析CSV格式的微信账单
     */
    private fun parseCsv(uri: Uri, skipDuplicate: Boolean, filterTypes: Boolean): ParseResult {
        val bills = mutableListOf<Bill>()
        val errors = mutableListOf<String>()
        var skipCount = 0
        var totalCount = 0
        val batchId = UUID.randomUUID().toString()

        val importConfigDao = ImportConfigDao(context)
        val mappingRules = importConfigDao.getAllEnabled()
        val categoryDao = CategoryDao(context)

        try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return ParseResult(
                emptyList(), 0, 0, 0, listOf("无法打开文件")
            )

            val reader = java.io.BufferedReader(java.io.InputStreamReader(inputStream, "GBK"))
            val lines = reader.readLines()
            reader.close()

            val dataStartIndex = findDataStart(lines)

            for (i in dataStartIndex until lines.size) {
                val line = lines[i].trim()
                if (line.isEmpty() || line.startsWith("-") || (line.contains("共") && line.contains("笔"))) {
                    continue
                }

                totalCount++

                try {
                    val fields = parseCsvLine(line)
                    if (fields.size < 11) {
                        errors.add("第${i + 1}行：字段不足，跳过")
                        skipCount++
                        continue
                    }

                    val transType = fields[COL_TYPE].trim()
                    val direction = fields[COL_DIRECTION].trim()
                    val status = fields[COL_STATUS].trim()

                    if (filterTypes && transType in FILTERED_TYPES) {
                        skipCount++
                        continue
                    }

                    if (status != "支付成功" && status != "已转账" && status != "已收钱" && status != "已存入零钱") {
                        skipCount++
                        continue
                    }

                    if (direction == "/" || direction == "不计收支") {
                        skipCount++
                        continue
                    }

                    val amountStr = fields[COL_AMOUNT].trim().replace("¥", "").replace(",", "")
                    val amount = amountStr.toDoubleOrNull()
                    if (amount == null || amount <= 0) {
                        errors.add("第${i + 1}行：金额解析失败[$amountStr]")
                        skipCount++
                        continue
                    }

                    val billType = when (direction) {
                        "支出" -> Bill.TYPE_EXPENSE
                        "收入" -> Bill.TYPE_INCOME
                        else -> Bill.TYPE_EXPENSE
                    }

                    val timeStr = fields[COL_TIME].trim()
                    val billTime = parseTime(timeStr)

                    val counterparty = fields[COL_COUNTERPARTY].trim()
                    val product = fields[COL_PRODUCT].trim()

                    val matchedCategory = matchCategory(counterparty, product, mappingRules, categoryDao, billType)

                    if (skipDuplicate) {
                        val exists = com.billbook.app.db.BillDao(context).existsByTimeAndMoney(amount, billTime, Bill.PAY_WECHAT)
                        if (exists) {
                            skipCount++
                            continue
                        }
                    }

                    val bill = Bill(
                        money = amount,
                        type = billType,
                        categoryId = matchedCategory?.id ?: 0,
                        categoryName = matchedCategory?.name ?: "其他",
                        payType = Bill.PAY_WECHAT,
                        billTime = billTime,
                        remark = if (product.isNotEmpty()) "$counterparty - $product" else counterparty,
                        source = Bill.SOURCE_WECHAT_IMPORT,
                        importBatchId = batchId
                    )

                    bills.add(bill)
                } catch (e: Exception) {
                    errors.add("第${i + 1}行：解析异常 - ${e.message}")
                    skipCount++
                }
            }
        } catch (e: Exception) {
            errors.add("文件读取异常：${e.message}")
        }

        return ParseResult(
            bills = bills,
            totalCount = totalCount,
            successCount = bills.size,
            skipCount = skipCount,
            errorMessages = errors
        )
    }

    /**
     * 获取单元格字符串值
     */
    private fun getCellString(row: Row, colIndex: Int): String {
        val cell = row.getCell(colIndex) ?: return ""
        return when (cell.cellType) {
            CellType.STRING -> cell.stringCellValue
            CellType.NUMERIC -> {
                if (DateUtil.isCellDateFormatted(cell)) {
                    val date = cell.dateCellValue
                    DATE_FORMAT.format(date)
                } else {
                    cell.numericCellValue.toString()
                }
            }
            CellType.BOOLEAN -> cell.booleanCellValue.toString()
            else -> ""
        }
    }

    /**
     * 解析时间字符串
     */
    private fun parseTime(timeStr: String): Long {
        return try {
            DATE_FORMAT.parse(timeStr)?.time
                ?: DATE_FORMAT_EXCEL.parse(timeStr)?.time
                ?: System.currentTimeMillis()
        } catch (e: Exception) {
            System.currentTimeMillis()
        }
    }

    private fun findDataStart(lines: List<String>): Int {
        for (i in lines.indices) {
            val line = lines[i].trim()
            if (line.contains("交易时间") && line.contains("交易类型")) {
                return i + 1
            }
        }
        return if (lines.size > 17) 17 else 0
    }

    private fun parseCsvLine(line: String): List<String> {
        val fields = mutableListOf<String>()
        var current = StringBuilder()
        var inQuotes = false

        for (char in line) {
            when {
                char == '"' -> inQuotes = !inQuotes
                char == ',' && !inQuotes -> {
                    fields.add(current.toString())
                    current = StringBuilder()
                }
                else -> current.append(char)
            }
        }
        fields.add(current.toString())
        return fields
    }

    private fun matchCategory(
        counterparty: String,
        product: String,
        rules: List<ImportConfig>,
        categoryDao: CategoryDao,
        billType: Int
    ): com.billbook.app.model.Category? {
        val searchText = "$counterparty $product"
        return com.billbook.app.autobill.CategoryMatcher.match(
            searchText, rules, categoryDao, billType
        )
    }

    fun preview(uri: Uri): ParseResult {
        return parse(uri, skipDuplicate = false, filterTypes = true)
    }
}
