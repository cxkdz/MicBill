package com.billbook.app.dialog

import android.content.Context
import androidx.appcompat.app.AlertDialog
import com.billbook.app.util.AnimeDialogHelper
import com.billbook.app.util.ThemePalettes

/**
 * 「预设主题」独立选择器：只列出 6 套主题，不夹带背景/前景设置。
 * 选中后由调用方写入 ThemeManager 并刷新界面。
 */
object PresetPickerDialog {

    fun show(context: Context, onPresetSelected: (String) -> Unit) {
        val presets = ThemePalettes.PRESETS
        val items = presets.map { "${it.name}   ·   渐变背景" }
        lateinit var dialog: AlertDialog
        dialog = AnimeDialogHelper.showListDialog(
            context, "选择预设主题（${presets.size} 套）", items
        ) { index ->
            dialog.dismiss()
            onPresetSelected(presets[index].key)
        }
    }
}
