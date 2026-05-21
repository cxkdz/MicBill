package com.billbook.app.animation

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.util.AttributeSet
import android.view.animation.LinearInterpolator
import com.google.android.material.button.MaterialButton
import androidx.dynamicanimation.animation.DynamicAnimation
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce

/**
 * 发光按钮：呼吸发光 + 点击弹簧缩放
 */
class GlowButton @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : MaterialButton(context, attrs, defStyleAttr) {

    private var breatheAnimator: ObjectAnimator? = null

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        startBreathe()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopBreathe()
    }

    override fun onVisibilityChanged(changedView: android.view.View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (visibility == VISIBLE) {
            startBreathe()
        } else {
            stopBreathe()
        }
    }

    private fun startBreathe() {
        if (breatheAnimator?.isRunning == true) return
        breatheAnimator = ObjectAnimator.ofFloat(this, "alpha", 0.85f, 1f).apply {
            duration = 2000
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = LinearInterpolator()
            start()
        }
    }

    private fun stopBreathe() {
        breatheAnimator?.cancel()
        breatheAnimator = null
        alpha = 1f
    }

    /**
     * 点击弹簧缩放动画
     */
    fun playClickEffect(onEnd: (() -> Unit)? = null) {
        scaleX = 0.93f
        scaleY = 0.93f

        SpringAnimation(this, DynamicAnimation.SCALE_X, 1f).apply {
            spring.stiffness = SpringForce.STIFFNESS_HIGH
            spring.dampingRatio = SpringForce.DAMPING_RATIO_MEDIUM_BOUNCY
            start()
        }
        SpringAnimation(this, DynamicAnimation.SCALE_Y, 1f).apply {
            spring.stiffness = SpringForce.STIFFNESS_HIGH
            spring.dampingRatio = SpringForce.DAMPING_RATIO_MEDIUM_BOUNCY
            addEndListener { _, _, _, _ ->
                onEnd?.invoke()
            }
            start()
        }
    }
}
