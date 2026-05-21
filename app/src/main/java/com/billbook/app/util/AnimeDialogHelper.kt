package com.billbook.app.util

import androidx.appcompat.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * 二次元风格对话框统一工具。
 * 深色半透明背景 + 圆角 + 白色文字 + anime 配色。
 */
object AnimeDialogHelper {

    private const val BG_COLOR = 0xE61A1A2E.toInt()
    private const val BORDER_COLOR = 0x20FFFFFF
    private const val TITLE_COLOR = 0xE0FFFFFF.toInt()
    private const val MSG_COLOR = 0xB0FFFFFF.toInt()
    private const val HINT_COLOR = 0x50FFFFFF.toInt()
    private const val ACCENT_COLOR = 0xFF6B8DD6.toInt()
    private const val NEGATIVE_COLOR = 0x80FFFFFF.toInt()
    private const val DIVIDER_COLOR = 0x15FFFFFF

    /** 创建一个预设好二次元风格的 AlertDialog.Builder */
    fun createDialog(context: Context): AlertDialog.Builder {
        return AlertDialog.Builder(context, android.R.style.Theme_DeviceDefault_Dialog_NoActionBar)
    }

    /** 构建并显示一个标准二次元对话框 */
    fun show(
        context: Context,
        title: String? = null,
        message: String? = null,
        customView: View? = null,
        positiveText: String? = null,
        negativeText: String? = null,
        neutralText: String? = null,
        onPositive: (() -> Unit)? = null,
        onNegative: (() -> Unit)? = null,
        onNeutral: (() -> Unit)? = null,
        cancelable: Boolean = true
    ): AlertDialog {
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val dp = context.resources.displayMetrics.density
            setPadding((24 * dp).toInt(), (20 * dp).toInt(), (24 * dp).toInt(), (8 * dp).toInt())
        }

        // 标题
        if (title != null) {
            container.addView(TextView(context).apply {
                text = title
                setTextColor(TITLE_COLOR)
                textSize = 18f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setPadding(0, 0, 0, (8 * context.resources.displayMetrics.density).toInt())
            })
        }

        // 消息
        if (message != null) {
            container.addView(TextView(context).apply {
                text = message
                setTextColor(MSG_COLOR)
                textSize = 14f
                setPadding(0, 0, 0, (4 * context.resources.displayMetrics.density).toInt())
            })
        }

        // 自定义内容
        if (customView != null) {
            container.addView(customView)
        }

        val dialog = AlertDialog.Builder(context, android.R.style.Theme_DeviceDefault_Dialog_NoActionBar)
            .setView(container)
            .setCancelable(cancelable)
            .create()

        // 设置按钮
        if (positiveText != null || negativeText != null || neutralText != null) {
            dialog.setOnShowListener {
                positiveText?.let {
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.apply {
                        text = it
                        setTextColor(ACCENT_COLOR)
                        textSize = 15f
                    }
                }
                negativeText?.let {
                    dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.apply {
                        text = it
                        setTextColor(NEGATIVE_COLOR)
                        textSize = 15f
                    }
                }
                neutralText?.let {
                    dialog.getButton(AlertDialog.BUTTON_NEUTRAL)?.apply {
                        text = it
                        setTextColor(HINT_COLOR)
                        textSize = 15f
                    }
                }
            }
        }

        // 先设置按钮回调
        if (positiveText != null) {
            dialog.setButton(AlertDialog.BUTTON_POSITIVE, positiveText) { _, _ -> onPositive?.invoke() }
        }
        if (negativeText != null) {
            dialog.setButton(AlertDialog.BUTTON_NEGATIVE, negativeText) { _, _ -> onNegative?.invoke() }
        }
        if (neutralText != null) {
            dialog.setButton(AlertDialog.BUTTON_NEUTRAL, neutralText) { _, _ -> onNeutral?.invoke() }
        }

        dialog.show()

        // 应用二次元背景
        applyAnimeStyle(dialog)

