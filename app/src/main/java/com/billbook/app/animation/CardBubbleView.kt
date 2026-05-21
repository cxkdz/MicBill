package com.billbook.app.animation

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.sin
import kotlin.random.Random

/**
 * 卡片气泡上浮动画 View。
 * 圆形/椭圆半透明光点从底部上浮到顶部，带正弦摆动、缩放呼吸和 glow 光晕。
 * 绘制在卡片内容下方，不遮挡文字。
 * 通过 setTargetBounds 限制气泡只在指定区域内生成和绘制。
 */
class CardBubbleView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private data class Bubble(
        var x: Float,
        var y: Float,
        val radius: Float,
        val scaleY: Float,       // 椭圆纵向缩放（1.0=圆形）
        val speed: Float,
        val alpha: Int,
        val wobbleAmp: Float,
        val wobbleFreq: Float,
        val phase: Float,
        val breatheSpeed: Float  // 呼吸动画速度
    )

    private val bubbles = mutableListOf<Bubble>()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var animator: ValueAnimator? = null
    private val bubbleCount = 14

    // 目标绘制区域（相对于本 View 的坐标），null 表示使用整个 View
    private var targetBounds: RectF? = null

    fun setTargetBounds(left: Float, top: Float, right: Float, bottom: Float) {
        targetBounds = RectF(left, top, right, bottom)
        if (bubbles.isNotEmpty()) {
            initBubbles()
        }
    }

    fun start() {
        if (animator?.isRunning == true) return
        post {
            if (width == 0 || height == 0) return@post
            initBubbles()
            animator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = Long.MAX_VALUE
                interpolator = LinearInterpolator()
                addUpdateListener { invalidate() }
                start()
            }
        }
    }

    fun stop() {
        animator?.cancel()
        animator = null
        bubbles.clear()
        invalidate()
    }

    private fun getDrawArea(): RectF {
        return targetBounds ?: RectF(0f, 0f, width.toFloat(), height.toFloat())
    }

    private fun initBubbles() {
        bubbles.clear()
        val dp = resources.displayMetrics.density
        val area = getDrawArea()
        for (i in 0 until bubbleCount) {
            bubbles.add(createBubble(area, dp, startFromBottom = true))
        }
    }

    private fun createBubble(area: RectF, dp: Float, startFromBottom: Boolean): Bubble {
        val isOval = Random.nextFloat() < 0.35f // 35% 椭圆
        val baseRadius = (4 + Random.nextFloat() * 8) * dp // 4-12dp
        return Bubble(
            x = area.left + Random.nextFloat() * area.width(),
            y = if (startFromBottom) area.bottom + Random.nextFloat() * 20 * dp
                else area.top + Random.nextFloat() * area.height(),
            radius = baseRadius,
            scaleY = if (isOval) 0.6f + Random.nextFloat() * 0.4f else 1f,
            speed = (0.2f + Random.nextFloat() * 0.6f) * dp,
            alpha = (12 + Random.nextInt(28)), // 12-40
            wobbleAmp = (2 + Random.nextFloat() * 6) * dp,
            wobbleFreq = 0.006f + Random.nextFloat() * 0.01f,
            phase = Random.nextFloat() * 6.283f,
            breatheSpeed = 0.001f + Random.nextFloat() * 0.002f
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (bubbles.isEmpty() || width == 0 || height == 0) return

        val area = getDrawArea()
        canvas.save()
        canvas.clipRect(area)

        val time = System.currentTimeMillis()
        val dp = resources.displayMetrics.density

        for (i in bubbles.indices) {
            val b = bubbles[i]
            // 移动
            b.y -= b.speed

            // 到达目标区域顶部 → 重置到底部
            if (b.y + b.radius < area.top) {
                bubbles[i] = createBubble(area, dp, startFromBottom = true)
                continue
            }

            // 正弦摆动
            val wobbleX = sin((time * b.wobbleFreq + b.phase).toDouble()).toFloat() * b.wobbleAmp
            val drawX = b.x + wobbleX

            // 顶部渐隐（相对于目标区域）
            val fadeStart = area.top + area.height() * 0.3f
            val alphaFactor = if (b.y < fadeStart) {
                ((b.y - area.top) / (fadeStart - area.top)).coerceIn(0f, 1f)
            } else 1f

            // 呼吸缩放
            val breathe = 1f + 0.15f * sin((time * b.breatheSpeed + b.phase).toDouble()).toFloat()

            val finalAlpha = (b.alpha * alphaFactor).toInt().coerceIn(0, 255)
            if (finalAlpha <= 0) continue

            val r = b.radius * breathe

            // 外层 glow 光晕（2.5倍大小，1/4 透明度）
            val glowAlpha = (finalAlpha * 0.25f).toInt().coerceIn(0, 255)
            if (glowAlpha > 0) {
                glowPaint.color = Color.argb(glowAlpha, 255, 255, 255)
                if (b.scaleY < 1f) {
                    canvas.save()
                    canvas.scale(1f, b.scaleY, drawX, b.y)
                    canvas.drawCircle(drawX, b.y, r * 2.5f, glowPaint)
                    canvas.restore()
                } else {
                    canvas.drawCircle(drawX, b.y, r * 2.5f, glowPaint)
                }
            }

            // 内层实体光点
            paint.color = Color.argb(finalAlpha, 255, 255, 255)
            if (b.scaleY < 1f) {
                canvas.save()
                canvas.scale(1f, b.scaleY, drawX, b.y)
                canvas.drawCircle(drawX, b.y, r, paint)
                canvas.restore()
            } else {
                canvas.drawCircle(drawX, b.y, r, paint)
            }
        }

        canvas.restore()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stop()
    }
}
