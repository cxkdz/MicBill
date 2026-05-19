package com.billbook.app.util

import android.graphics.Color

object AutoContrast {

    fun isDark(color: Int): Boolean {
        return relativeLuminance(color) < 0.179
    }

    fun textColorFor(background: Int): Int {
        return if (isDark(background)) Color.WHITE else Color.parseColor("#1A1A2E")
    }

    fun secondaryTextColorFor(background: Int): Int {
        return if (isDark(background)) Color.parseColor("#B0FFFFFF") else Color.parseColor("#801A1A2E")
    }

    fun hintTextColorFor(background: Int): Int {
        return if (isDark(background)) Color.parseColor("#80FFFFFF") else Color.parseColor("#601A1A2E")
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
}