        return dialog
    }

    /** 对已有的 AlertDialog 应用二次元风格 */
    fun applyAnimeStyle(dialog: AlertDialog) {
        val dp = dialog.context.resources.displayMetrics.density

        // 窗口背景：深色圆角
        dialog.window?.setBackgroundDrawable(
            GradientDrawable().apply {
                setColor(BG_COLOR)
                cornerRadius = 20 * dp
                setStroke((1 * dp).toInt(), BORDER_COLOR)
            }
        )

        // 按钮颜色
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(ACCENT_COLOR)
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(NEGATIVE_COLOR)
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL)?.setTextColor(HINT_COLOR)
    }

    /** 创建标准标签（用于列表项、分类等） */
    fun createTag(context: Context, text: String, selected: Boolean = false): TextView {
        val dp = context.resources.displayMetrics.density
        return TextView(context).apply {
            this.text = text
            textSize = 13f
            gravity = Gravity.CENTER
            setPadding((16 * dp).toInt(), (8 * dp).toInt(), (16 * dp).toInt(), (8 * dp).toInt())
            background = GradientDrawable().apply {
                setColor(if (selected) 0x25FFFFFF else 0x10FFFFFF)
                cornerRadius = 16 * dp
                setStroke((1 * dp).toInt(), if (selected) 0x40FFFFFF else 0x15FFFFFF)
            }
            setTextColor(if (selected) TITLE_COLOR else MSG_COLOR)
        }
    }

    /** 创建标准输入框 */
    fun createEditText(context: Context, hint: String = "", prefill: String = ""): EditText {
        val dp = context.resources.displayMetrics.density
        return EditText(context).apply {
            this.hint = hint
            setText(prefill)
            setTextColor(TITLE_COLOR)
            setHintTextColor(HINT_COLOR)
            textSize = 16f
            setPadding((16 * dp).toInt(), (12 * dp).toInt(), (16 * dp).toInt(), (12 * dp).toInt())
            background = GradientDrawable().apply {
                setColor(0x10FFFFFF)
                cornerRadius = 12 * dp
                setStroke((1 * dp).toInt(), 0x15FFFFFF)
            }
            setSingleLine()
        }
    }

    /** 创建带标签的水平滚动行 */
    fun createTagRow(context: Context, tags: List<String>, onTagClick: (String) -> Unit): LinearLayout {
        val dp = context.resources.displayMetrics.density
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            val scrollView = android.widget.HorizontalScrollView(context).apply {
                isHorizontalScrollBarEnabled = false
                setPadding(0, (8 * dp).toInt(), 0, (8 * dp).toInt())
            }
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
            }
            tags.forEach { tag ->
                row.addView(createTag(context, tag).apply {
                    setOnClickListener { onTagClick(tag) }
                    val lp = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                    lp.marginEnd = (8 * dp).toInt()
                    layoutParams = lp
                })
            }
            scrollView.addView(row)
            addView(scrollView, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ))
        }
    }

    // ==================== 列表对话框 ====================

    /** 显示二次元风格的列表选择对话框 */
    fun showListDialog(
        context: Context,
        title: String,
        items: List<String>,
        onItemSelected: (Int) -> Unit
    ): AlertDialog {
        val dp = context.resources.displayMetrics.density

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((24 * dp).toInt(), (20 * dp).toInt(), (24 * dp).toInt(), (12 * dp).toInt())
        }

        // 标题
        container.addView(TextView(context).apply {
            text = title
            setTextColor(TITLE_COLOR)
            textSize = 18f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, (12 * dp).toInt())
        })

        // 列表项
        val scrollView = ScrollView(context)
        val listLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }

        items.forEachIndexed { index, item ->
            listLayout.addView(TextView(context).apply {
                text = item
                setTextColor(MSG_COLOR)
                textSize = 15f
                setPadding((12 * dp).toInt(), (12 * dp).toInt(), (12 * dp).toInt(), (12 * dp).toInt())
                background = GradientDrawable().apply {
                    setColor(0x00FFFFFF)
                    cornerRadius = 8 * dp
                }
                setOnClickListener { onItemSelected(index) }
                // 按压反馈
                isClickable = true
                isFocusable = true
            })

            // 分割线
            if (index < items.size - 1) {
                listLayout.addView(View(context).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        (1 * dp).toInt()
                    )
                    setBackgroundColor(DIVIDER_COLOR)
                })
            }
        }

        scrollView.addView(listLayout)
        container.addView(scrollView, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            0, 1f
        ))

        val dialog = AlertDialog.Builder(context, android.R.style.Theme_DeviceDefault_Dialog_NoActionBar)
            .setView(container)
            .setCancelable(true)
            .create()

        dialog.show()
        applyAnimeStyle(dialog)

        return dialog
    }

    /** 显示带项目符号的对话框（icon + name 列表） */
    fun showIconListDialog(
        context: Context,
        title: String,
        items: List<Pair<String, String>>,  // (icon, name)
        onItemSelected: (Int) -> Unit,
        neutralText: String? = null,
        onNeutral: (() -> Unit)? = null
    ): AlertDialog {
        val dp = context.resources.displayMetrics.density

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((24 * dp).toInt(), (20 * dp).toInt(), (24 * dp).toInt(), (12 * dp).toInt())
        }

        // 标题
        container.addView(TextView(context).apply {
            text = title
            setTextColor(TITLE_COLOR)
            textSize = 18f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, (12 * dp).toInt())
        })

        // 列表
        val scrollView = ScrollView(context)
        val listLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }

        items.forEachIndexed { index, (icon, name) ->
            listLayout.addView(TextView(context).apply {
                text = "$icon  $name"
                setTextColor(MSG_COLOR)
                textSize = 15f
                setPadding((12 * dp).toInt(), (12 * dp).toInt(), (12 * dp).toInt(), (12 * dp).toInt())
                setOnClickListener { onItemSelected(index) }
                isClickable = true
                isFocusable = true
            })

            if (index < items.size - 1) {
                listLayout.addView(View(context).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        (1 * dp).toInt()
                    )
                    setBackgroundColor(DIVIDER_COLOR)
                })
            }
        }

        scrollView.addView(listLayout)
        container.addView(scrollView, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            0, 1f
        ))

        val builder = AlertDialog.Builder(context, android.R.style.Theme_DeviceDefault_Dialog_NoActionBar)
            .setView(container)
            .setCancelable(true)

        if (neutralText != null) {
            builder.setNeutralButton(neutralText, null)
        }

        val dialog = builder.create()

        if (neutralText != null) {
            dialog.setOnShowListener {
                dialog.getButton(AlertDialog.BUTTON_NEUTRAL)?.apply {
                    setTextColor(HINT_COLOR)
                    setOnClickListener { onNeutral?.invoke() }
                }
            }
        }

        dialog.show()
        applyAnimeStyle(dialog)

        return dialog
    }
}
