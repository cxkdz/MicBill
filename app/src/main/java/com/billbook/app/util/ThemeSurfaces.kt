package com.billbook.app.util

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import com.billbook.app.R

/**
 * 把历史遗留的「固定深蓝紫 / 固定蓝紫渐变」界面资源按当前预设主题重新着色。
 *
 * 背景：这些界面（记账浮层、编辑页、详情页、日期选择器、主题编辑器、标准对话框）
 * 的颜色全部写死在 XML / 代码里，与 ThemePalette 完全脱钩，切换主题后主页变了、
 * 二级页面仍是同一块深蓝紫，形成割裂。
 *
 * 用法：在 setContentView / inflate / onViewCreated 之后调用一次 apply(root)；
 * 运行时动态切换的背景改用 chip() / selectedDay() 等工厂方法，避免被重新覆盖回固定色。
 */
object ThemeSurfaces {

    private const val LEGACY_SURFACE = 0xE61A1A2E.toInt()

    // 明确使用固定色资源的控件，作为 ConstantState 匹配失败时的兜底
    private val BUTTON_IDS = setOf("btnSave", "btnEdit", "btnPickImage", "btnClearImage")
    private val SURFACE_IDS = setOf("datePickerCard")
    private val CHIP_SELECTED_IDS = setOf("chipExpense", "chipWechat", "btnConfirm")

    private fun palette(context: Context) = ThemeManager(context).getEffectivePalette()

    private fun dp(context: Context, value: Float) = value * context.resources.displayMetrics.density

    /**
     * 深色容器底色：把主题的 ground 压深 55% 得到「深色变体」。
     * 保留该主题色相（紫烟→深紫、深海→深蓝、樱织→深玫），同时足够暗，
     * 让内部大量白色半透明卡片、白色文字不需改动就继续可读。
     */
    fun surface(context: Context, radiusDp: Float = 0f): GradientDrawable {
        val p = palette(context)
        val deep = ColorUtils.blendARGB(p.dioramaGround, Color.BLACK, 0.55f)
        return GradientDrawable().apply {
            cornerRadius = dp(context, radiusDp)
            setColor(ColorUtils.setAlphaComponent(deep, (255 * 0.96f).toInt()))
            setStroke(
                dp(context, 1f).toInt().coerceAtLeast(1),
                ColorUtils.setAlphaComponent(p.planeAccent, (255 * 0.22f).toInt())
            )
        }
    }

    private fun buttonColors(context: Context): Pair<Int, Int> {
        val accent = palette(context).planeAccent
        return ColorUtils.blendARGB(accent, Color.WHITE, 0.18f) to
            ColorUtils.blendARGB(accent, Color.BLACK, 0.26f)
    }

    /** 主按钮：主题强调色渐变（替代写死的 #8BA7E8→#5A7BC8 蓝紫渐变） */
    fun button(context: Context): GradientDrawable {
        val (start, end) = buttonColors(context)
        return GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(start, end)
        ).apply {
            cornerRadius = dp(context, 24f)
            setStroke(dp(context, 1f).toInt().coerceAtLeast(1), 0x40FFFFFF)
        }
    }

    /**
     * 按钮文字色：按按钮渐变起始色自动取黑/白。
     * 布局里按钮文字原本写死 #FFFFFF，而深底主题会把强调色提亮到接近白，
     * 不修正就会出现「白字压浅底」看不清。
     */
    fun buttonTextColor(context: Context): Int =
        AutoContrast.textColorFor(buttonColors(context).first)

    private fun tintButtonText(view: View, color: Int) {
        if (view is TextView) view.setTextColor(color)
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) tintButtonText(view.getChildAt(i), color)
        }
    }

    /**
     * 标签 / 支付方式的选中态：主题强调色淡染 + 强调色描边。
     * 未选中态保持中性白色半透明，避免整屏都是主题色。
     */
    fun chip(context: Context, selected: Boolean): GradientDrawable {
        val accent = palette(context).planeAccent
        return GradientDrawable().apply {
            cornerRadius = dp(context, 16f)
            if (selected) {
                setColor(ColorUtils.setAlphaComponent(accent, 0x3A))
                setStroke(dp(context, 1f).toInt().coerceAtLeast(1),
                    ColorUtils.setAlphaComponent(accent, 0xAA))
            } else {
                setColor(0x10FFFFFF)
                setStroke(dp(context, 1f).toInt().coerceAtLeast(1), 0x15FFFFFF)
            }
        }
    }

    /** 日期选中圆：主题强调色实心圆 + 外圈柔光（替代写死的 #6B8DD6） */
    fun selectedDay(context: Context): Drawable {
        val accent = palette(context).planeAccent
        val dp = dp(context, 2f)
        return android.graphics.drawable.LayerDrawable(
            arrayOf(
                GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(ColorUtils.setAlphaComponent(accent, 0x24))
                },
                GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(ColorUtils.setAlphaComponent(accent, 0xDD))
                }
            )
        ).apply {
            setLayerInset(1, dp.toInt(), dp.toInt(), dp.toInt(), dp.toInt())
        }
    }

    /** 强调文字色（跟随主题且保证可读），用于标准对话框的按钮文字 */
    fun accentText(context: Context): Int = palette(context).planeAccent

    private fun stateOf(context: Context, resId: Int): Drawable.ConstantState? =
        ContextCompat.getDrawable(context, resId)?.constantState

    private fun idName(view: View): String = try {
        if (view.id == View.NO_ID) "" else view.resources.getResourceEntryName(view.id)
    } catch (_: Exception) {
        ""
    }

    /**
     * 遍历 View 树，把仍引用固定色资源的 background 换成本主题派生的 Drawable。
     * 只在界面初始化时调用一次；动态切换的选态请直接调用 chip() / selectedDay()。
     */
    fun apply(root: View) {
        val context = root.context
        val buttonState = stateOf(context, R.drawable.bg_anime_button)
        val dialogState = stateOf(context, R.drawable.bg_anime_dialog)
        val chipSelectedState = stateOf(context, R.drawable.bg_anime_pay_chip_selected)
        val daySelectedState = stateOf(context, R.drawable.bg_anime_date_selected)

        fun walk(view: View) {
            val bg = view.background
            val state = bg?.constantState
            val name = idName(view)
            // XML 里直接写 android:background="#E61A1A2E" 得到的是独立 ColorDrawable，
            // 不与资源缓存共享 ConstantState，必须按颜色值识别。
            val legacySurface = (bg as? ColorDrawable)?.color == LEGACY_SURFACE
            when {
                // 整页 / 对话框的写死深蓝紫底色
                legacySurface -> view.background = surface(context)
                state == dialogState || name in SURFACE_IDS -> view.background = surface(context, 20f)
                // 蓝紫渐变按钮：连文字色一起按新底色重算
                state == buttonState || name in BUTTON_IDS -> {
                    view.background = button(context)
                    tintButtonText(view, buttonTextColor(context))
                }
                // 选中态标签
                state == chipSelectedState || name in CHIP_SELECTED_IDS -> view.background = chip(context, true)
                state == daySelectedState -> view.background = selectedDay(context)
            }
            if (view is ViewGroup) {
                for (i in 0 until view.childCount) walk(view.getChildAt(i))
            }
        }
        walk(root)
    }
}
