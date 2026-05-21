package com.billbook.app.util

import android.graphics.Color
import kotlin.math.max
import kotlin.math.min

object AutoContrast {

    fun isDark(color: Int): Boolean {
        return relativeLuminance(color) < 0.179
    }

    fun textColorFor(background: Int): Int {
        return if (isDark(background)) Color.WHITE else Color.parseColor("#1A1A2E")
    }

    fun secondaryTextColorFor(background: Int): Int {
        return if (isDark(background)) Color.parseColor("#C8FFFFFF") else Color.parseColor("#C01A1A2E")
    }

    fun hintTextColorFor(background: Int): Int {
        return if (isDark(background)) Color.parseColor("#A0FFFFFF") else Color.parseColor("#991A1A2E")
    }

    fun relativeLuminance(color: Int): Double {
        fun linearize(v: Int): Double {
            val s = v / 255.0
            return if (s <= 0.03928) s / 12.92 else Math.pow((s + 0.055) / 1.055, 2.4)
        }
        return 0.2126 * linearize(Color.red(color)) +
                0.7152 * linearize(Color.green(color)) +
                0.0722 * linearize(Color.blue(color))
    }

    fun contrastRatio(fg: Int, bg: Int): Double {
        val l1 = relativeLuminance(fg)
        val l2 = relativeLuminance(bg)
        val lighter = maxOf(l1, l2)
        val darker = minOf(l1, l2)
        return (lighter + 0.05) / (darker + 0.05)
    }

    fun textShadowColor(bg: Int): Int {
        return if (isDark(bg)) Color.parseColor("#C0000000") else Color.parseColor("#C0FFFFFF")
    }

    fun textStrokeColor(bg: Int): Int {
        return if (isDark(bg)) Color.parseColor("#60000000") else Color.parseColor("#60FFFFFF")
    }

    fun glowColor(bg: Int): Int {
        return if (isDark(bg)) Color.parseColor("#40FFFFFF") else Color.parseColor("#40000000")
    }

    fun overlayTint(bg: Int): Int {
        return if (isDark(bg)) Color.parseColor("#18FFFFFF") else Color.parseColor("#20000000")
    }

    // ==================== Card Color (RGB Blend) ====================

    /**
     * 基于 HSL 去饱和 + RGB 混合计算卡片背景色。
     * 先去饱和消除色相（防绿色等），再向黑/白偏移 12%。
     * 深色背景：变亮 12%，浅色背景：变深 12%。
     */
    fun cardColorFor(pageBg: Int): Int {
        val hsl = rgbToHsl(pageBg)
        hsl[1] = (hsl[1] * 0.12f).coerceAtMost(0.10f)
        val gray = hslToRgb(hsl)
        return if (isDark(gray)) {
            blendColor(gray, Color.WHITE, 0.12f)
        } else {
            blendColor(gray, Color.BLACK, 0.12f)
        }
    }

    /**
     * 卡片子区域背景（比卡片再偏移 5%）
     */
    fun cardInnerColorFor(cardBg: Int): Int {
        return if (isDark(cardBg)) {
            blendColor(cardBg, Color.WHITE, 0.05f)
        } else {
            blendColor(cardBg, Color.BLACK, 0.05f)
        }
    }

    /**
     * 卡片分割线颜色
     */
    fun dividerColorFor(cardBg: Int): Int {
        return if (isDark(cardBg)) {
            Color.argb(40, 255, 255, 255)
        } else {
            Color.argb(40, 0, 0, 0)
        }
    }

    /**
     * RGB 混合：将 color1 和 color2 按 ratio 混合。
     * ratio=0 返回 color1，ratio=1 返回 color2。
     */
    fun blendColor(color1: Int, color2: Int, ratio: Float): Int {
        val r = (Color.red(color1) * (1 - ratio) + Color.red(color2) * ratio).toInt().coerceIn(0, 255)
        val g = (Color.green(color1) * (1 - ratio) + Color.green(color2) * ratio).toInt().coerceIn(0, 255)
        val b = (Color.blue(color1) * (1 - ratio) + Color.blue(color2) * ratio).toInt().coerceIn(0, 255)
        val a = (Color.alpha(color1) * (1 - ratio) + Color.alpha(color2) * ratio).toInt().coerceIn(0, 255)
        return Color.argb(a, r, g, b)
    }

    // ==================== Saturated Color Detection ====================

