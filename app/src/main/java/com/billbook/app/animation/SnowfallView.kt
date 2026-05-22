package com.billbook.app.animation

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import com.billbook.app.util.ThemeManager
import kotlin.math.sin
import kotlin.random.Random

class SnowfallView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private data class Snowflake(
        var x: Float,
        var y: Float,
        var size: Float,
        var speed: Float,
        var alpha: Int,
        var rotation: Float,
        var rotationSpeed: Float,
        var driftAmplitude: Float,
        var driftFrequency: Float,
        var phase: Float,
        var color: Int
    )

    private val snowflakes = mutableListOf<Snowflake>()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var animator: ValueAnimator? = null
    private var time = 0f
    private val density = resources.displayMetrics.density
    private var fallHeightPx = 0f

    private val snowColors = intArrayOf(
        Color.parseColor("#80FFFFFF"),
        Color.parseColor("#80C8E6FF"),
        Color.parseColor("#60E0F0FF"),
        Color.parseColor("#90FFFFFF")
    )

    init {
        repeat(18) {
            snowflakes.add(createSnowflake(startRandomY = true))
        }
    }

    private fun createSnowflake(startRandomY: Boolean): Snowflake {
        return Snowflake(
            x = Random.nextFloat(),
            y = if (startRandomY) Random.nextFloat() * 0.3f else -Random.nextFloat() * 0.1f,
            size = (2 + Random.nextFloat() * 4) * density,
            speed = 0.4f + Random.nextFloat() * 1.2f,
            alpha = (80 + Random.nextFloat() * 130).toInt(),
            rotation = Random.nextFloat() * 360f,
            rotationSpeed = 1f + Random.nextFloat() * 2f,
            driftAmplitude = (8 + Random.nextFloat() * 12) * density,
            driftFrequency = 0.3f + Random.nextFloat() * 0.8f,
            phase = Random.nextFloat() * Math.PI.toFloat() * 2,
            color = snowColors[Random.nextInt(snowColors.size)]
        )
    }

    fun setFallHeight(heightDp: Int) {
        fallHeightPx = heightDp * density
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        val tm = ThemeManager(context)
        if (!tm.isSnowEnabled()) {
            visibility = GONE
            return
        }
        startAnimation()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopAnimation()
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (visibility == VISIBLE) {
            val tm = ThemeManager(context)
            if (tm.isSnowEnabled()) startAnimation()
        } else {
            stopAnimation()
        }
    }

    private fun startAnimation() {
        if (animator?.isRunning == true) return
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 16L
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                time += 0.016f
                updateSnowflakes()
                invalidate()
            }
            start()
        }
    }

    private fun stopAnimation() {
        animator?.cancel()
        animator = null
    }

    private fun updateSnowflakes() {
        val h = height.toFloat()
        if (h == 0f) return
        val limit = if (fallHeightPx > 0) fallHeightPx else h

        for (s in snowflakes) {
            s.y += s.speed / h
            s.rotation += s.rotationSpeed

            if (s.y * h >= limit) {
                s.x = Random.nextFloat()
                s.y = -Random.nextFloat() * 0.05f
                s.alpha = (80 + Random.nextFloat() * 130).toInt()
                s.size = (2 + Random.nextFloat() * 4) * density
                s.speed = 0.4f + Random.nextFloat() * 1.2f
                s.color = snowColors[Random.nextInt(snowColors.size)]
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w == 0f || h == 0f) return
        val limit = if (fallHeightPx > 0) fallHeightPx else h

        for (s in snowflakes) {
            val drawY = s.y * h
            if (drawY > limit) continue

            val drift = sin((time * s.driftFrequency + s.phase).toDouble()).toFloat() * s.driftAmplitude
            val drawX = s.x * w + drift

            val fadeRatio = 1f - (drawY / limit).coerceIn(0f, 1f) * 0.6f
            paint.color = s.color
            paint.alpha = (s.alpha * fadeRatio).toInt().coerceIn(0, 255)

            canvas.save()
            canvas.rotate(s.rotation, drawX, drawY)
            canvas.drawCircle(drawX, drawY, s.size, paint)
            canvas.restore()
        }
    }
}
