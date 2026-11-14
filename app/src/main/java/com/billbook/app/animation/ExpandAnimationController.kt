package com.billbook.app.animation

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.content.res.Resources
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.dynamicanimation.animation.DynamicAnimation
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import com.billbook.app.model.ThemePalette

/**
 * FAB 展开到全屏的高级动画控制器
 *
 * 使用 scale + translation 实现从 FAB 位置展开到全屏的效果
 * 3 个独立动画通道：
 * 1. 容器通道 (600ms): position/size/cornerRadius/alpha
 * 2. 背景通道 (500ms): dim alpha
 * 3. 内容通道 (300ms, 延迟 300ms): content alpha + translationY
 */
class ExpandAnimationController(
    private val overlayRoot: View,
    private val dimBackground: View,
    private val expandContainer: View,
    private val expandContent: View,
    private val mainContentRoot: View,
    private val fabAdd: View,
    private val resources: Resources
) {
    enum class State { IDLE, EXPANDING, EXPANDED, COLLAPSING }

    var state = State.IDLE
        private set

    private val glassMorphDrawable = GlassMorphDrawable()
    private var containerAnimator: ValueAnimator? = null
    private var backgroundAnimator: ValueAnimator? = null
    private var contentAnimator: ValueAnimator? = null
    private var mainContentAnimator: ObjectAnimator? = null

    private val fabSizePx = (52 * resources.displayMetrics.density).toInt()
    private val screenWidth = resources.displayMetrics.widthPixels
    private val screenHeight = resources.displayMetrics.heightPixels
    private val screenCenterX = screenWidth / 2f
    private val screenCenterY = screenHeight / 2f

    // FAB 屏幕坐标（每次展开时更新）
    private var fabCenterX = 0f
    private var fabCenterY = 0f

    init {
        expandContainer.background = glassMorphDrawable
    }

    /** 记账浮层容器跟随主题：每套主题用自己的深色变体，而不是固定深蓝紫 */
    fun applyPalette(palette: ThemePalette) {
        glassMorphDrawable.applyTheme(palette.dioramaGround, palette.planeAccent)
    }

    /**
     * 从 FAB 位置展开到全屏
     */
    fun expand() {
        if (state == State.EXPANDING || state == State.EXPANDED) return
        cancelAllAnimators()

        // 获取 FAB 屏幕坐标
        val location = IntArray(2)
        fabAdd.getLocationOnScreen(location)
        fabCenterX = location[0] + fabAdd.width / 2f
        fabCenterY = location[1] + fabAdd.height / 2f

        // 显示 overlay，隐藏 FAB
        overlayRoot.visibility = View.VISIBLE
        fabAdd.visibility = View.INVISIBLE

        // 设置容器初始状态：位于 FAB 位置，FAB 大小
        expandContainer.translationX = fabCenterX - screenCenterX
        expandContainer.translationY = fabCenterY - screenCenterY
        expandContainer.scaleX = fabSizePx.toFloat() / screenWidth
        expandContainer.scaleY = fabSizePx.toFloat() / screenHeight
        expandContainer.pivotX = screenCenterX
        expandContainer.pivotY = screenCenterY
        expandContainer.alpha = 1f
        glassMorphDrawable.cornerRadius = 300f

        expandContent.alpha = 0f
        expandContent.translationY = 20 * resources.displayMetrics.density

        state = State.EXPANDING
        startContainerAnimation(forward = true)
        startBackgroundAnimation(forward = true)
        startMainContentAnimation(forward = true)
    }

    /**
     * 收缩回 FAB 位置
     */
    fun collapse() {
        if (state == State.IDLE || state == State.COLLAPSING) return
        cancelAllAnimators()

        state = State.COLLAPSING
        startContainerAnimation(forward = false)
        startBackgroundAnimation(forward = false)
        startMainContentAnimation(forward = false)
    }

    private fun startContainerAnimation(forward: Boolean) {
        val startValue = if (forward) 0f else 1f
        val endValue = if (forward) 1f else 0f

        containerAnimator = ValueAnimator.ofFloat(startValue, endValue).apply {
            duration = 350
            interpolator = if (forward) DecelerateInterpolator(2f) else DecelerateInterpolator(1.5f)

            addUpdateListener { anim ->
                val progress = anim.animatedValue as Float

                // 位置：FAB 中心 → 屏幕中心
                expandContainer.translationX = lerp(fabCenterX - screenCenterX, 0f, progress)
                expandContainer.translationY = lerp(fabCenterY - screenCenterY, 0f, progress)

                // 尺寸：FAB → 全屏（通过 scale 实现）
                val scaleStart = fabSizePx.toFloat() / screenWidth
                expandContainer.scaleX = lerp(scaleStart, 1f, progress)
                expandContainer.scaleY = lerp(scaleStart, 1f, progress)

                // 圆角：300 → 0
                glassMorphDrawable.cornerRadius = lerp(300f, 0f, progress)

                // 容器 alpha：快速出现
                expandContainer.alpha = (progress / 0.2f).coerceIn(0f, 1f)
            }

            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if (forward) {
                        startSpringOvershoot()
                        startContentAnimation()
                        state = State.EXPANDED
                    } else {
                        overlayRoot.visibility = View.GONE
                        fabAdd.visibility = View.VISIBLE
                        state = State.IDLE
                        resetContainer()
                    }
                }
            })
            start()
        }
    }

    private fun startBackgroundAnimation(forward: Boolean) {
        val startValue = if (forward) 0f else 1f
        val endValue = if (forward) 1f else 0f

        backgroundAnimator = ValueAnimator.ofFloat(startValue, endValue).apply {
            duration = 300
            interpolator = DecelerateInterpolator()

            addUpdateListener { anim ->
                val progress = anim.animatedValue as Float
                dimBackground.alpha = progress * 0.4f
            }
            start()
        }
    }

    private fun startContentAnimation() {
        contentAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 200
            interpolator = DecelerateInterpolator()

            addUpdateListener { anim ->
                val progress = anim.animatedValue as Float
                expandContent.alpha = progress
                expandContent.translationY = 20 * resources.displayMetrics.density * (1f - progress)
            }
            start()
        }
    }

    private fun startMainContentAnimation(forward: Boolean) {
        val startScale = if (forward) 1f else 0.95f
        val endScale = if (forward) 0.95f else 1f
        val startAlpha = if (forward) 1f else 0.6f
        val endAlpha = if (forward) 0.6f else 1f

        mainContentAnimator = ObjectAnimator.ofPropertyValuesHolder(
            mainContentRoot,
            PropertyValuesHolder.ofFloat("scaleX", startScale, endScale),
            PropertyValuesHolder.ofFloat("scaleY", startScale, endScale),
            PropertyValuesHolder.ofFloat("alpha", startAlpha, endAlpha)
        ).apply {
            duration = 300
            interpolator = DecelerateInterpolator()
            start()
        }
    }

    private fun startSpringOvershoot() {
        SpringAnimation(expandContainer, DynamicAnimation.SCALE_X, 1f).apply {
            spring.stiffness = SpringForce.STIFFNESS_MEDIUM
            spring.dampingRatio = SpringForce.DAMPING_RATIO_MEDIUM_BOUNCY
            setStartValue(0.97f)
            start()
        }
        SpringAnimation(expandContainer, DynamicAnimation.SCALE_Y, 1f).apply {
            spring.stiffness = SpringForce.STIFFNESS_MEDIUM
            spring.dampingRatio = SpringForce.DAMPING_RATIO_MEDIUM_BOUNCY
            setStartValue(0.97f)
            start()
        }
    }

    private fun resetContainer() {
        expandContainer.alpha = 0f
        expandContent.alpha = 0f
        expandContainer.translationX = 0f
        expandContainer.translationY = 0f
        expandContainer.scaleX = 1f
        expandContainer.scaleY = 1f
        glassMorphDrawable.cornerRadius = 300f
    }

    private fun cancelAllAnimators() {
        containerAnimator?.cancel()
        backgroundAnimator?.cancel()
        contentAnimator?.cancel()
        mainContentAnimator?.cancel()
    }

    private fun lerp(start: Float, end: Float, fraction: Float): Float {
        return start + (end - start) * fraction
    }
}
