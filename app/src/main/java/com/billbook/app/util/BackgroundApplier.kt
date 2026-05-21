package com.billbook.app.util

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.TextView

object BackgroundApplier {

    fun apply(activity: Activity) {
        // The 3D palette system handles all visuals via MainActivity.
        // No-op to prevent fragment backgrounds from covering the plane.
    }

    /**
     * 为 Fragment 应用全局自适应主题：
     * 1. 背景透明（让 3D 平面可见）
     * 2. 文字阴影
     * 3. 文字颜色自适应背景亮度（低对比灰色系自动切换黑白，高饱和色保持不变）
     */
    fun applyToFragment(view: View, themeManager: ThemeManager) {
        view.setBackgroundColor(Color.TRANSPARENT)
        val palette = themeManager.getEffectivePalette()
        val composite = computeComposite(palette, themeManager)
        val shadowColor = AutoContrast.textShadowColor(composite)
        adaptRecursively(view, composite, shadowColor)
    }

    fun applyToExtraViews(activity: Activity, vararg views: View) {
        // No-op
    }

    private fun computeComposite(palette: com.billbook.app.model.ThemePalette, themeManager: ThemeManager): Int {
        val alpha = palette.planeGlassAlpha / 255f
        val isDark = themeManager.isDarkMode()
        val effectiveAlpha = if (isDark) (alpha * 1.5f).coerceAtMost(1f) else alpha
        val r = (Color.red(palette.planePrimary) * effectiveAlpha + Color.red(palette.dioramaGround) * (1 - effectiveAlpha)).toInt()
        val g = (Color.green(palette.planePrimary) * effectiveAlpha + Color.green(palette.dioramaGround) * (1 - effectiveAlpha)).toInt()
        val b = (Color.blue(palette.planePrimary) * effectiveAlpha + Color.blue(palette.dioramaGround) * (1 - effectiveAlpha)).toInt()
        return Color.rgb(r.coerceIn(0, 255), g.coerceIn(0, 255), b.coerceIn(0, 255))
    }

    /**
     * 递归遍历所有子 View，同时应用阴影和文字颜色自适应。
     * @param effectiveBg 当前层级的有效背景色（祖先中最近的有背景的 View 决定）
     */
    private fun adaptRecursively(view: View, effectiveBg: Int, shadowColor: Int) {
        // 计算当前 View 的有效背景色
        var currentBg = effectiveBg
        val bg = view.background
        if (bg != null) {
            currentBg = computeEffectiveBg(view, effectiveBg)
        }

        // 文字阴影
        if (view is TextView) {
            view.setShadowLayer(3f, 0f, 1f, shadowColor)
            adaptTextColor(view, currentBg)
        }

        // 递归子 View
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                adaptRecursively(view.getChildAt(i), currentBg, shadowColor)
            }
        }
    }

    /**
     * 计算 View 的有效背景色。
     * GradientDrawable → 直接取颜色；
     * 其他有背景的 View → 用 composite 推导卡片色；
     * 无背景 → 返回父级 effectiveBg。
     */
    private fun computeEffectiveBg(view: View, parentBg: Int): Int {
        val bg = view.background ?: return parentBg
        // GradientDrawable: 直接取填充色
        if (bg is GradientDrawable) {
            val c = bg.color
            if (c != null && c.defaultColor != Color.TRANSPARENT) {
                return c.defaultColor
            }
        }
        // LayerDrawable (bg_frost_card): 取第一层填充色
        if (bg is LayerDrawable && bg.numberOfLayers > 0) {
            val fill = bg.getDrawable(0)
            if (fill is GradientDrawable) {
                val c = fill.color
                if (c != null && c.defaultColor != Color.TRANSPARENT) {
                    return c.defaultColor
                }
            }
        }
        // 有背景但无法提取颜色 → 推导为卡片色
        return AutoContrast.cardColorFor(parentBg)
    }

    /**
     * 自适应文字颜色。
     * 高饱和色保持不变；灰色系根据背景亮度自动切换黑白。
     */
    private fun adaptTextColor(tv: TextView, bg: Int) {
        val current = tv.currentTextColor

        // 高饱和色 / 语义色 → 保持
        if (AutoContrast.isSaturatedColor(current)) return

        // 灰色系 → 根据背景亮度切换
        val dark = AutoContrast.isDark(bg)
        val lum = AutoContrast.relativeLuminance(current)
        val newColor = when {
            // 亮度 > 0.5 的浅灰文字（text_hint #A8B4C4 等）→ 主文字色
            lum > 0.5 -> {
                if (dark) Color.parseColor("#E0FFFFFF") else Color.parseColor("#E63D4A5C")
            }
            // 中间亮度灰色（text_primary #3D4A5C、text_secondary #8895A7 等）
            lum > 0.05 -> {
                if (dark) Color.parseColor("#C0FFFFFF") else Color.parseColor("#E63D4A5C")
            }
            // 接近纯黑的文字
            else -> {
                if (dark) Color.parseColor("#E0FFFFFF") else Color.parseColor("#E63D4A5C")
            }
        }
        tv.setTextColor(newColor)
    }
}
