package com.billbook.app.view

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Shader
import android.net.Uri
import android.util.AttributeSet
import android.view.View
import com.billbook.app.util.BitmapUtils

/**
 * Layer 1「背景」：只做两件事——画预设的纯色渐变，或画用户自定义背景图。
 * 旧的 10 种程序化场景绘制（森林 / 雪山 / 赛博朋克…）从未被调用，已整体删除。
 */
class DioramaView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var customBitmap: Bitmap? = null
    private var gradientTop: Int? = null
    private var gradientBottom: Int? = null
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    init {
        setLayerType(LAYER_TYPE_HARDWARE, null)
    }

    /** 应用预设的纯色渐变背景（自上而下） */
    fun applyGradient(top: Int, bottom: Int) {
        gradientTop = top
        gradientBottom = bottom
        customBitmap = null
        invalidate()
    }

    /** 用户自定义背景图优先于渐变 */
    fun applyCustomImage(uri: Uri) {
        try {
            val metrics = resources.displayMetrics
            customBitmap = BitmapUtils.decodeSampledUri(
                context, uri, metrics.widthPixels, metrics.heightPixels
            )
            invalidate()
        } catch (_: Exception) {
            customBitmap = null
        }
    }

    override fun onDraw(canvas: Canvas) {
        paint.shader = null
        val bmp = customBitmap
        if (bmp != null) {
            drawCustomImage(canvas, bmp)
            return
        }
        val top = gradientTop ?: return
        val bottom = gradientBottom ?: return
        paint.shader = LinearGradient(
            0f, 0f, 0f, height.toFloat(),
            top, bottom, Shader.TileMode.CLAMP
        )
        canvas.drawPaint(paint)
        paint.shader = null
    }

    private fun drawCustomImage(canvas: Canvas, bitmap: Bitmap) {
        val w = width.toFloat()
        val h = height.toFloat()
        val bw = bitmap.width.toFloat()
        val bh = bitmap.height.toFloat()
        if (w <= 0f || h <= 0f || bw <= 0f || bh <= 0f) return
        val scale = maxOf(w / bw, h / bh)
        val matrix = Matrix().apply {
            setScale(scale, scale)
            postTranslate((w - bw * scale) / 2f, (h - bh * scale) / 2f)
        }
        canvas.drawBitmap(bitmap, matrix, paint)
    }
}
