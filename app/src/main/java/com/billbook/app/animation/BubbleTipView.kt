package com.billbook.app.animation

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator

/**
 * 浮动气泡提示视图
 * - 圆角矩形 + 底部小三角箭头
 * - 呼吸动画：上下浮动 + alpha 变化
 * - 文字随金额变化
 */
class BubbleTipView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val bubblePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#20FFFFFF")
        style = Paint.Style.FILL
    }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#30FFFFFF")
        style = Paint.Style.STROKE
        strokeWidth = 1f * resources.displayMetrics.density
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#B0FFFFFF")
        textSize = 13f * resources.displayMetrics.density
        textAlign = Paint.Align.CENTER
    }

    private val density = resources.displayMetrics.density
    private val cornerRadius = 16f * density
    private val arrowSize = 6f * density
    private val hPadding = 20f * density
    private val vPadding = 10f * density

    private var tipText = "记一笔日常开销~"
    private var breatheAnimator: ObjectAnimator? = null
    private var textAlpha = 1f

    private val bubblePath = Path()
    private val bubbleRect = RectF()

    init {
        // 默认呼吸动画
        startBreathe()
    }

    fun setTipText(text: String) {
        if (tipText == text) return
        // 淡出 → 换字 → 淡入
        animate()
            .alpha(0f)
            .setDuration(150)
            .withEndAction {
                tipText = text
                requestLayout()
                animate()
                    .alpha(1f)
                    .setDuration(150)
                    .start()
            }
            .start()
    }

    fun updateForAmount(amount: Double) {
        val newTip = when {
            amount <= 0 -> "记一笔日常开销~"
            amount < 10 -> "省吃俭用~"
            amount < 50 -> "日常开销呢"
            amount < 100 -> "小目标完成啦！"
            amount < 500 -> "今日消费达人"
            else -> "超出预算啦！"
        }
        setTipText(newTip)
    }

    private fun startBreathe() {
        breatheAnimator = ObjectAnimator.ofFloat(this, "translationY", -3f * density, 3f * density).apply {
            duration = 2500
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = LinearInterpolator()
            start()
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        breatheAnimator?.cancel()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val textWidth = textPaint.measureText(tipText)
        val desiredWidth = (textWidth + hPadding * 2).toInt()
        val desiredHeight = (textPaint.textSize + vPadding * 2 + arrowSize + 4 * density).toInt()

        val width = resolveSize(desiredWidth, widthMeasureSpec)
        val height = resolveSize(desiredHeight, heightMeasureSpec)
        setMeasuredDimension(width, height)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        val bubbleBottom = h - arrowSize - 2 * density

        // 气泡路径
        bubblePath.reset()
        bubbleRect.set(0f, 0f, w, bubbleBottom)
        bubblePath.addRoundRect(bubbleRect, cornerRadius, cornerRadius, Path.Direction.CW)

        // 底部三角箭头
        val arrowCenterX = w / 2
        val arrowTop = bubbleBottom
        val arrowTip = h - 1 * density
        bubblePath.moveTo(arrowCenterX - arrowSize, arrowTop)
        bubblePath.lineTo(arrowCenterX, arrowTip)
        bubblePath.lineTo(arrowCenterX + arrowSize, arrowTop)
        bubblePath.close()

        // 绘制
        canvas.drawPath(bubblePath, bubblePaint)
        canvas.drawPath(bubblePath, borderPaint)

        // 文字
        val textX = w / 2
        val textY = bubbleBottom / 2 - (textPaint.descent() + textPaint.ascent()) / 2
        canvas.drawText(tipText, textX, textY, textPaint)
    }
}
