package com.billbook.app.util

import android.content.Context
import android.net.Uri
import com.billbook.app.db.CategoryDao
import com.billbook.app.db.ImportConfigDao
import com.billbook.app.model.Bill
import com.billbook.app.model.ImportConfig
import org.apache.poi.ss.usermodel.*
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.UUID

/**
 * 支付宝账单解析器（支持Excel格式）
 *
 * 支付宝账单格式说明：
 * - 前几行为文件头信息
 * - 表头行包含：交易号,商家订单号,交易创建时间,付款时间,最近修改时间,交易来源地,类型,收/支,金额（元）,商品名称,...
 * - 数据从表头下一行开始
 */
class AlipayBillParser(private val context: Context) {

    companion object {
        // 支付宝账单列名
        const val COL_TRADE_NO = "交易号"
        const val COL_MERCHANT_NO = "商家订单号"
        const val COL_CREATE_TIME = "交易创建时间"
        const val COL_PAY_TIME = "付款时间"
        const val COL_LAST_MODIFY = "最近修改时间"
        const val COL_SOURCE = "交易来源地"
        const val COL_TYPE = "类型"
        const val COL_DIRECTION = "收/支"
        const val COL_AMOUNT = "金额（元）"
        const val COL_PRODUCT = "商品名称"
        const val COL_STATUS = "交易状态"
        const val COL_SERVICE_FEE = "服务费（元）"
        const val COL_REFUND = "退款（元）"
        const val COL_REMARK = "备注"

        // 要过滤的交易类型
        val FILTERED_TYPES = setOf("转账", "提现", "充值", "还款", "理财")

        // 时间格式
        val DATE_FORMAT = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA)
        val DATE_FORMAT_ALT = SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.CHINA)
    }

    data class ParseResult(
        val bills: List<Bill>,
        val totalCount: Int,
        val successCount: Int,
        val skipCount: Int,
        val errorMessages: List<String>
    )

    /**
     * 解析支付宝账单Excel文件
     * @param uri 文件URI
     * @param skipDuplicate 是否跳过已存在的记录
     * @param filterTypes 是否过滤非消费类交易
     */
    fun parse(uri: Uri, skipDuplicate: Boolean = true, filterTypes: Boolean = true): ParseResult {
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

            // 解析表头，建立列名到索引的映射
            val headerMap = mutableMapOf<String, Int>()
            var headerRowIndex = -1

            for (i in 0..minOf(20, sheet.lastRowNum)) {
                val row = sheet.getRow(i) ?: continue
                for (j in 0..row.lastCellNum) {
                    val cellValue = row.getCell(j)?.toString()?.trim() ?: ""
                    if (cellValue == COL_TRADE_NO || cellValue == COL_CREATE_TIME) {
                        headerRowIndex = i
                        break
                    }
                }
                if (headerRowIndex >= 0) break
            }

            if (headerRowIndex == -1) {
                workbook.close()
                return ParseResult(emptyList(), 0, 0, 0, listOf("未找到支付宝账单表头"))
            }

            // 读取表头
            val headerRow = sheet.getRow(headerRowIndex)
            for (j in 0..headerRow.lastCellNum) {
                val cellValue = headerRow.getCell(j)?.toString()?.trim() ?: ""
                if (cellValue.isNotEmpty()) {
                    headerMap[cellValue] = j
                }
            }

            // 获取必要列的索引
            val colCreateTime = headerMap[COL_CREATE_TIME] ?: headerMap["交易时间"] ?: -1
            val colType = headerMap[COL_TYPE] ?: -1
            val colDirection = headerMap[COL_DIRECTION] ?: -1
            val colAmount = headerMap[COL_AMOUNT] ?: -1
            val colProduct = headerMap[COL_PRODUCT] ?: headerMap["商品说明"] ?: -1
            val colStatus = headerMap[COL_STATUS] ?: -1
            val colRemark = headerMap[COL_REMARK] ?: -1

            if (colCreateTime == -1 || colAmount == -1) {
                workbook.close()
                return ParseResult(emptyList(), 0, 0, 0, listOf("缺少必要列（交易时间或金额）"))
            }

            // 从表头下一行开始解析数据
            for (i in (headerRowIndex + 1)..sheet.lastRowNum) {
                val row = sheet.getRow(i) ?: continue

                // 跳过空行或汇总行
                val firstCell = row.getCell(0)?.toString()?.trim() ?: ""
                if (firstCell.isEmpty() || firstCell.contains("合计") || firstCell.contains("共")) {
                    continue
                }

                totalCount++

                try {
                    val transType = if (colType >= 0) getCellString(row, colType) else ""
                    val direction = if (colDirection >= 0) getCellString(row, colDirection) else ""
                    val status = if (colStatus >= 0) getCellString(row, colStatus) else ""

                    // 过滤非消费类交易
                    if (filterTypes && transType.isNotEmpty()) {
                        val shouldFilter = FILTERED_TYPES.any { transType.contains(it) }
                        if (shouldFilter) {
                            skipCount++
                            continue
                        }
                    }

                    // 过滤交易状态不是成功的
                    if (status.isNotEmpty() && !status.contains("成功") && !status.contains("完成")) {
                        skipCount++
                        continue
                    }

                    // 过滤"不计收支"
                    if (direction.isEmpty() || direction == "不计收支" || direction == "/") {
                        skipCount++
                        continue
                    }

                    // 解析金额
                    val amountStr = getCellString(row, colAmount)
                        .replace("¥", "")
                        .replace(",", "")
                        .replace("￥", "")
                        .trim()
                    val amount = amountStr.toDoubleOrNull()
                    if (amount == null || amount <= 0) {
                        errors.add("第${i + 1}行：金额解析失败[$amountStr]")
                        skipCount++
                        continue
                    }

                    // 判断收入/支出
                    val billType = when {
                        direction.contains("支出") -> Bill.TYPE_EXPENSE
                        direction.contains("收入") -> Bill.TYPE_INCOME
                        else -> Bill.TYPE_EXPENSE
                    }

                    // 解析时间
                    val timeStr = getCellString(row, colCreateTime)
                    val billTime = parseTime(timeStr)

                    // 解析商品名和备注
                    val product = if (colProduct >= 0) getCellString(row, colProduct) else ""
                    val remark = if (colRemark >= 0) getCellString(row, colRemark) else ""

                    // 自动匹配分类
                    val matchedCategory = matchCategory(product, remark, mappingRules, categoryDao, billType)

                    // 去重检查
                    if (skipDuplicate) {
                        val exists = com.billbook.app.db.BillDao(context).existsByTimeAndMoney(amount, billTime, Bill.PAY_ALIPAY)
                        if (exists) {
                            skipCount++
                            continue
                        }
                    }

                    val displayName = product.ifEmpty { remark }

                    val bill = Bill(
                        money = amount,
                        type = billType,
                        categoryId = matchedCategory?.id ?: 0,
                        categoryName = matchedCategory?.name ?: "其他",
                        payType = Bill.PAY_ALIPAY,
                        billTime = billTime,
                        remark = displayName,
                        source = Bill.SOURCE_ALIPAY_IMPORT,
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
                    // 避免科学计数法
                    val value = cell.numericCellValue
                    if (value == value.toLong().toDouble()) {
                        value.toLong().toString()
                    } else {
                        value.toString()
                    }
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
                ?: DATE_FORMAT_ALT.parse(timeStr)?.time
                ?: System.currentTimeMillis()
        } catch (e: Exception) {
            System.currentTimeMillis()
        }
    }

    /**
     * 根据商品名自动匹配分类
     */
    private fun matchCategory(
        product: String,
        remark: String,
        rules: List<ImportConfig>,
        categoryDao: CategoryDao,
        billType: Int
    ): com.billbook.app.model.Category? {
        val searchText = "$product $remark"
        return com.billbook.app.autobill.CategoryMatcher.match(
            searchText, rules, categoryDao, billType
        )
    }

    /**
     * 预览解析结果
     */
    fun preview(uri: Uri): ParseResult {
        return parse(uri, skipDuplicate = false, filterTypes = true)
    }
}
