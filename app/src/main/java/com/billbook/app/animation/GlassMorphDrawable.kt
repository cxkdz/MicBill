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
        // 深色半透明背景
        setColor(Color.parseColor("#E61A1A2E"))
        // 微光描边
        setStroke(1, ColorUtils.setAlphaComponent(Color.WHITE, (255 * 0.12f).toInt()))
        cornerRadius = 100f * 3
    }
}
