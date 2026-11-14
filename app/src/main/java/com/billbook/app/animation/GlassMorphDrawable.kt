package com.billbook.app.animation

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import androidx.core.graphics.ColorUtils

/**
 * 可动画圆角的毛玻璃背景 Drawable
 * 深色二次元风格：深蓝渐变 + 微光描边
 */
class GlassMorphDrawable : GradientDrawable() {

    init {
        shape = RECTANGLE
        // 默认深色半透明背景（未套用主题前的兜底）
        setColor(Color.parseColor("#E61A1A2E"))
        // 微光描边
        setStroke(1, ColorUtils.setAlphaComponent(Color.WHITE, (255 * 0.12f).toInt()))
        cornerRadius = 100f * 3
    }

    /**
     * 按当前主题重设容器底色：把主题的 ground 色压深作为「深色变体」，
     * 既保留该主题的色相（紫烟→深紫、深海→深蓝、樱织→深玫），
     * 又足够暗，让内部白色半透明卡片与白色文字仍然可读。
     */
    fun applyTheme(groundColor: Int, accentColor: Int) {
        val deep = ColorUtils.blendARGB(groundColor, Color.BLACK, 0.55f)
        setColor(ColorUtils.setAlphaComponent(deep, (255 * 0.94f).toInt()))
        setStroke(1, ColorUtils.setAlphaComponent(accentColor, (255 * 0.22f).toInt()))
    }
}
