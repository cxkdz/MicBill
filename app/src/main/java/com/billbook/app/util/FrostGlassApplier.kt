package com.billbook.app.util

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import com.google.android.material.button.MaterialButton

/**
 * 通用磨砂玻璃卡片适配器。
 * 为所有使用 bg_glass_card 的卡片应用：
 * 1. 半透明磨砂背景（颜色基于页面背景偏移 15%，先去饱和）
 * 2. 文字阴影
 * 3. 文字颜色自动适配（深卡白字，浅卡黑字）
 *
 * 用于 Statistics、Settings、Query 等非预算页面。
 */
object FrostGlassApplier {

    /**
     * 为 Fragment 中所有 bg_glass_card 卡片应用磨砂玻璃主题。
     * 应在 BackgroundApplier.applyToFragment 之后调用。
     */
    fun applyToFragment(activity: Activity, rootView: View, textColorMode: Int = 0) {
        val pageBg = BudgetThemeApplier.getPageComposite(activity)
        val cardColor = frostCardColor(pageBg)
        val isDarkCard = AutoContrast.isDark(cardColor)
        val alpha = if (isDarkCard) 140 else 115
        val r = Color.red(cardColor)
        val g = Color.green(cardColor)
        val b = Color.blue(cardColor)
        val semiColor = Color.argb(alpha, r, g, b)

        val shadowColor = if (isDarkCard) Color.parseColor("#50000000") else Color.parseColor("#40FFFFFF")

        applyRecursively(rootView, semiColor, cardColor, shadowColor, textColorMode, false)
    }

    private fun frostCardColor(pageBg: Int): Int {
        val hsl = AutoContrast.rgbToHsl(pageBg)
        hsl[1] = (hsl[1] * 0.12f).coerceAtMost(0.10f)
        val gray = AutoContrast.hslToRgb(hsl)
        return if (AutoContrast.isDark(gray)) {
            AutoContrast.blendColor(gray, Color.WHITE, 0.15f)
        } else {
            AutoContrast.blendColor(gray, Color.BLACK, 0.15f)
        }
    }

    private fun applyRecursively(view: View, semiColor: Int, cardColor: Int, shadowColor: Int, textColorMode: Int, insideCard: Boolean) {
        val bg = view.background
        var isGlassCard = false
        if (bg is GradientDrawable) {
            val currentColor = bg.color?.defaultColor ?: 0
            // 只处理接近透明白色的 bg_glass_card（alpha < 40, 接近白色）
            if (Color.alpha(currentColor) < 40 && isNearWhite(currentColor)) {
                bg.setColor(semiColor)
                isGlassCard = true
            }
        }

        val inCard = insideCard || isGlassCard

        if (view is TextView && inCard) {
            // 文字阴影（BackgroundApplier 也会设，这里用卡片色决定阴影方向）
            view.setShadowLayer(2.5f, 0f, 1f, shadowColor)
            // 文字颜色：跳过按钮文字，高饱和色保持，灰色系根据原始亮度级别 + mode 切换
            if (!isButtonText(view) && !AutoContrast.isSaturatedColor(view.currentTextColor)) {
                val lum = AutoContrast.relativeLuminance(view.currentTextColor)
                val newColor = when {
                    lum > 0.5 -> AutoContrast.textColorFor(cardColor, textColorMode)
                    lum > 0.05 -> AutoContrast.secondaryTextFor(cardColor, textColorMode)
                    else -> AutoContrast.hintTextFor(cardColor, textColorMode)
                }
                view.setTextColor(newColor)
            }
        }

        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                applyRecursively(view.getChildAt(i), semiColor, cardColor, shadowColor, textColorMode, inCard)
            }
        }
    }

    private fun isButtonText(view: View): Boolean {
        if (view is Button || view is MaterialButton) return true
        try {
            val id = view.resources.getResourceEntryName(view.id)
            return id == "btnEdit" || id == "btnDelete"
        } catch (_: Exception) {}
        return false
    }

    private fun isNearWhite(color: Int): Boolean {
        val r = Color.red(color)
        val g = Color.green(color)
        val b = Color.blue(color)
        return r > 200 && g > 200 && b > 200
    }
}
