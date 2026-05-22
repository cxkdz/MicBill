package com.billbook.app.model

data class Bill(
    var id: Long = 0,
    var money: Double = 0.0,
    var type: Int = TYPE_EXPENSE,        // 0支出 1收入
    var categoryId: Long = 0,
    var categoryName: String = "",
    var categoryIcon: String = "",       // 保留字段兼容数据库
    var categoryIconResId: Int = 0,      // 新增：drawable 资源 ID
    var payType: String = PAY_WECHAT,    // 支付渠道
    var billTime: Long = System.currentTimeMillis(),
    var remark: String = "",
    var source: String = SOURCE_MANUAL,  // 数据来源
    var importBatchId: String = ""       // 导入批次ID，用于去重
) {
    companion object {
        const val TYPE_EXPENSE = 0
        const val TYPE_INCOME = 1

        const val PAY_WECHAT = "微信"
        const val PAY_ALIPAY = "支付宝"
        const val PAY_CASH = "现金"
        const val PAY_BANK = "银行卡"
        const val PAY_OTHER = "其他"

        const val SOURCE_MANUAL = "手动录入"
        const val SOURCE_WECHAT_IMPORT = "微信导入"
        const val SOURCE_ALIPAY_IMPORT = "支付宝导入"

        val PAY_TYPES = listOf(PAY_WECHAT, PAY_ALIPAY, PAY_CASH, PAY_BANK, PAY_OTHER)
    }
}
