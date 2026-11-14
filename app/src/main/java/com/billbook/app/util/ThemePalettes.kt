package com.billbook.app.util

import android.graphics.Color
import com.billbook.app.R
import com.billbook.app.model.ThemePalette

/**
 * 6 套预设主题：每套 = 一张「前景」角色图 + 一层「背景」纯色渐变 + 配套 UI 配色。
 *
 * 渐变两端色取自对应角色图的明暗主色；dioramaGround 取渐变中间色，供 AutoContrast
 * 估算文字对比度。旧的 10 套程序化场景预设（forest/snow/...）及场景绘制链已删除。
 * 以后加一套预设只需在 PRESETS 里加一行。
 *
 * 所有「派生色」（文字、强调、segment、收支）统一由 derive() 计算：
 * 预设与自定义配色共用同一套算法，自定义时不会出现「底色变了但文字色没跟上」。
 */
object ThemePalettes {

    private fun color(hex: String) = Color.parseColor(hex)

    /**
     * 用一组基础色（ground / accent）重算 palette 的全部派生字段。
     *
     * @param base 基础 palette（提供图片、玻璃参数、预设渐变等静态字段）
     * @param groundColor 容器/页面底色，AutoContrast 以它判定明暗
     * @param accentColor 主题强调色，用于按钮、选中态与语义色染色
     * @param overrideGradient 是否用 ground/accent 重新生成 Layer1 渐变。
     *        预设路径为 false（保留逐套精确调过的渐变）；自定义配色为 true。
     */
    fun derive(
        base: ThemePalette,
        groundColor: Int = base.dioramaGround,
        accentColor: Int = base.dioramaAccent,
        overrideGradient: Boolean = false
    ): ThemePalette {
        val onDark = AutoContrast.isDark(groundColor)

        // 主题强调色：保留色相，但在当前底色上拉到可读（深底提亮 / 浅底压暗）
        val readableAccent = if (onDark) {
            AutoContrast.blendColor(accentColor, Color.WHITE, 0.32f)
        } else {
            AutoContrast.blendColor(accentColor, color("#171722"), 0.30f)
        }

        // 文字一律由 AutoContrast 按底色自动取，保证任何自定义色下都清晰
        val textPrimary = AutoContrast.textColorFor(groundColor)
        val textSecondary = AutoContrast.secondaryTextColorFor(groundColor)

        // 收支语义色保持稳定（红/绿 = 辨识度），只染 18% 主题色达到协调
        val expense = AutoContrast.blendColor(color("#E8615F"), accentColor, 0.18f)
        val income = AutoContrast.blendColor(color("#5FC89B"), accentColor, 0.18f)

        val skyTop = if (overrideGradient) {
            AutoContrast.blendColor(groundColor, Color.BLACK, 0.45f)
        } else base.dioramaSkyTop
        val skyBottom = if (overrideGradient) {
            AutoContrast.blendColor(
                AutoContrast.blendColor(groundColor, accentColor, 0.45f),
                Color.WHITE, 0.18f
            )
        } else base.dioramaSkyBottom

        return base.copy(
            dioramaSkyTop = skyTop,
            dioramaSkyBottom = skyBottom,
            dioramaGround = groundColor,
            dioramaAccent = accentColor,
            planeAccent = readableAccent,
            headerTextPrimary = textPrimary,
            headerTextSecondary = textSecondary,
            segmentBg = if (onDark) color("#18FFFFFF") else color("#12000000"),
            segmentActiveBg = if (onDark) color("#30FFFFFF") else color("#20000000"),
            segmentActiveText = readableAccent,
            segmentInactiveText = textSecondary,
            expenseColor = expense,
            incomeColor = income
        )
    }

    private fun preset(
        key: String,
        name: String,
        foregroundResId: Int,
        skyTop: String,
        skyBottom: String,
        ground: String,
        accent: String,
        planePrimary: String,
        glassAlpha: Int,
        borderAlpha: Int
    ): ThemePalette {
        // 只填「基础色」；派生字段全部交给 derive()，避免两套算法
        val base = ThemePalette(
            key = key,
            name = name,
            foregroundResId = foregroundResId,
            dioramaSkyTop = color(skyTop),
            dioramaSkyBottom = color(skyBottom),
            dioramaGround = color(ground),
            dioramaAccent = color(accent),
            dioramaHighlight = Color.WHITE,
            planePrimary = color(planePrimary),
            planeAccent = 0,
            planeShadowTint = color("#33000000"),
            planeAmbientLight = color("#20FFFFFF"),
            planeGlassAlpha = glassAlpha,
            planeBorderAlpha = borderAlpha,
            headerTextPrimary = 0,
            headerTextSecondary = 0,
            segmentBg = 0,
            segmentActiveBg = 0,
            segmentActiveText = 0,
            segmentInactiveText = 0,
            expenseColor = 0,
            incomeColor = 0
        )
        return derive(base)
    }

    val PRESETS = listOf(
        preset("moying", "墨影", R.drawable.fg_moying,
            "#0A0A0C", "#6E7070", "#3A3B3B", "#9A9B99", "#E8E8E8", 42, 40),
        preset("ziyan", "紫烟", R.drawable.fg_ziyan,
            "#8E6E8E", "#F2E9EF", "#C4AFC0", "#A97FA9", "#F7F0F6", 30, 20),
        preset("shenhai", "深海", R.drawable.fg_shenhai,
            "#101F38", "#5A6C82", "#35455C", "#6C9BD2", "#E3EDF7", 38, 34),
        preset("yingzhi", "樱织", R.drawable.fg_yingzhi,
            "#6E4E5B", "#E8CBD2", "#A98A96", "#D98AA4", "#FBEFF2", 32, 22),
        preset("nuanzhou", "暖昼", R.drawable.fg_nuanzhou,
            "#6E6872", "#D8CFC2", "#A29B99", "#C9A96B", "#F5F0E8", 32, 22),
        preset("xingye", "星夜", R.drawable.fg_xingye,
            "#1C2540", "#7A7890", "#4A4A66", "#8FA8E0", "#E6EAF6", 40, 36)
    )

    val DEFAULT_KEY = "moying"

    val DEFAULT: ThemePalette get() = PRESETS.first()

    fun findByKey(key: String): ThemePalette? = PRESETS.find { it.key == key }
}
