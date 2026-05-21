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
import com.billbook.app.util.CategoryIcons
import com.billbook.app.util.ThemeManager
import com.billbook.app.util.WeChatBillParser
import com.billbook.app.util.AlipayBillParser
import com.billbook.app.db.BillDao
import com.billbook.app.dialog.ThemeEditorDialog

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    private lateinit var themeManager: ThemeManager

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

        setupThemeEditor()
        setupDarkModeSwitch()
        setupTiltAngle()
        setupCustomImageBg()
        setupTextColorToggle()
        setupCategoryManage()
        setupBudgetAndQuery()
        setupImportBills()
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
        val parser = WeChatBillParser(requireContext())
        val previewResult = parser.preview(uri)

        if (previewResult.totalCount == 0) {
            Toast.makeText(requireContext(), "未找到有效账单数据", Toast.LENGTH_SHORT).show()
            return
        }

        val message = buildString {
            appendLine("找到 ${previewResult.totalCount} 条记录")
            appendLine("有效记录：${previewResult.successCount} 条")
            if (previewResult.skipCount > 0) appendLine("将跳过：${previewResult.skipCount} 条")
            if (previewResult.errorMessages.isNotEmpty()) appendLine("解析错误：${previewResult.errorMessages.size} 条")
            appendLine("\n是否导入？")
        }

        com.billbook.app.util.AnimeDialogHelper.show(
            context = requireContext(),
            title = "导入微信账单",
            message = message,
            positiveText = "导入",
            negativeText = "取消",
            onPositive = { executeWechatImport(uri) }
        )
    }

    private fun executeWechatImport(uri: Uri) {
        val parser = WeChatBillParser(requireContext())
        val result = parser.parse(uri, skipDuplicate = true, filterTypes = true)

        if (result.bills.isEmpty()) {
            Toast.makeText(requireContext(), "没有可导入的新记录", Toast.LENGTH_SHORT).show()
            return
        }

        val billDao = BillDao(requireContext())
        var insertCount = 0
        for (bill in result.bills) {
            try { billDao.insert(bill); insertCount++ } catch (_: Exception) {}
        }

        com.billbook.app.util.AnimeDialogHelper.show(
            context = requireContext(),
            title = "导入结果",
            message = "导入完成！成功导入：$insertCount 条",
            positiveText = "确定",
            onPositive = { activity?.recreate() }
        )
    }

    private fun importAlipayBill(uri: Uri) {
        val parser = AlipayBillParser(requireContext())
        val previewResult = parser.preview(uri)

        if (previewResult.totalCount == 0) {
            Toast.makeText(requireContext(), "未找到有效账单数据", Toast.LENGTH_SHORT).show()
            return
        }

        val message = buildString {
            appendLine("找到 ${previewResult.totalCount} 条记录")
            appendLine("有效记录：${previewResult.successCount} 条")
            if (previewResult.skipCount > 0) appendLine("将跳过：${previewResult.skipCount} 条")
            appendLine("\n是否导入？")
        }

        com.billbook.app.util.AnimeDialogHelper.show(
            context = requireContext(),
            title = "导入支付宝账单",
            message = message,
            positiveText = "导入",
            negativeText = "取消",
            onPositive = { executeAlipayImport(uri) }
        )
    }

    private fun executeAlipayImport(uri: Uri) {
        val parser = AlipayBillParser(requireContext())
        val result = parser.parse(uri, skipDuplicate = true, filterTypes = true)

        if (result.bills.isEmpty()) {
            Toast.makeText(requireContext(), "没有可导入的新记录", Toast.LENGTH_SHORT).show()
            return
        }

        val billDao = BillDao(requireContext())
        var insertCount = 0
        for (bill in result.bills) {
            try { billDao.insert(bill); insertCount++ } catch (_: Exception) {}
        }

        com.billbook.app.util.AnimeDialogHelper.show(
            context = requireContext(),
            title = "导入结果",
            message = "导入完成！成功导入：$insertCount 条",
            positiveText = "确定",
            onPositive = { activity?.recreate() }
        )
    }

    private fun setupThemeEditor() {
        val currentPalette = themeManager.getEffectivePalette()
        binding.tvCurrentPalette.text = currentPalette.name

        binding.btnThemeEditor.setOnClickListener {
            val dialog = ThemeEditorDialog()
            dialog.setStyle(DialogFragment.STYLE_NO_TITLE, androidx.appcompat.R.style.Theme_AppCompat_Light_Dialog)
            dialog.setOnApplyListener { key ->
                themeManager.setPalette(key)
                binding.tvCurrentPalette.text = themeManager.getEffectivePalette().name
                requireActivity().recreate()
            }
            dialog.show(parentFragmentManager, ThemeEditorDialog.TAG)
        }
    }

    private fun setupDarkModeSwitch() {
        val isDark = themeManager.isDarkMode()
        binding.switchDarkMode.isChecked = isDark
        binding.tvDarkModeStatus.text = if (isDark) "开启" else "关闭"

        binding.switchDarkMode.setOnCheckedChangeListener { _, isChecked ->
            themeManager.setDarkMode(isChecked)
            if (isChecked) {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            } else {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            }
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
            thumb?.setTint(0xFF6B8DD6.toInt())
            progressDrawable?.setTint(0xFF6B8DD6.toInt())
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
                setTextColor(0xFF6B8DD6.toInt())
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
            Toast.makeText(requireContext(), "已恢复为纯色背景", Toast.LENGTH_SHORT).show()
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
            thumb?.setTint(0xFF6B8DD6.toInt())
            progressDrawable?.setTint(0xFF6B8DD6.toInt())
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
                setTextColor(0xFF6B8DD6.toInt())
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
                message = "是否现在调整背景透明度？\n\n透明度越高，背景越淡，文字越清晰。\n建议设为20%-40%。",
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
            binding.tvCustomBgStatus.text = "当前：自定义图片"
            binding.tvCustomBgStatus.setTextColor(resources.getColor(android.R.color.holo_green_dark, null))
            binding.btnClearImage.visibility = View.VISIBLE
            binding.layoutOpacity.visibility = View.VISIBLE

            try {
                val uri = themeManager.getCustomBackgroundUri()
                if (uri != null) {
                    val inputStream = requireContext().contentResolver.openInputStream(uri)
                    val bitmap = android.graphics.BitmapFactory.decodeStream(inputStream)
                    inputStream?.close()
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
            binding.tvCustomBgStatus.text = "当前：纯色背景"
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

    private fun setupTextColorToggle() {
        val mode = themeManager.getTextColorMode()
        binding.tvTextColorStatus.text = when (mode) {
            1 -> "深色文字"
            2 -> "浅色文字"
            else -> "自动"
        }

        binding.btnTextColorToggle.setOnClickListener {
            val current = themeManager.getTextColorMode()
            val next = (current + 1) % 3
            themeManager.setTextColorMode(next)
            binding.tvTextColorStatus.text = when (next) {
                1 -> "深色文字"
                2 -> "浅色文字"
                else -> "自动"
            }
            requireActivity().recreate()
        }
    }

    private fun setupCategoryManage() {
        binding.btnManageCategory.setOnClickListener { showCategoryDialog() }
        binding.btnAddCategory.setOnClickListener { showAddCategoryDialog() }
    }

    private fun showCategoryDialog() {
        val categoryDao = CategoryDao(requireContext())
        val categories = categoryDao.getAll()
        val items = categories.map { Pair(it.icon, it.name) }

        com.billbook.app.util.AnimeDialogHelper.showIconListDialog(
            context = requireContext(),
            title = "分类管理",
            items = items,
            onItemSelected = { index -> showEditCategoryDialog(categories[index]) }
        )
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
        val icons = CategoryIcons.ALL_ICONS

        com.billbook.app.util.AnimeDialogHelper.showListDialog(
            context = requireContext(),
            title = if (category == null) "选择图标" else "修改图标",
            items = icons,
            onItemSelected = { iconIndex ->
                val selectedIcon = icons[iconIndex]
                if (category != null) {
                    val categoryDao = CategoryDao(requireContext())
                    category.icon = selectedIcon
                    categoryDao.update(category)
                    Toast.makeText(requireContext(), "图标已更新", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(requireContext(), "选中图标：$selectedIcon，请完善分类信息", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
