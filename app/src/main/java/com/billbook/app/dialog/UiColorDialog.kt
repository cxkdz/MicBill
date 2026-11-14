package com.billbook.app.dialog

import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.billbook.app.R
import com.billbook.app.util.AnimeDialogHelper
import com.billbook.app.util.AutoContrast
import com.billbook.app.util.ThemeManager
import com.billbook.app.util.ThemePalettes

/**
 * 自定义 UI 配色入口：复用早先写好但一直无引用的 dialog_color_picker.xml。
 *
 * 只让用户选两个基础色（容器底色 + 强调色），其余文字 / 选中态 / 收支色
 * 由 ThemePalettes.derive() 与预设共用同一套 AutoContrast 算法派生，
 * 因此不会出现「自己配的颜色把文字压得看不清」。
 *
 * 覆盖层独立于预设 key：开着时覆盖配色，关掉后自动回到当前预设。
 */
class UiColorDialog : DialogFragment() {

    private var onApplied: (() -> Unit)? = null
    private lateinit var themeManager: ThemeManager

    private var groundColor = 0
    private var accentColor = 0
    private var editingGround = true
    private var syncing = false

    private lateinit var tabGround: TextView
    private lateinit var tabAccent: TextView
    private lateinit var colorPreview: View
    private lateinit var etHex: EditText
    private lateinit var seekR: SeekBar
    private lateinit var seekG: SeekBar
    private lateinit var seekB: SeekBar
    private lateinit var tvR: TextView
    private lateinit var tvG: TextView
    private lateinit var tvB: TextView

