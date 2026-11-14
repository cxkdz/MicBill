package com.billbook.app.model

/**
 * 一套预设主题 = 一张「前景」角色图 + 一层「背景」纯色渐变 + 配套 UI 配色。
 * foregroundResId = 0 表示该预设不带前景图，回退到玻璃平面。
 */
data class ThemePalette(
    val key: String,
    val name: String,
    val foregroundResId: Int = 0,
    // Layer 1「背景」：渐变两端色 + 供 AutoContrast 估算明暗的中间色
    val dioramaSkyTop: Int,
    val dioramaSkyBottom: Int,
    val dioramaGround: Int,
    val dioramaAccent: Int,
    val dioramaHighlight: Int,
    // Layer 2「前景平面」玻璃质感
    val planePrimary: Int,
    val planeAccent: Int,
    val planeShadowTint: Int,
    val planeAmbientLight: Int,
    val planeGlassAlpha: Int,
    val planeBorderAlpha: Int,
    // Layer 3 文字与控件
    val headerTextPrimary: Int,
    val headerTextSecondary: Int,
    val segmentBg: Int,
    val segmentActiveBg: Int,
    val segmentActiveText: Int,
    val segmentInactiveText: Int,
    val expenseColor: Int,
    val incomeColor: Int
)
