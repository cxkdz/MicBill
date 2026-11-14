package com.billbook.app.fragment

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDelegate
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import com.billbook.app.MainActivity
import com.billbook.app.R
import com.billbook.app.databinding.FragmentSettingsBinding
import com.billbook.app.db.CategoryDao
import com.billbook.app.model.Category
import com.billbook.app.util.CategoryIconMapper
import com.billbook.app.util.ThemeManager
import com.billbook.app.util.AppExecutors
import com.billbook.app.util.BitmapUtils
import com.billbook.app.util.WeChatBillParser
import com.billbook.app.util.AlipayBillParser
import com.billbook.app.db.BillDao
import com.billbook.app.dialog.ImportRuleDialog
import com.billbook.app.dialog.PresetPickerDialog
import com.billbook.app.dialog.ThemeEditorDialog
import com.billbook.app.dialog.UiColorDialog
import com.billbook.app.util.BackupManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    private lateinit var themeManager: ThemeManager

    /** 当前预设主题的强调色（已由 AutoContrast 保证可读），替代历史写死的 #6B8DD6 */
    private fun accentColor(): Int = themeManager.getEffectivePalette().planeAccent

    private val imagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri -> onImageSelected(uri) }
        }
    }

    private val wechatBillPickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri -> importWechatBill(uri) }
        }
    }

    private val alipayBillPickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri -> importAlipayBill(uri) }
        }
    }

    private val exportBackupLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri -> executeExport(uri) }
        }
    }

    private val restoreBackupLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri -> inspectRestore(uri) }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        themeManager = ThemeManager(requireContext())
        val mode = themeManager.getTextColorMode()
        com.billbook.app.util.BackgroundApplier.applyToFragment(view, themeManager, mode)
        com.billbook.app.util.FrostGlassApplier.applyToFragment(requireActivity(), view, mode)

        setupPresetPicker()
        setupDarkModeSwitch()
        setupSnowSwitch()
        setupBillViewMode()
        setupTiltAngle()
        setupCustomImageBg()

        setupCategoryManage()
        setupBudgetAndQuery()
        setupImportBills()
        setupBackupAndRules()
    }

    /**
     * 账单浏览方式。只写偏好，不在这里刷新账单页：
     * 当前显示的是设置页（Fragment 容器），此时刷新会把账单列表误设为可见盖住设置页。
     * 用户点底部「账单」时 showBillPage() 会重读偏好并切换容器。
     */
    private fun setupBillViewMode() {
        fun render(vertical: Boolean) {
            binding.btnModeHorizontal.setBackgroundResource(
                if (vertical) R.drawable.selector_glass_button else R.drawable.bg_segment_active
            )
            binding.btnModeVertical.setBackgroundResource(
                if (vertical) R.drawable.bg_segment_active else R.drawable.selector_glass_button
            )
            binding.tvBillModeStatus.text = if (vertical) {
                "竖向列表 · 按日期连续下滑"
            } else {
                "横向翻页 · 左右滑动切换日期"
            }
        }

        render(themeManager.isVerticalBillMode())
        binding.btnModeHorizontal.setOnClickListener {
            themeManager.setVerticalBillMode(false)
            render(false)
        }
        binding.btnModeVertical.setOnClickListener {
            themeManager.setVerticalBillMode(true)
            render(true)
        }
    }

    private fun setupBudgetAndQuery() {
        binding.btnBudget.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, BudgetFragment())
                .addToBackStack(null)
                .commit()
        }

        binding.btnQuery.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, QueryFragment())
                .addToBackStack(null)
                .commit()
        }
    }

    private fun setupImportBills() {
        binding.btnImportWechat.setOnClickListener { openWechatBillPicker() }
        binding.btnImportAlipay.setOnClickListener { openAlipayBillPicker() }
    }

    // ==================== 数据管理：备份 / 恢复 / 归类规则 ====================

    private fun setupBackupAndRules() {
        binding.btnExportBackup.setOnClickListener { openExportPicker() }
        binding.btnRestoreBackup.setOnClickListener { openRestorePicker() }
        binding.btnEditRules.setOnClickListener { ImportRuleDialog.show(requireContext()) }
    }

    private fun openExportPicker() {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/json"
            putExtra(Intent.EXTRA_TITLE, BackupManager.defaultFileName())
        }
        exportBackupLauncher.launch(intent)
    }

    private fun openRestorePicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("application/json", "text/plain"))
        }
        restoreBackupLauncher.launch(intent)
    }

    private fun executeExport(uri: Uri) {
        val appContext = requireContext().applicationContext
        AppExecutors.io {
            val result = try {
                BackupManager.export(appContext, uri)
            } catch (_: Exception) {
                null
            }
            AppExecutors.main {
                if (!isAdded) return@main
                val ctx = context ?: return@main
                val msg = if (result == null) {
                    "导出失败，请重试"
                } else {
                    "已导出 ${result.billCount} 条账单（共 ${result.totalRows} 条记录）"
                }
                Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun inspectRestore(uri: Uri) {
        val appContext = requireContext().applicationContext
        AppExecutors.io {
            val meta = try {
                BackupManager.inspect(appContext, uri)
            } catch (_: Exception) {
                null
            }
            AppExecutors.main {
                if (!isAdded) return@main
                val ctx = context ?: return@main
                if (meta == null) {
                    Toast.makeText(ctx, "不是有效的 BillBook 备份文件", Toast.LENGTH_LONG).show()
                    return@main
                }
                val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).format(Date(meta.exportedAt))
                val bills = meta.counts[com.billbook.app.db.DatabaseHelper.TABLE_BILL] ?: 0
                val message = "备份时间：$stamp\n账单：$bills 条\n\n注意：恢复会覆盖当前全部账单、分类、预算和归类规则，建议先导出当前备份。"
                com.billbook.app.util.AnimeDialogHelper.show(
                    context = ctx,
                    title = "恢复备份",
                    message = message,
                    positiveText = "确认恢复",
                    negativeText = "取消",
                    onPositive = { executeRestore(uri) }
                )
            }
        }
    }

    private fun executeRestore(uri: Uri) {
        val appContext = requireContext().applicationContext
        AppExecutors.io {
            val count = try {
                BackupManager.restore(appContext, uri)
            } catch (_: Exception) {
                -1
            }
            AppExecutors.main {
                if (!isAdded) return@main
                val ctx = context ?: return@main
                if (count < 0) {
                    Toast.makeText(ctx, "恢复失败，数据已回滚未改动", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(ctx, "已恢复 $count 条记录", Toast.LENGTH_LONG).show()
                    activity?.recreate()
                }
            }
        }
    }

    private fun openWechatBillPicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "application/vnd.ms-excel",
                "text/csv",
                "text/comma-separated-values"
            ))
        }
        wechatBillPickerLauncher.launch(intent)
    }

    private fun openAlipayBillPicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "application/vnd.ms-excel"
            ))
        }
        alipayBillPickerLauncher.launch(intent)
    }

    private fun importWechatBill(uri: Uri) {
        val appContext = requireContext().applicationContext
        AppExecutors.io {
            // POI / CSV 解析是重操作，放到后台；applicationContext 避免持有 Activity
            val previewResult = try {
                WeChatBillParser(appContext).preview(uri)
            } catch (_: Exception) {
                null
            }
            AppExecutors.main {
                if (!isAdded) return@main
                val ctx = context ?: return@main
                if (previewResult == null || previewResult.totalCount == 0) {
                    Toast.makeText(ctx, "未找到有效账单数据", Toast.LENGTH_SHORT).show()
                    return@main
                }

                val message = buildString {
                    appendLine("找到 ${previewResult.totalCount} 条记录")
                    appendLine("有效记录：${previewResult.successCount} 条")
                    if (previewResult.skipCount > 0) appendLine("将跳过：${previewResult.skipCount} 条")
                    if (previewResult.errorMessages.isNotEmpty()) appendLine("解析错误：${previewResult.errorMessages.size} 条")
                    appendLine("\n是否导入？")
                }

                com.billbook.app.util.AnimeDialogHelper.show(
                    context = ctx,
                    title = "导入微信账单",
                    message = message,
                    positiveText = "导入",
                    negativeText = "取消",
                    onPositive = { executeWechatImport(uri) }
                )
            }
        }
    }

    private fun executeWechatImport(uri: Uri) {
        val appContext = requireContext().applicationContext
        AppExecutors.io {
            val result = try {
                WeChatBillParser(appContext).parse(uri, skipDuplicate = true, filterTypes = true)
            } catch (_: Exception) {
                null
            }
            // 一次事务批量写入，替代逐条 insert 的隐式事务
            val insertCount = if (result == null || result.bills.isEmpty()) {
                0
            } else {
                try {
                    BillDao(appContext).insertBatch(result.bills)
                } catch (_: Exception) {
                    -1   // insertOrThrow 抛异常时事务已整体回滚
                }
            }
            AppExecutors.main {
                if (!isAdded) return@main
                val ctx = context ?: return@main
                if (insertCount < 0) {
                    Toast.makeText(ctx, "导入失败，数据已回滚，请重试", Toast.LENGTH_LONG).show()
                    return@main
                }
                if (insertCount == 0) {
                    Toast.makeText(ctx, "没有可导入的新记录", Toast.LENGTH_SHORT).show()
                    return@main
                }
                com.billbook.app.util.AnimeDialogHelper.show(
                    context = ctx,
                    title = "导入结果",
                    message = "导入完成！成功导入：$insertCount 条",
                    positiveText = "确定",
                    onPositive = { activity?.recreate() }
                )
            }
        }
    }

    private fun importAlipayBill(uri: Uri) {
        val appContext = requireContext().applicationContext
        AppExecutors.io {
            val previewResult = try {
                AlipayBillParser(appContext).preview(uri)
            } catch (_: Exception) {
                null
            }
            AppExecutors.main {
                if (!isAdded) return@main
                val ctx = context ?: return@main
                if (previewResult == null || previewResult.totalCount == 0) {
                    Toast.makeText(ctx, "未找到有效账单数据", Toast.LENGTH_SHORT).show()
                    return@main
                }

                val message = buildString {
                    appendLine("找到 ${previewResult.totalCount} 条记录")
                    appendLine("有效记录：${previewResult.successCount} 条")
                    if (previewResult.skipCount > 0) appendLine("将跳过：${previewResult.skipCount} 条")
                    appendLine("\n是否导入？")
                }

                com.billbook.app.util.AnimeDialogHelper.show(
                    context = ctx,
                    title = "导入支付宝账单",
                    message = message,
                    positiveText = "导入",
                    negativeText = "取消",
                    onPositive = { executeAlipayImport(uri) }
                )
            }
        }
    }

    private fun executeAlipayImport(uri: Uri) {
        val appContext = requireContext().applicationContext
        AppExecutors.io {
            val result = try {
                AlipayBillParser(appContext).parse(uri, skipDuplicate = true, filterTypes = true)
            } catch (_: Exception) {
                null
            }
            val insertCount = if (result == null || result.bills.isEmpty()) {
                0
            } else {
                try {
                    BillDao(appContext).insertBatch(result.bills)
                } catch (_: Exception) {
                    -1   // insertOrThrow 抛异常时事务已整体回滚
                }
            }
            AppExecutors.main {
                if (!isAdded) return@main
                val ctx = context ?: return@main
                if (insertCount < 0) {
                    Toast.makeText(ctx, "导入失败，数据已回滚，请重试", Toast.LENGTH_LONG).show()
                    return@main
                }
                if (insertCount == 0) {
                    Toast.makeText(ctx, "没有可导入的新记录", Toast.LENGTH_SHORT).show()
                    return@main
                }
                com.billbook.app.util.AnimeDialogHelper.show(
                    context = ctx,
                    title = "导入结果",
                    message = "导入完成！成功导入：$insertCount 条",
                    positiveText = "确定",
                    onPositive = { activity?.recreate() }
                )
            }
        }
    }

    /** 预设主题独立入口：只切换整套主题，不夹带背景/前景设置 */
    private fun setupPresetPicker() {
        binding.tvCurrentPalette.text = themeManager.getEffectivePalette().name
        updateBackgroundStatus()
        updateUiColorStatus()

        binding.btnThemeEditor.setOnClickListener {
            PresetPickerDialog.show(requireContext()) { key ->
                themeManager.setPalette(key)
                // 选预设时清掉两层自定义图，让该预设的渐变背景与角色前景都生效
                themeManager.clearCustomDioramaImage()
                themeManager.clearCustomBackground()
                // 预设与自定义配色是两种模式：切到预设就退回预设配色。
                // 只关开关不删色值，用户回到「调整」还能接着用上次配的颜色。
                themeManager.clearCustomUiColors()
                binding.tvCurrentPalette.text = themeManager.getEffectivePalette().name
                updateUiColorStatus()
                requireActivity().recreate()
            }
        }

        binding.btnCustomBackground.setOnClickListener { openBackgroundEditor() }

        binding.btnUiColor.setOnClickListener {
            val dialog = UiColorDialog()
            dialog.setOnAppliedListener {
                updateUiColorStatus()
                requireActivity().recreate()
            }
            dialog.show(parentFragmentManager, UiColorDialog.TAG)
        }
    }

    private fun updateUiColorStatus() {
        binding.tvUiColorStatus.text = if (themeManager.isCustomUiColorEnabled()) {
            "自定义配色"
        } else {
            "跟随预设"
        }
    }

    private fun updateBackgroundStatus() {
        binding.tvBackgroundStatus.text = if (themeManager.hasCustomDioramaImage()) {
            "当前：自定义图片"
        } else {
            "当前：预设渐变"
        }
    }

    private fun openBackgroundEditor() {
        val dialog = ThemeEditorDialog()
        dialog.setStyle(
            DialogFragment.STYLE_NO_TITLE,
            androidx.appcompat.R.style.Theme_AppCompat_Light_Dialog
        )
        dialog.setOnApplyListener {
            updateBackgroundStatus()
            requireActivity().recreate()
        }
        dialog.show(parentFragmentManager, ThemeEditorDialog.TAG)
    }

    private fun setupDarkModeSwitch() {
        val isDark = themeManager.isDarkMode()
        binding.switchDarkMode.isChecked = isDark
        binding.tvDarkModeStatus.text = if (isDark) "浅色" else "深色"

        binding.switchDarkMode.setOnCheckedChangeListener { _, isChecked ->
            themeManager.setDarkMode(isChecked)
            // 文字色统一由 AutoContrast 按实际背景亮度决定，不再随夜间模式强制白字
            if (isChecked) {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            } else {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            }
            requireActivity().recreate()
        }
    }

    private fun setupSnowSwitch() {
        val isEnabled = themeManager.isSnowEnabled()
        binding.switchSnow.isChecked = isEnabled
        binding.tvSnowStatus.text = if (isEnabled) "开启" else "关闭"

        binding.switchSnow.setOnCheckedChangeListener { _, isChecked ->
            themeManager.setSnowEnabled(isChecked)
            binding.tvSnowStatus.text = if (isChecked) "开启" else "关闭"
            requireActivity().recreate()
        }
    }

    private fun setupTiltAngle() {
        val currentAngle = themeManager.getTiltAngle()
        binding.tvTiltValue.text = "${currentAngle}°"

        binding.layoutTiltAngle.setOnClickListener { showTiltAngleDialog() }
    }

    private fun showTiltAngleDialog() {
        val dp = resources.displayMetrics.density
        val container = android.widget.LinearLayout(requireContext()).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding((32 * dp).toInt(), (20 * dp).toInt(), (32 * dp).toInt(), (8 * dp).toInt())
        }

        // 角度数值显示
        val tvAngle = android.widget.TextView(requireContext()).apply {
            text = "${themeManager.getTiltAngle()}°"
            textSize = 28f
            setTypeface(null, android.graphics.Typeface.BOLD)
            gravity = android.view.Gravity.CENTER
            setTextColor(0xE0FFFFFF.toInt())
        }
        container.addView(tvAngle)

        // SeekBar
        val seekBar = SeekBar(requireContext()).apply {
            max = 45
            progress = themeManager.getTiltAngle()
            thumb?.setTint(accentColor())
            progressDrawable?.setTint(accentColor())
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                    tvAngle.text = "${progress}°"
                    themeManager.setTiltAngle(progress)
                    val mainActivity = activity
                    if (mainActivity is MainActivity) {
                        mainActivity.updateTiltPreview(progress.toFloat())
                    }
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
        }
        val lp = android.widget.LinearLayout.LayoutParams(
            android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
        )
        lp.topMargin = (16 * dp).toInt()
        container.addView(seekBar, lp)

        // 提示文字
        container.addView(android.widget.TextView(requireContext()).apply {
            text = "调整 3D 背景的倾斜透视效果"
            textSize = 12f
            setTextColor(0x50FFFFFF.toInt())
            gravity = android.view.Gravity.CENTER
            setPadding(0, (8 * dp).toInt(), 0, 0)
        })

        val dialog = AlertDialog.Builder(requireContext(), android.R.style.Theme_DeviceDefault_Dialog_NoActionBar)
            .setView(container)
            .setPositiveButton("完成", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.apply {
                setTextColor(accentColor())
                textSize = 15f
            }
        }

        dialog.show()
        com.billbook.app.util.AnimeDialogHelper.applyAnimeStyle(dialog)
    }

    private fun setupCustomImageBg() {
        updateCustomBgStatus()
        updateOpacityDisplay()

        binding.btnImportImage.setOnClickListener { openImagePicker() }

        binding.btnClearImage.setOnClickListener {
            themeManager.clearCustomBackground()
            updateCustomBgStatus()
            binding.layoutBgPreview.visibility = View.GONE
            Toast.makeText(requireContext(), "已恢复为预设角色图", Toast.LENGTH_SHORT).show()
            requireActivity().recreate()
        }

        binding.layoutOpacity.setOnClickListener { showOpacityDialog() }
    }

    private fun showOpacityDialog() {
        val dp = resources.displayMetrics.density
        val container = android.widget.LinearLayout(requireContext()).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding((32 * dp).toInt(), (20 * dp).toInt(), (32 * dp).toInt(), (8 * dp).toInt())
        }

        // 透明度数值显示
        val tvValue = android.widget.TextView(requireContext()).apply {
            text = "${themeManager.getBgOpacity()}%"
            textSize = 28f
            setTypeface(null, android.graphics.Typeface.BOLD)
            gravity = android.view.Gravity.CENTER
            setTextColor(0xE0FFFFFF.toInt())
        }
        container.addView(tvValue)

        // SeekBar
        val seekBar = SeekBar(requireContext()).apply {
            max = 100
            progress = themeManager.getBgOpacity()
            thumb?.setTint(accentColor())
            progressDrawable?.setTint(accentColor())
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                    tvValue.text = "$progress%"
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {
                    themeManager.setBgOpacity(sb?.progress ?: 30)
                }
            })
        }
        val lp = android.widget.LinearLayout.LayoutParams(
            android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
        )
        lp.topMargin = (16 * dp).toInt()
        container.addView(seekBar, lp)

        // 提示文字
        container.addView(android.widget.TextView(requireContext()).apply {
            text = "越高越淡，文字越清晰\n建议 20% ~ 40%"
            textSize = 12f
            setTextColor(0x50FFFFFF.toInt())
            gravity = android.view.Gravity.CENTER
            setPadding(0, (8 * dp).toInt(), 0, 0)
        })

        val dialog = AlertDialog.Builder(requireContext(), android.R.style.Theme_DeviceDefault_Dialog_NoActionBar)
            .setView(container)
            .setPositiveButton("完成") { _, _ ->
                requireActivity().recreate()
            }
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.apply {
                setTextColor(accentColor())
                textSize = 15f
            }
        }

        dialog.show()
        com.billbook.app.util.AnimeDialogHelper.applyAnimeStyle(dialog)
    }

    private fun openImagePicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "image/*"
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
        }
        imagePickerLauncher.launch(intent)
    }

    private fun onImageSelected(uri: Uri) {
        try {
            val inputStream = requireContext().contentResolver.openInputStream(uri)
            inputStream?.close()

            themeManager.setCustomBackground(uri)
            updateCustomBgStatus()

            com.billbook.app.util.AnimeDialogHelper.show(
                context = requireContext(),
                title = "图片已导入",
                message = "是否现在调整前景透明度？\n\n数值越高，前景图片越明显；数值越低越淡、文字越清晰。\n建议设为20%-40%。",
                positiveText = "调整",
                negativeText = "使用默认",
                neutralText = "稍后设置",
                onPositive = { showOpacityDialog() },
                onNegative = {
                    themeManager.setBgOpacity(30)
                    updateOpacityDisplay()
                }
            )
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "图片读取失败：${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateCustomBgStatus() {
        if (themeManager.isCustomBackgroundEnabled()) {
            binding.tvCustomBgStatus.text = "当前：自定义前景图片"
            binding.tvCustomBgStatus.setTextColor(resources.getColor(android.R.color.holo_green_dark, null))
            binding.btnClearImage.visibility = View.VISIBLE
            binding.layoutOpacity.visibility = View.VISIBLE

            try {
                val uri = themeManager.getCustomBackgroundUri()
                if (uri != null) {
                    val metrics = resources.displayMetrics
                    val bitmap = BitmapUtils.decodeSampledUri(
                        requireContext(), uri, metrics.widthPixels, metrics.heightPixels
                    )
                    if (bitmap != null) {
                        binding.layoutBgPreview.visibility = View.VISIBLE
                        binding.layoutBgPreview.post {
                            val width = binding.layoutBgPreview.width
                            val height = (width * 9) / 16
                            val layoutParams = binding.layoutBgPreview.layoutParams
                            layoutParams.height = height
                            binding.layoutBgPreview.layoutParams = layoutParams
                        }
                        binding.ivBgPreview.setImageBitmap(bitmap)
                    }
                }
            } catch (_: Exception) {}
        } else {
            binding.tvCustomBgStatus.text = "当前：预设角色图"
            binding.tvCustomBgStatus.setTextColor(
                com.billbook.app.util.AutoContrast.autoSecondaryTextFor(
                    com.billbook.app.util.BudgetThemeApplier.getPageComposite(requireActivity())
                )
            )
            binding.btnClearImage.visibility = View.GONE
            binding.layoutOpacity.visibility = View.GONE
            binding.layoutBgPreview.visibility = View.GONE
        }
    }

    private fun updateOpacityDisplay() {
        binding.tvOpacityValue.text = "${themeManager.getBgOpacity()}%"
    }

    private fun setupCategoryManage() {
        binding.btnManageCategory.setOnClickListener { showCategoryDialog() }
        binding.btnAddCategory.setOnClickListener { showAddCategoryDialog() }
    }

    private fun showCategoryDialog() {
        val categoryDao = CategoryDao(requireContext())
        val allCategories = categoryDao.getAll()
        val expenseCategories = allCategories.filter { it.type == 0 }
        val incomeCategories = allCategories.filter { it.type == 1 }

        val dp = resources.displayMetrics.density
        val container = android.widget.LinearLayout(requireContext()).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding((24 * dp).toInt(), (20 * dp).toInt(), (24 * dp).toInt(), (12 * dp).toInt())
        }

        // 标题
        container.addView(android.widget.TextView(requireContext()).apply {
            text = "分类管理"
            setTextColor(0xE0FFFFFF.toInt())
            textSize = 18f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, (12 * dp).toInt())
        })

        // 列表
        val scrollView = android.widget.ScrollView(requireContext())
        val listLayout = android.widget.LinearLayout(requireContext()).apply {
            orientation = android.widget.LinearLayout.VERTICAL
        }

        // 添加分组标题和分类项的函数
        fun addCategoryGroup(title: String, categories: List<com.billbook.app.model.Category>) {
            // 分组标题
            listLayout.addView(android.widget.TextView(requireContext()).apply {
                text = title
                setTextColor(0x80FFFFFF.toInt())
                textSize = 13f
                setPadding((12 * dp).toInt(), (16 * dp).toInt(), (12 * dp).toInt(), (8 * dp).toInt())
            })

            categories.forEachIndexed { index, category ->
                val itemView = android.widget.LinearLayout(requireContext()).apply {
                    orientation = android.widget.LinearLayout.HORIZONTAL
                    gravity = android.view.Gravity.CENTER_VERTICAL
                    setPadding((12 * dp).toInt(), (12 * dp).toInt(), (12 * dp).toInt(), (12 * dp).toInt())
                }

                // 图标 - 优先使用自定义的iconResId
                val iconResId = if (category.iconResId != 0) category.iconResId else CategoryIconMapper.getIconResId(category.name, category.type)
                if (iconResId != 0) {
                    val iconView = android.widget.ImageView(requireContext()).apply {
                        layoutParams = android.widget.LinearLayout.LayoutParams((36 * dp).toInt(), (36 * dp).toInt())
                        setImageResource(iconResId)
                        scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE
                        setPadding((4 * dp).toInt(), (4 * dp).toInt(), (4 * dp).toInt(), (4 * dp).toInt())
                    }
                    itemView.addView(iconView)
                } else {
                    val iconView = android.widget.TextView(requireContext()).apply {
                        layoutParams = android.widget.LinearLayout.LayoutParams((36 * dp).toInt(), (36 * dp).toInt())
                        text = ""
                        textSize = 20f
                        gravity = android.view.Gravity.CENTER
                    }
                    itemView.addView(iconView)
                }

                // 名称
                val nameView = android.widget.TextView(requireContext()).apply {
                    layoutParams = android.widget.LinearLayout.LayoutParams(0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                        marginStart = (12 * dp).toInt()
                    }
                    text = category.name
                    setTextColor(0xB0FFFFFF.toInt())
                    textSize = 15f
                }
                itemView.addView(nameView)

                itemView.setOnClickListener {
                    showEditCategoryDialog(category)
                }

                // 按压反馈
                itemView.isClickable = true
                itemView.isFocusable = true
                itemView.setBackgroundResource(android.R.drawable.list_selector_background)

                listLayout.addView(itemView)

                // 分割线
                if (index < categories.size - 1) {
                    listLayout.addView(View(requireContext()).apply {
                        layoutParams = android.widget.LinearLayout.LayoutParams(
                            android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                            (1 * dp).toInt()
                        )
                        setBackgroundColor(0x15FFFFFF)
                    })
                }
            }
        }

        // 支出分类
        addCategoryGroup("支出分类", expenseCategories)

        // 分组间距
        listLayout.addView(View(requireContext()).apply {
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                (8 * dp).toInt()
            )
        })

        // 收入分类
        addCategoryGroup("收入分类", incomeCategories)

        scrollView.addView(listLayout)
        container.addView(scrollView, android.widget.LinearLayout.LayoutParams(
            android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
            0, 1f
        ))

        val dialog = AlertDialog.Builder(requireContext(), android.R.style.Theme_DeviceDefault_Dialog_NoActionBar)
            .setView(container)
            .setCancelable(true)
            .create()

        dialog.show()
        com.billbook.app.util.AnimeDialogHelper.applyAnimeStyle(dialog)
    }

    private fun showAddCategoryDialog() {
        val types = listOf("支出分类", "收入分类")
        com.billbook.app.util.AnimeDialogHelper.showListDialog(
            context = requireContext(),
            title = "添加分类类型",
            items = types,
            onItemSelected = { which ->
                val type = if (which == 0) com.billbook.app.model.Bill.TYPE_EXPENSE else com.billbook.app.model.Bill.TYPE_INCOME
                showEditCategoryDialog(null, type)
            }
        )
    }

    private fun showEditCategoryDialog(category: Category?, defaultType: Int = 0) {
        val dp = resources.displayMetrics.density
        var dialog: AlertDialog? = null
        val container = android.widget.LinearLayout(requireContext()).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding((24 * dp).toInt(), (20 * dp).toInt(), (24 * dp).toInt(), (12 * dp).toInt())
        }

        val isEdit = category != null
        val categoryDao = CategoryDao(requireContext())

        // 标题
        container.addView(android.widget.TextView(requireContext()).apply {
            text = if (isEdit) "编辑分类" else "添加分类"
            setTextColor(0xE0FFFFFF.toInt())
            textSize = 18f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, (16 * dp).toInt())
        })

        // 分类名称输入框
        val nameInput = android.widget.EditText(requireContext()).apply {
            setText(category?.name ?: "")
            hint = "请输入分类名称"
            setTextColor(0xE0FFFFFF.toInt())
            setHintTextColor(0x50FFFFFF.toInt())
            textSize = 16f
            setPadding((16 * dp).toInt(), (12 * dp).toInt(), (16 * dp).toInt(), (12 * dp).toInt())
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(0x15FFFFFF)
                cornerRadius = 12 * dp
                setStroke((1 * dp).toInt(), 0x20FFFFFF)
            }
            setSingleLine(true)
        }
        container.addView(nameInput, android.widget.LinearLayout.LayoutParams(
            android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            bottomMargin = (16 * dp).toInt()
        })

        // 选中的图标预览
        val currentIconResId = category?.let {
            if (it.iconResId != 0) it.iconResId else CategoryIconMapper.getIconResId(it.name, it.type)
        } ?: 0
        var selectedIconResId = currentIconResId

        val previewContainer = android.widget.LinearLayout(requireContext()).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding((12 * dp).toInt(), (12 * dp).toInt(), (12 * dp).toInt(), (12 * dp).toInt())
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(0x10FFFFFF)
                cornerRadius = 12 * dp
            }
        }

        val previewIcon = android.widget.ImageView(requireContext()).apply {
            layoutParams = android.widget.LinearLayout.LayoutParams((48 * dp).toInt(), (48 * dp).toInt())
            if (selectedIconResId != 0) {
                setImageResource(selectedIconResId)
            } else {
                setImageResource(R.drawable.ic_misc_more)
                alpha = 0.5f
            }
            scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE
        }
        previewContainer.addView(previewIcon)

        val previewText = android.widget.TextView(requireContext()).apply {
            text = if (selectedIconResId != 0) "已选择图标" else "请选择图标"
            setTextColor(0x80FFFFFF.toInt())
            textSize = 14f
            setPadding((12 * dp).toInt(), 0, 0, 0)
        }
        previewContainer.addView(previewText)

        container.addView(previewContainer, android.widget.LinearLayout.LayoutParams(
            android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            bottomMargin = (12 * dp).toInt()
        })

        // 存储所有图标View的引用及其是否为"已使用"状态
        val allIconViews = mutableListOf<Pair<android.widget.ImageView, Boolean>>()

        // 更新预览的函数
        fun updatePreview(iconResId: Int) {
            selectedIconResId = iconResId
            previewIcon.setImageResource(iconResId)
            previewIcon.alpha = 1.0f
            previewText.text = "已选择图标"

            // 更新所有图标的高亮状态
            allIconViews.forEach { (iconView, isUsed) ->
                val viewIconResId = iconView.tag as? Int ?: 0
                if (viewIconResId == selectedIconResId) {
                    iconView.background = android.graphics.drawable.GradientDrawable().apply {
                        setColor(0x30FFFFFF)
                        cornerRadius = 12 * dp
                        setStroke((2 * dp).toInt(), 0x60FFFFFF)
                    }
                    iconView.alpha = 1.0f
                } else {
                    iconView.background = android.graphics.drawable.GradientDrawable().apply {
                        setColor(if (isUsed) 0x16FFFFFF else 0x10FFFFFF)
                        cornerRadius = 12 * dp
                        setStroke((1 * dp).toInt(), if (isUsed) 0x20FFFFFF else 0x15FFFFFF)
                    }
                    iconView.alpha = if (isUsed) 0.92f else 1.0f
                }
            }
        }

        // 图标网格
        val scrollView = android.widget.ScrollView(requireContext()).apply {
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                0, 1f
            )
        }
        val gridLayout = android.widget.LinearLayout(requireContext()).apply {
            orientation = android.widget.LinearLayout.VERTICAL
        }

        // 获取已使用的图标
        val allCategories = categoryDao.getAll()
        val usedIconResIds = allCategories.mapNotNull { cat ->
            val resId = if (cat.iconResId != 0) cat.iconResId else CategoryIconMapper.getIconResId(cat.name, cat.type)
            if (resId != 0) resId else null
        }.toSet()

        // 候选池统一使用新的三渲二分类图标：当前类型在前，另一类型作为补充。
        // 旧的 ic_misc_* 是 emoji 风格 vector，与新画风不统一，不再作为候选项。
        val isIncome = category?.type == 1 || defaultType == 1
        val typeSpecificIcons = if (isIncome) {
            CategoryIconMapper.getAllIncomeIconResIds()
        } else {
            CategoryIconMapper.getAllExpenseIconResIds()
        }
        val otherTypeIcons = if (isIncome) {
            CategoryIconMapper.getAllExpenseIconResIds()
        } else {
            CategoryIconMapper.getAllIncomeIconResIds()
        }

        // 合并图标并排序：未使用的在前，已使用的在后
        val allIcons = (typeSpecificIcons + otherTypeIcons).distinct()
        val unusedIcons = allIcons.filter { it !in usedIconResIds }
        val usedIcons = allIcons.filter { it in usedIconResIds }

        // 每行显示 4 个图标
        val iconsPerRow = 4
        var currentRow = android.widget.LinearLayout(requireContext()).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER
        }

        // 添加分组标题
        fun addGroupTitle(title: String) {
            gridLayout.addView(android.widget.TextView(requireContext()).apply {
                text = title
                setTextColor(0x80FFFFFF.toInt())
                textSize = 13f
                setPadding((4 * dp).toInt(), (12 * dp).toInt(), (4 * dp).toInt(), (8 * dp).toInt())
                layoutParams = android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                )
            })
        }

        // 创建图标项
        fun createIconItem(iconResId: Int, isUsed: Boolean) {
            val iconContainer = android.widget.FrameLayout(requireContext()).apply {
                layoutParams = android.widget.LinearLayout.LayoutParams(
                    0,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                ).apply {
                    setMargins((4 * dp).toInt(), (4 * dp).toInt(), (4 * dp).toInt(), (4 * dp).toInt())
                }
            }

            val iconView = android.widget.ImageView(requireContext()).apply {
                layoutParams = android.widget.FrameLayout.LayoutParams(
                    android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                    (56 * dp).toInt()
                )
                setImageResource(iconResId)
                tag = iconResId
                // 新 PNG 自带留白，用 FIT_CENTER + 更小 padding 让图标在格子里更饱满
                scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
                setPadding((8 * dp).toInt(), (8 * dp).toInt(), (8 * dp).toInt(), (8 * dp).toInt())
                background = android.graphics.drawable.GradientDrawable().apply {
                    setColor(if (isUsed) 0x16FFFFFF else 0x10FFFFFF)
                    cornerRadius = 12 * dp
                    setStroke((1 * dp).toInt(), if (isUsed) 0x20FFFFFF else 0x15FFFFFF)
                }
                if (isUsed) alpha = 0.92f

                // 高亮当前选中的图标
                if (iconResId == selectedIconResId) {
                    background = android.graphics.drawable.GradientDrawable().apply {
                        setColor(0x30FFFFFF)
                        cornerRadius = 12 * dp
                        setStroke((2 * dp).toInt(), 0x60FFFFFF)
                    }
                    alpha = 1.0f
                }
            }

            allIconViews.add(Pair(iconView, isUsed))

            iconView.setOnClickListener {
                updatePreview(iconResId)
            }

            iconContainer.addView(iconView)
            currentRow.addView(iconContainer)
        }

        // 显示未使用的图标
        if (unusedIcons.isNotEmpty()) {
            addGroupTitle("可选图标（${unusedIcons.size}）")
            unusedIcons.forEachIndexed { index, iconResId ->
                createIconItem(iconResId, false)

                // 换行
                if ((index + 1) % iconsPerRow == 0) {
                    gridLayout.addView(currentRow)
                    currentRow = android.widget.LinearLayout(requireContext()).apply {
                        orientation = android.widget.LinearLayout.HORIZONTAL
                        gravity = android.view.Gravity.CENTER
                    }
                }
            }

            // 添加最后一行（如果不满）
            if (unusedIcons.size % iconsPerRow != 0) {
                gridLayout.addView(currentRow)
                currentRow = android.widget.LinearLayout(requireContext()).apply {
                    orientation = android.widget.LinearLayout.HORIZONTAL
                    gravity = android.view.Gravity.CENTER
                }
            }
        }

        // 显示已使用的图标
        if (usedIcons.isNotEmpty()) {
            addGroupTitle("已使用图标（${usedIcons.size}）")
            usedIcons.forEachIndexed { index, iconResId ->
                createIconItem(iconResId, true)

                // 换行
                if ((index + 1) % iconsPerRow == 0) {
                    gridLayout.addView(currentRow)
                    currentRow = android.widget.LinearLayout(requireContext()).apply {
                        orientation = android.widget.LinearLayout.HORIZONTAL
                        gravity = android.view.Gravity.CENTER
                    }
                }
            }

            // 添加最后一行（如果不满）
            if (usedIcons.size % iconsPerRow != 0) {
                gridLayout.addView(currentRow)
            }
        }

        scrollView.addView(gridLayout)
        container.addView(scrollView)

        // 按钮容器
        val buttonContainer = android.widget.LinearLayout(requireContext()).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.END
            setPadding(0, (16 * dp).toInt(), 0, 0)
        }

        // 删除按钮（仅编辑模式显示）
        if (isEdit && category != null) {
            val cat = category
            val deleteButton = android.widget.TextView(requireContext()).apply {
                text = "删除"
                setTextColor(0xFFFF5252.toInt())
                textSize = 15f
                setPadding((16 * dp).toInt(), (10 * dp).toInt(), (16 * dp).toInt(), (10 * dp).toInt())
                background = android.graphics.drawable.GradientDrawable().apply {
                    setColor(0x15FF5252)
                    cornerRadius = 8 * dp
                }
                setOnClickListener {
                    com.billbook.app.util.AnimeDialogHelper.show(
                        context = requireContext(),
                        title = "确认删除",
                        message = "确定要删除分类「${cat.name}」吗？",
                        positiveText = "删除",
                        negativeText = "取消",
                        onPositive = {
                            val result = categoryDao.delete(cat.id)
                            if (result > 0) {
                                Toast.makeText(requireContext(), "分类已删除", Toast.LENGTH_SHORT).show()
                                dialog?.dismiss()
                                showCategoryDialog()
                            } else {
                                Toast.makeText(requireContext(), "默认分类无法删除", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                }
            }
            buttonContainer.addView(deleteButton)
        }

        // 间距
        buttonContainer.addView(android.view.View(requireContext()).apply {
            layoutParams = android.widget.LinearLayout.LayoutParams(0, 1, 1f)
        })

        // 取消按钮
        val cancelButton = android.widget.TextView(requireContext()).apply {
            text = "取消"
            setTextColor(0x80FFFFFF.toInt())
            textSize = 15f
            setPadding((16 * dp).toInt(), (10 * dp).toInt(), (16 * dp).toInt(), (10 * dp).toInt())
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(0x15FFFFFF)
                cornerRadius = 8 * dp
            }
            setOnClickListener { dialog?.dismiss() }
        }
        buttonContainer.addView(cancelButton)

        // 保存按钮
        val saveButton = android.widget.TextView(requireContext()).apply {
            text = "保存"
            // 强调色在深底主题下会被提亮，白字可能看不清，按实际底色取对比色
            setTextColor(com.billbook.app.util.AutoContrast.textColorFor(accentColor()))
            textSize = 15f
            setPadding((16 * dp).toInt(), (10 * dp).toInt(), (16 * dp).toInt(), (10 * dp).toInt())
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(accentColor())
                cornerRadius = 8 * dp
            }
            setOnClickListener {
                val name = nameInput.text.toString().trim()
                if (name.isEmpty()) {
                    Toast.makeText(requireContext(), "请输入分类名称", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                if (isEdit && category != null) {
                    // 更新分类
                    val cat = category
                    cat.name = name
                    cat.iconResId = selectedIconResId
                    categoryDao.update(cat)
                    Toast.makeText(requireContext(), "分类已更新", Toast.LENGTH_SHORT).show()
                } else {
                    // 添加新分类
                    val newCategory = Category(
                        name = name,
                        iconResId = selectedIconResId,
                        type = defaultType,
                        sortOrder = allCategories.size
                    )
                    categoryDao.insert(newCategory)
                    Toast.makeText(requireContext(), "分类已添加", Toast.LENGTH_SHORT).show()
                }
                dialog?.dismiss()
                showCategoryDialog()
            }
        }
        buttonContainer.addView(saveButton)

        container.addView(buttonContainer)

        dialog = AlertDialog.Builder(requireContext(), android.R.style.Theme_DeviceDefault_Dialog_NoActionBar)
            .setView(container)
            .setCancelable(true)
            .create()

        dialog.show()
        com.billbook.app.util.AnimeDialogHelper.applyAnimeStyle(dialog)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
