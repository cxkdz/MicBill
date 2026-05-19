package com.billbook.app.model

data class ImportConfig(
    var id: Long = 0,
    var merchantKeyword: String = "",    // 商户名关键词
    var mapToCategoryId: Long = 0,       // 映射到的分类ID
    var mapToCategoryName: String = "",
    var isEnabled: Boolean = true
) {
    companion object {
        // 微信账单商户名 → 分类的默认映射规则
        val DEFAULT_MAPPING_RULES = listOf(
            ImportConfig(merchantKeyword = "美团", mapToCategoryName = "餐饮"),
            ImportConfig(merchantKeyword = "饿了么", mapToCategoryName = "餐饮"),
            ImportConfig(merchantKeyword = "肯德基", mapToCategoryName = "餐饮"),
            ImportConfig(merchantKeyword = "麦当劳", mapToCategoryName = "餐饮"),
            ImportConfig(merchantKeyword = "星巴克", mapToCategoryName = "餐饮"),
            ImportConfig(merchantKeyword = "瑞幸", mapToCategoryName = "餐饮"),
            ImportConfig(merchantKeyword = "滴滴", mapToCategoryName = "交通"),
            ImportConfig(merchantKeyword = "高德", mapToCategoryName = "交通"),
            ImportConfig(merchantKeyword = "地铁", mapToCategoryName = "交通"),
            ImportConfig(merchantKeyword = "公交", mapToCategoryName = "交通"),
            ImportConfig(merchantKeyword = "淘宝", mapToCategoryName = "购物"),
            ImportConfig(merchantKeyword = "京东", mapToCategoryName = "购物"),
            ImportConfig(merchantKeyword = "拼多多", mapToCategoryName = "购物"),
            ImportConfig(merchantKeyword = "天猫", mapToCategoryName = "购物"),
            ImportConfig(merchantKeyword = "唯品会", mapToCategoryName = "购物"),
            ImportConfig(merchantKeyword = "网易严选", mapToCategoryName = "购物"),
            ImportConfig(merchantKeyword = "电影", mapToCategoryName = "娱乐"),
            ImportConfig(merchantKeyword = "游戏", mapToCategoryName = "娱乐"),
            ImportConfig(merchantKeyword = "腾讯", mapToCategoryName = "娱乐"),
            ImportConfig(merchantKeyword = "爱奇艺", mapToCategoryName = "娱乐"),
            ImportConfig(merchantKeyword = "物业", mapToCategoryName = "居住"),
            ImportConfig(merchantKeyword = "电费", mapToCategoryName = "居住"),
            ImportConfig(merchantKeyword = "水费", mapToCategoryName = "居住"),
            ImportConfig(merchantKeyword = "燃气", mapToCategoryName = "居住"),
            ImportConfig(merchantKeyword = "医院", mapToCategoryName = "医疗"),
            ImportConfig(merchantKeyword = "药房", mapToCategoryName = "医疗"),
            ImportConfig(merchantKeyword = "药店", mapToCategoryName = "医疗"),
            ImportConfig(merchantKeyword = "话费", mapToCategoryName = "通讯"),
            ImportConfig(merchantKeyword = "中国移动", mapToCategoryName = "通讯"),
            ImportConfig(merchantKeyword = "中国联通", mapToCategoryName = "通讯"),
            ImportConfig(merchantKeyword = "中国电信", mapToCategoryName = "通讯")
        )
    }
}
