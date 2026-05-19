package com.billbook.app.util

import android.app.Activity
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.widget.TextView

object BackgroundApplier {

    fun apply(activity: Activity) {
        // The 3D palette system handles all visuals via MainActivity.
        // No-op to prevent fragment backgrounds from covering the plane.
    }

    fun applyToFragment(view: View, themeManager: ThemeManager) {
        view.setBackgroundColor(Color.TRANSPARENT)
        val palette = themeManager.getEffectivePalette()
        val composite = computeComposite(palette, themeManager)
        val shadowColor = AutoContrast.textShadowColor(composite)
        applyShadowsRecursively(view, shadowColor)
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

    private fun applyShadowsRecursively(view: View, shadowColor: Int) {
        if (view is TextView) {
            view.setShadowLayer(3f, 0f, 1f, shadowColor)
        } else if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                applyShadowsRecursively(view.getChildAt(i), shadowColor)
            }
        }
    }
}