    fun setOnAppliedListener(listener: () -> Unit) {
        onApplied = listener
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        themeManager = ThemeManager(requireContext())
        val view = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_color_picker, null)
        setup(view)
        return AlertDialog.Builder(
            requireContext(), android.R.style.Theme_DeviceDefault_Dialog_NoActionBar
        ).setView(view).create()
    }

    override fun onStart() {
        super.onStart()
        (dialog as? AlertDialog)?.let { AnimeDialogHelper.applyAnimeStyle(it) }
    }

    private fun setup(view: View) {
        val dp = resources.displayMetrics.density
        val accent = themeManager.getEffectivePalette().planeAccent

        // 这份布局原本面向浅色卡片，这里放进深色对话框，文字统一染浅色
        tintForDarkDialog(view, dp, accent)

        val base = ThemePalettes.findByKey(themeManager.getCurrentPaletteKey())
            ?: ThemePalettes.DEFAULT
        val saved = themeManager.getCustomUiColors()
        groundColor = saved?.first ?: base.dioramaGround
        accentColor = saved?.second ?: base.dioramaAccent

        val root = view as LinearLayout
        colorPreview = view.findViewById(R.id.colorPreview)
        etHex = view.findViewById(R.id.etColorHex)
        seekR = view.findViewById(R.id.seekBarRed)
        seekG = view.findViewById(R.id.seekBarGreen)
        seekB = view.findViewById(R.id.seekBarBlue)
        tvR = view.findViewById(R.id.tvRedValue)
        tvG = view.findViewById(R.id.tvGreenValue)
        tvB = view.findViewById(R.id.tvBlueValue)

        // 顶部插入两个编辑目标：本身也是当前色值的色块预览
        val tabRow = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.topMargin = (14 * dp).toInt()
            layoutParams = lp
        }
        tabGround = makeTab("容器底色", dp, true)
        tabAccent = makeTab("强调色", dp, false)
        tabRow.addView(tabGround)
        tabRow.addView(tabAccent)
        root.addView(tabRow, 1)

        // 18 格预设色（复用 ThemeManager.PRESET_COLORS，6 列 × 3 行）
        val grid = view.findViewById<GridLayout>(R.id.gridColors)
        val size = (34 * dp).toInt()
        ThemeManager.PRESET_COLORS.forEach { hex ->
            val c = Color.parseColor(hex)
            val swatch = View(requireContext()).apply {
                layoutParams = GridLayout.LayoutParams().apply {
                    width = size
                    height = size
                    setMargins((3 * dp).toInt(), (3 * dp).toInt(), (3 * dp).toInt(), (3 * dp).toInt())
                }
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(c)
                    setStroke((1 * dp).toInt().coerceAtLeast(1), 0x30FFFFFF)
                }
                setOnClickListener { applyColor(c) }
            }
            grid.addView(swatch)
        }

        val seekListener = object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!syncing) applyColor(Color.rgb(seekR.progress, seekG.progress, seekB.progress))
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        }
        seekR.setOnSeekBarChangeListener(seekListener)
        seekG.setOnSeekBarChangeListener(seekListener)
        seekB.setOnSeekBarChangeListener(seekListener)

        etHex.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (syncing) return
                val parsed = runCatching { Color.parseColor(s?.toString().orEmpty()) }.getOrNull()
                if (parsed != null) applyColor(parsed)
            }
        })

        // 底部按钮：确定 / 恢复预设 / 取消
        val btnCancel = view.findViewById<View>(R.id.btnCancel)
        val btnConfirm = view.findViewById<View>(R.id.btnConfirm)
        val buttonRow = btnCancel.parent as LinearLayout
        val btnReset = TextView(requireContext()).apply {
            text = "恢复预设"
            setTextColor(0xB0FFFFFF.toInt())
            textSize = 13f
            gravity = android.view.Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams((84 * dp).toInt(), (44 * dp).toInt())
            background = GradientDrawable().apply {
                setColor(0x10FFFFFF)
                cornerRadius = 10 * dp
                setStroke((1 * dp).toInt().coerceAtLeast(1), 0x20FFFFFF)
            }
            setOnClickListener {
                themeManager.clearCustomUiColors()
                onApplied?.invoke()
                dismiss()
            }
        }
        buttonRow.addView(btnReset, 0)

        btnCancel.setOnClickListener { dismiss() }
        btnConfirm.setOnClickListener {
            themeManager.setCustomUiColors(groundColor, accentColor)
            onApplied?.invoke()
            dismiss()
        }

        syncFromColor()
    }

    private fun makeTab(text: String, dp: Float, isGround: Boolean): TextView =
        TextView(requireContext()).apply {
            this.text = text
            textSize = 13f
            gravity = android.view.Gravity.CENTER
            val lp = LinearLayout.LayoutParams(0, (40 * dp).toInt(), 1f)
            lp.setMargins((4 * dp).toInt(), 0, (4 * dp).toInt(), 0)
            layoutParams = lp
            setOnClickListener {
                editingGround = isGround
                syncFromColor()
            }
        }

    private fun applyColor(c: Int) {
        if (editingGround) groundColor = c else accentColor = c
        syncFromColor()
    }

    private val currentColor: Int
        get() = if (editingGround) groundColor else accentColor

    private fun syncFromColor() {
        syncing = true
        val c = currentColor
        val r = Color.red(c)
        val g = Color.green(c)
        val b = Color.blue(c)
        seekR.progress = r
        seekG.progress = g
        seekB.progress = b
        tvR.text = r.toString()
        tvG.text = g.toString()
        tvB.text = b.toString()
        etHex.setText("#%02X%02X%02X".format(r, g, b))
        etHex.setSelection(etHex.text.length)
        colorPreview.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(c)
            setStroke(2, 0x60FFFFFF)
        }
        refreshTab(tabGround, groundColor, editingGround)
        refreshTab(tabAccent, accentColor, !editingGround)
        syncing = false
    }

    private fun refreshTab(tab: TextView, color: Int, selected: Boolean) {
        val dp = resources.displayMetrics.density
        tab.background = GradientDrawable().apply {
            setColor(color)
            cornerRadius = 10 * dp
            setStroke(if (selected) (2 * dp).toInt() else (1 * dp).toInt().coerceAtLeast(1),
                if (selected) 0xE0FFFFFF.toInt() else 0x30FFFFFF)
        }
        tab.setTextColor(AutoContrast.textColorFor(color))
    }

    /** 深色对话框下把所有文字显式染浅，避免 values/ 下的深色 text_primary 看不清 */
    private fun tintForDarkDialog(root: View, dp: Float, accent: Int) {
        fun walk(v: View) {
            when (v) {
                is EditText -> {
                    v.setTextColor(0xF0FFFFFF.toInt())
                    v.setHintTextColor(0x60FFFFFF.toInt())
                    v.background = GradientDrawable().apply {
                        setColor(0x18FFFFFF)
                        cornerRadius = 8 * dp
                        setStroke((1 * dp).toInt().coerceAtLeast(1), 0x30FFFFFF)
                    }
                }
                is TextView -> v.setTextColor(0xE0FFFFFF.toInt())
                is SeekBar -> {
                    v.thumb?.setTint(accent)
                    v.progressDrawable?.setTint(accent)
                }
            }
            if (v is ViewGroup) for (i in 0 until v.childCount) walk(v.getChildAt(i))
        }
        // 色值预览是 View，不参与文字染色；从子标题开始即可
        walk(root)
    }

    companion object {
        const val TAG = "UiColorDialog"
    }
}