    /**
     * 判断颜色是否为高饱和度或语义色，应保持不变。
     * 纯灰色系返回 false，应自动切换黑白。
     *
     * 阈值 0.20：
     * - text_primary(0.15) text_secondary(0.153) text_hint(0.148) → 全部 < 0.20 → 自适应
     * - expense_red(0.38) income_green(0.38) orange_primary(0.70) → 远高于 0.20 → 保持
     */
    fun isSaturatedColor(color: Int): Boolean {
        val hsl = rgbToHsl(color)
        return hsl[1] >= 0.20f
    }

    /**
     * 根据卡片背景计算合适的主文字色
     */
    fun autoTextColorFor(cardBg: Int): Int {
        return if (isDark(cardBg)) Color.parseColor("#E0FFFFFF") else Color.parseColor("#E63D4A5C")
    }

    /**
     * 根据卡片背景计算合适的次要文字色
     */
    fun autoSecondaryTextFor(cardBg: Int): Int {
        return if (isDark(cardBg)) Color.parseColor("#C8FFFFFF") else Color.parseColor("#D03D4A5C")
    }

    /**
     * 根据卡片背景计算合适的提示文字色
     */
    fun autoHintTextFor(cardBg: Int): Int {
        return if (isDark(cardBg)) Color.parseColor("#A0FFFFFF") else Color.parseColor("#B03D4A5C")
    }

    // ==================== Mode-Aware Text Color ====================
    // mode: 0=自动, 1=强制深色, 2=强制浅色
    // 强制模式使用不透明色，确保视觉上明确区分

    fun textColorFor(cardBg: Int, mode: Int): Int {
        if (mode == 1) return Color.parseColor("#FF3D4A5C")
        if (mode == 2) return Color.parseColor("#FFFFFFFF")
        return autoTextColorFor(cardBg)
    }

    fun secondaryTextFor(cardBg: Int, mode: Int): Int {
        if (mode == 1) return Color.parseColor("#FF8895A7")
        if (mode == 2) return Color.parseColor("#E0FFFFFF")
        return autoSecondaryTextFor(cardBg)
    }

    fun hintTextFor(cardBg: Int, mode: Int): Int {
        if (mode == 1) return Color.parseColor("#FFA8B4C4")
        if (mode == 2) return Color.parseColor("#C0FFFFFF")
        return autoHintTextFor(cardBg)
    }

    // ==================== HSL Conversion ====================

    fun rgbToHsl(color: Int): FloatArray {
        val r = Color.red(color) / 255f
        val g = Color.green(color) / 255f
        val b = Color.blue(color) / 255f

        val max = max(r, max(g, b))
        val min = min(r, min(g, b))
        val l = (max + min) / 2f

        if (max == min) {
            return floatArrayOf(0f, 0f, l)
        }

        val d = max - min
        val s = if (l > 0.5f) d / (2f - max - min) else d / (max + min)

        val h = when (max) {
            r -> ((g - b) / d + (if (g < b) 6f else 0f)) / 6f
            g -> ((b - r) / d + 2f) / 6f
            else -> ((r - g) / d + 4f) / 6f
        }

        return floatArrayOf(h, s, l)
    }

    fun hslToRgb(hsl: FloatArray, alpha: Int = 255): Int {
        val h = hsl[0]
        val s = hsl[1]
        val l = hsl[2]

        if (s == 0f) {
            val v = (l * 255).toInt().coerceIn(0, 255)
            return Color.argb(alpha, v, v, v)
        }

        val q = if (l < 0.5f) l * (1f + s) else l + s - l * s
        val p = 2f * l - q

        val r = hueToRgb(p, q, h + 1f / 3f)
        val g = hueToRgb(p, q, h)
        val b = hueToRgb(p, q, h - 1f / 3f)

        return Color.argb(
            alpha,
            (r * 255).toInt().coerceIn(0, 255),
            (g * 255).toInt().coerceIn(0, 255),
            (b * 255).toInt().coerceIn(0, 255)
        )
    }

    private fun hueToRgb(p: Float, q: Float, t: Float): Float {
        var tt = t
        if (tt < 0f) tt += 1f
        if (tt > 1f) tt -= 1f
        return when {
            tt < 1f / 6f -> p + (q - p) * 6f * tt
            tt < 1f / 2f -> q
            tt < 2f / 3f -> p + (q - p) * (2f / 3f - tt) * 6f
            else -> p
        }
    }
}
