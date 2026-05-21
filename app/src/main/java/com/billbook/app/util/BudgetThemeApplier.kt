package com.billbook.app.util

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.cardview.widget.CardView
import com.billbook.app.R
import com.google.android.material.button.MaterialButton

/**
 * 预算页面磨砂玻璃主题适配器。
 *
 * 卡片颜色规则：
 * - 浅色/杂色背景 → 卡片比背景深 10-20%，半透明
 * - 深色背景 → 卡片比背景亮 10-20%，半透明
 *
 * 文字颜色规则：
 * - 深色卡片 → 白色文字
 * - 浅色卡片 → 深色文字
 * - 高饱和/语义色保持不变
 * - 附加文字阴影增强可读性
 */
object BudgetThemeApplier {

    fun getPageComposite(activity: android.app.Activity): Int {
        val themeManager = ThemeManager(activity)
        val palette = themeManager.getEffectivePalette()
        val alpha = palette.planeGlassAlpha / 255f
        val isDark = themeManager.isDarkMode()
        val effectiveAlpha = if (isDark) (alpha * 1.5f).coerceAtMost(1f) else alpha

        val r = (Color.red(palette.planePrimary) * effectiveAlpha + Color.red(palette.dioramaGround) * (1 - effectiveAlpha)).toInt()
        val g = (Color.green(palette.planePrimary) * effectiveAlpha + Color.green(palette.dioramaGround) * (1 - effectiveAlpha)).toInt()
        val b = (Color.blue(palette.planePrimary) * effectiveAlpha + Color.blue(palette.dioramaGround) * (1 - effectiveAlpha)).toInt()
        return Color.rgb(r.coerceIn(0, 255), g.coerceIn(0, 255), b.coerceIn(0, 255))
    }

    /**
     * 磨砂玻璃卡片色：先去饱和（消除背景色相），再向黑/白偏移 15%。
     * 浅色背景 → 变深 15%，深色背景 → 变亮 15%。
     * 结果始终是中性灰色系，不会出现绿色等非背景色。
     */
    private fun frostCardColor(pageBg: Int): Int {
        val hsl = AutoContrast.rgbToHsl(pageBg)
        hsl[1] = (hsl[1] * 0.12f).coerceAtMost(0.10f) // 饱和度降至 12%，上限 10%
        val gray = AutoContrast.hslToRgb(hsl)
        return if (AutoContrast.isDark(gray)) {
            AutoContrast.blendColor(gray, Color.WHITE, 0.15f)
        } else {
            AutoContrast.blendColor(gray, Color.BLACK, 0.15f)
        }
    }

    fun apply(rootView: View, pageBg: Int, textColorMode: Int = 0) {
        val cardColor = frostCardColor(pageBg)
        applyCardBackground(rootView, cardColor)
        applyTextColors(rootView, cardColor, textColorMode)
    }

    fun applyCategoryCard(itemView: View, pageBg: Int, textColorMode: Int = 0) {
        val cardColor = frostCardColor(pageBg)
        applyCardBackground(itemView, cardColor)
        applyTextColors(itemView, cardColor, textColorMode)
    }

    // ==================== Card Background ====================

    /**
     * 磨砂玻璃半透明背景。
     * 深色卡片用较高不透明度（让浅色文字可见），浅色卡片用较低不透明度（透出背景）。
     */
    private fun applyCardBackground(view: View, cardColor: Int) {
        val r = Color.red(cardColor)
        val g = Color.green(cardColor)
        val b = Color.blue(cardColor)
        val isDarkCard = AutoContrast.isDark(cardColor)

        // 深色卡片 55% 不透明，浅色卡片 45% 不透明
        val alpha = if (isDarkCard) 140 else 115

        if (view is CardView) {
            view.setCardBackgroundColor(Color.argb(alpha, r, g, b))
            return
        }

        val bg = view.background
        when (bg) {
            is GradientDrawable -> bg.setColor(Color.argb(alpha, r, g, b))
            is LayerDrawable -> {
                val fill = bg.getDrawable(0)
                if (fill is GradientDrawable) fill.setColor(Color.argb(alpha, r, g, b))
            }
        }
    }

    // ==================== Text Colors ====================

    private fun applyTextColors(view: View, cardColor: Int, textColorMode: Int) {
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                applyTextColors(view.getChildAt(i), cardColor, textColorMode)
            }
        }

        if (view !is TextView) return
        val tv = view as TextView

        // 跳过按钮文字
        if (isButtonText(tv)) return

        val isDarkCard = AutoContrast.isDark(cardColor)

        // 文字阴影：深色卡片用暗阴影，浅色卡片用亮阴影
        val shadowColor = if (isDarkCard) Color.parseColor("#50000000") else Color.parseColor("#40FFFFFF")
        tv.setShadowLayer(2.5f, 0f, 1f, shadowColor)

        // 已知 ID → 直接覆盖（不检查饱和度，优先级最高）
        when (tv.id) {
            R.id.tvBudgetAmount, R.id.tvCategoryName, R.id.tvCategoryBudget -> {
                tv.setTextColor(AutoContrast.textColorFor(cardColor, textColorMode))
                return
            }
            R.id.tvPercent, R.id.tvSpentAmount, R.id.tvRemainingAmount,
            R.id.tvDailyBudget, R.id.tvAllocated, R.id.tvAvailable,
            R.id.tvCategoryStatus -> {
                tv.setTextColor(AutoContrast.secondaryTextFor(cardColor, textColorMode))
                return
            }
            R.id.tvNoBudget, R.id.tvNoCategory -> {
                tv.setTextColor(AutoContrast.hintTextFor(cardColor, textColorMode))
                return
            }
        }

        // 未知 ID → 高饱和色保持不变
        if (AutoContrast.isSaturatedColor(tv.currentTextColor)) return
    }

    private fun isButtonText(view: View): Boolean {
        if (view is Button || view is MaterialButton) return true
        try {
            val id = view.resources.getResourceEntryName(view.id)
            return id == "btnEdit" || id == "btnDelete"
        } catch (_: Exception) {}
        return false
    }
}
