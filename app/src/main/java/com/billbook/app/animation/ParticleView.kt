package com.billbook.app.animation

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.sin
import kotlin.random.Random

/**
 * 浮动粒子光效视图
 * 绘制 25 个缓慢上浮的半透明白色光点，带正弦波运动
 */
class ParticleView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private data class Particle(
        var x: Float,
        var y: Float,
        var radius: Float,
        var alpha: Int,
        var speed: Float,
        var sinAmplitude: Float,
        var sinFrequency: Float,
        var phase: Float
    )

    private val particles = mutableListOf<Particle>()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }

    private var animator: ValueAnimator? = null
    private var time = 0f

    private val density = resources.displayMetrics.density

    init {
        initParticles()
    }

    private fun initParticles() {
        particles.clear()
        repeat(25) {
            particles.add(createParticle(startRandomY = true))
        }
    }

    private fun createParticle(startRandomY: Boolean): Particle {
        return Particle(
            x = Random.nextFloat(),
            y = if (startRandomY) Random.nextFloat() else 1f + Random.nextFloat() * 0.3f,
            radius = (2 + Random.nextFloat() * 4) * density,
            alpha = (25 + Random.nextFloat() * 60).toInt(),
            speed = 0.0003f + Random.nextFloat() * 0.0008f,
            sinAmplitude = (5 + Random.nextFloat() * 15) * density,
            sinFrequency = 0.5f + Random.nextFloat() * 1.5f,
            phase = Random.nextFloat() * Math.PI.toFloat() * 2
        )
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        startAnimation()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopAnimation()
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (visibility == VISIBLE) {
            startAnimation()
        } else {
            stopAnimation()
        }
    }

    private fun startAnimation() {
        if (animator?.isRunning == true) return
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 16L // ~60fps
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                time += 0.016f
                updateParticles()
                invalidate()
            }
            start()
        }
    }

    private fun stopAnimation() {
        animator?.cancel()
        animator = null
    }

    private fun updateParticles() {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w == 0f || h == 0f) return

        for (p in particles) {
            p.y -= p.speed
            if (p.y < -0.1f) {
                // 重置到底部
                p.y = 1f + Random.nextFloat() * 0.2f
                p.x = Random.nextFloat()
                p.alpha = (25 + Random.nextFloat() * 60).toInt()
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w == 0f || h == 0f) return

        for (p in particles) {
            val baseX = p.x * w
            val baseY = p.y * h
            val offsetX = sin((time * p.sinFrequency + p.phase).toDouble()).toFloat() * p.sinAmplitude
            val drawX = baseX + offsetX
            val drawY = baseY

            if (drawY < -p.radius || drawY > h + p.radius) continue

            paint.alpha = p.alpha
            canvas.drawCircle(drawX, drawY, p.radius, paint)
        }
    }
}
