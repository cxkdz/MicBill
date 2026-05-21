package com.billbook.app.animation

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import androidx.core.graphics.ColorUtils

/**
 * 可动画圆角的毛玻璃背景 Drawable
 * 用于展开动画中圆角从 100dp → 0dp 的平滑过渡
 */
class GlassMorphDrawable : GradientDrawable() {

    init {
        shape = RECTANGLE
        setColor(ColorUtils.setAlphaComponent(Color.WHITE, (255 * 0.92f).toInt()))
        setStroke(2, ColorUtils.setAlphaComponent(Color.WHITE, (255 * 0.3f).toInt()))
        cornerRadius = 100f * 3 // 初始圆角，足够大以形成药丸形
    }
}
