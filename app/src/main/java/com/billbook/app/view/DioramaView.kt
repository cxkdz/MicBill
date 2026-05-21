package com.billbook.app.view

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import android.net.Uri
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.billbook.app.model.DioramaType
import com.billbook.app.model.ThemePalette

class DioramaView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var palette: ThemePalette? = null
    private var customBitmap: Bitmap? = null
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()

    init {
        setLayerType(LAYER_TYPE_HARDWARE, null)
    }

    fun applyPalette(palette: ThemePalette) {
        this.palette = palette
        customBitmap = null
        invalidate()
    }

    fun applyCustomImage(uri: Uri) {
        try {
            val inputStream = context.contentResolver.openInputStream(uri)
            customBitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()
            invalidate()
        } catch (_: Exception) {
            customBitmap = null
        }
    }


    fun applyDefaultImage(resId: Int) {
        try {
            val drawable = ContextCompat.getDrawable(context, resId)
            if (drawable is android.graphics.drawable.BitmapDrawable) {
                customBitmap = drawable.bitmap
            } else if (drawable != null) {
                val bitmap = android.graphics.Bitmap.createBitmap(
                    drawable.intrinsicWidth.coerceAtLeast(1),
                    drawable.intrinsicHeight.coerceAtLeast(1),
                    android.graphics.Bitmap.Config.ARGB_8888
                )
                val canvas = android.graphics.Canvas(bitmap)
                drawable.setBounds(0, 0, canvas.width, canvas.height)
                drawable.draw(canvas)
                customBitmap = bitmap
            }
            invalidate()
        } catch (_: Exception) {}
    }

        override fun onDraw(canvas: Canvas) {
        val bmp = customBitmap
        if (bmp != null) {
            drawCustomImage(canvas, bmp)
            return
        }
        val p = palette ?: return
        when (p.dioramaType) {
            DioramaType.FOREST -> drawForest(canvas, p)
            DioramaType.SNOW_MOUNTAIN -> drawSnowMountain(canvas, p)
            DioramaType.DESERT -> drawDesert(canvas, p)
            DioramaType.OCEAN -> drawOcean(canvas, p)
            DioramaType.STARRY_SKY -> drawStarrySky(canvas, p)
            DioramaType.CITY -> drawCity(canvas, p)
            DioramaType.CYBERPUNK -> drawCyberpunk(canvas, p)
            DioramaType.MINIMAL_GEOMETRY -> drawMinimalGeometry(canvas, p)
            DioramaType.ANIME -> drawAnime(canvas, p)
            DioramaType.ILLUSTRATION -> drawIllustration(canvas, p)
        }
    }

    private fun drawCustomImage(canvas: Canvas, bitmap: Bitmap) {
        val w = width.toFloat()
        val h = height.toFloat()
        val bw = bitmap.width.toFloat()
        val bh = bitmap.height.toFloat()
        val scale = maxOf(w / bw, h / bh)
        val dx = (w - bw * scale) / 2f
        val dy = (h - bh * scale) / 2f
        val matrix = Matrix()
        matrix.setScale(scale, scale)
        matrix.postTranslate(dx, dy)
        canvas.drawBitmap(bitmap, matrix, paint)
    }

    private fun drawForest(canvas: Canvas, p: ThemePalette) {
        val w = width.toFloat()
        val h = height.toFloat()

        // Sky gradient
        paint.shader = LinearGradient(0f, 0f, 0f, h * 0.6f,
            p.dioramaSkyTop, p.dioramaSkyBottom, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w, h * 0.6f, paint)

        // Ground
        paint.shader = null
        paint.color = p.dioramaGround
        canvas.drawRect(0f, h * 0.55f, w, h, paint)

        // Grass detail
        paint.color = Color.parseColor("#4CAF50")
        for (i in 0..20) {
            val x = w * i / 20f
            canvas.drawCircle(x, h * 0.56f, 8f, paint)
        }

        // Trees
        drawTree(canvas, w * 0.12f, h * 0.32f, h * 0.28f, p.dioramaAccent)
        drawTree(canvas, w * 0.25f, h * 0.38f, h * 0.22f, p.dioramaAccent)
        drawTree(canvas, w * 0.72f, h * 0.30f, h * 0.30f, p.dioramaAccent)
        drawTree(canvas, w * 0.88f, h * 0.36f, h * 0.24f, p.dioramaAccent)

        // House
        drawHouse(canvas, w * 0.48f, h * 0.42f, w * 0.08f, h * 0.12f, p.dioramaHighlight)

        // River
        paint.color = Color.parseColor("#64B5F6")
        paint.shader = LinearGradient(0f, h * 0.65f, w, h * 0.72f,
            Color.parseColor("#90CAF9"), Color.parseColor("#42A5F5"), Shader.TileMode.CLAMP)
        path.reset()
        path.moveTo(0f, h * 0.65f)
        path.cubicTo(w * 0.3f, h * 0.62f, w * 0.6f, h * 0.70f, w, h * 0.67f)
        path.lineTo(w, h * 0.73f)
        path.cubicTo(w * 0.6f, h * 0.76f, w * 0.3f, h * 0.68f, 0f, h * 0.71f)
        path.close()
        canvas.drawPath(path, paint)

        // Soft light
        drawSoftLight(canvas, w, h, p)
    }

    private fun drawTree(canvas: Canvas, x: Float, y: Float, treeH: Float, color: Int) {
        val trunkW = treeH * 0.08f
        // Trunk
        paint.shader = null
        paint.color = Color.parseColor("#795548")
        canvas.drawRect(x - trunkW, y, x + trunkW, y + treeH * 0.3f, paint)
        // Canopy layers
        paint.color = color
        for (i in 0..2) {
            val layerW = treeH * (0.4f - i * 0.08f)
            val layerY = y - treeH * i * 0.25f
            path.reset()
            path.moveTo(x, layerY - treeH * 0.25f)
            path.lineTo(x - layerW, layerY)
            path.lineTo(x + layerW, layerY)
            path.close()
            canvas.drawPath(path, paint)
        }
    }

    private fun drawHouse(canvas: Canvas, x: Float, y: Float, w: Float, h: Float, color: Int) {
        // Wall
        paint.shader = null
        paint.color = color
        canvas.drawRect(x - w / 2, y, x + w / 2, y + h, paint)
        // Roof
        paint.color = Color.parseColor("#8D6E63")
        path.reset()
        path.moveTo(x, y - h * 0.5f)
        path.lineTo(x - w * 0.7f, y)
        path.lineTo(x + w * 0.7f, y)
        path.close()
        canvas.drawPath(path, paint)
        // Window
        paint.color = Color.parseColor("#FFF9C4")
        val winSize = w * 0.2f
        canvas.drawRect(x - winSize, y + h * 0.3f, x + winSize, y + h * 0.6f, paint)
    }

    private fun drawSnowMountain(canvas: Canvas, p: ThemePalette) {
        val w = width.toFloat()
        val h = height.toFloat()

        // Sky
        paint.shader = LinearGradient(0f, 0f, 0f, h * 0.5f,
            p.dioramaSkyTop, p.dioramaSkyBottom, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w, h * 0.5f, paint)

        // Snow ground
        paint.shader = null
        paint.color = p.dioramaGround
        canvas.drawRect(0f, h * 0.5f, w, h, paint)

        // Mountains
        drawMountain(canvas, w * 0.2f, h * 0.15f, w * 0.45f, h * 0.45f, p.dioramaAccent)
        drawMountain(canvas, w * 0.6f, h * 0.20f, w * 0.50f, h * 0.40f, Color.parseColor("#78909C"))
        drawMountain(canvas, w * 0.4f, h * 0.10f, w * 0.35f, h * 0.50f, p.dioramaHighlight)

        // Snow caps
        paint.color = Color.WHITE
        drawSnowCap(canvas, w * 0.2f, h * 0.15f, w * 0.12f, h * 0.08f)
        drawSnowCap(canvas, w * 0.6f, h * 0.20f, w * 0.14f, h * 0.07f)
        drawSnowCap(canvas, w * 0.4f, h * 0.10f, w * 0.10f, h * 0.09f)

        drawSoftLight(canvas, w, h, p)
    }

    private fun drawMountain(canvas: Canvas, x: Float, peakY: Float, baseW: Float, height: Float, color: Int) {
        paint.shader = null
        paint.color = color
        path.reset()
        path.moveTo(x, peakY)
        path.lineTo(x - baseW / 2, peakY + height)
        path.lineTo(x + baseW / 2, peakY + height)
        path.close()
        canvas.drawPath(path, paint)
    }

    private fun drawSnowCap(canvas: Canvas, x: Float, peakY: Float, capW: Float, capH: Float) {
        path.reset()
        path.moveTo(x, peakY)
        path.lineTo(x - capW / 2, peakY + capH)
        path.lineTo(x + capW / 2, peakY + capH)
        path.close()
        canvas.drawPath(path, paint)
    }

    private fun drawDesert(canvas: Canvas, p: ThemePalette) {
        val w = width.toFloat()
        val h = height.toFloat()

        // Warm sky
        paint.shader = LinearGradient(0f, 0f, 0f, h * 0.55f,
            p.dioramaSkyTop, p.dioramaSkyBottom, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w, h * 0.55f, paint)

        // Sand ground
        paint.shader = null
        paint.color = p.dioramaGround
        canvas.drawRect(0f, h * 0.5f, w, h, paint)

        // Dunes
        paint.color = p.dioramaAccent
        path.reset()
        path.moveTo(0f, h * 0.6f)
        path.quadTo(w * 0.25f, h * 0.48f, w * 0.5f, h * 0.58f)
        path.quadTo(w * 0.75f, h * 0.52f, w, h * 0.56f)
        path.lineTo(w, h)
        path.lineTo(0f, h)
        path.close()
        canvas.drawPath(path, paint)

        // Sun
        paint.shader = RadialGradient(
            w * 0.8f, h * 0.15f, w * 0.08f,
            intArrayOf(Color.parseColor("#FFF176"), Color.parseColor("#FFEE58"), Color.TRANSPARENT),
            floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP
        )
        canvas.drawCircle(w * 0.8f, h * 0.15f, w * 0.1f, paint)

        drawSoftLight(canvas, w, h, p)
    }

    private fun drawOcean(canvas: Canvas, p: ThemePalette) {
        val w = width.toFloat()
        val h = height.toFloat()

        // Sky
        paint.shader = LinearGradient(0f, 0f, 0f, h * 0.45f,
            p.dioramaSkyTop, p.dioramaSkyBottom, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w, h * 0.45f, paint)

        // Ocean
        paint.shader = LinearGradient(0f, h * 0.4f, 0f, h,
            p.dioramaAccent, p.dioramaGround, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, h * 0.4f, w, h, paint)

        // Waves
        paint.color = Color.parseColor("#4FC3F7")
        paint.shader = null
        for (i in 0..4) {
            val waveY = h * 0.5f + i * h * 0.08f
            path.reset()
            path.moveTo(0f, waveY)
            for (j in 0..8) {
                val px = w * j / 8f
                val py = waveY + if (j % 2 == 0) -6f else 6f
                path.lineTo(px, py)
            }
            path.lineTo(w, waveY + 3f)
            path.lineTo(0f, waveY + 3f)
            path.close()
            canvas.drawPath(path, paint)
        }

        // Horizon line
        paint.color = Color.parseColor("#B3E5FC")
        canvas.drawRect(0f, h * 0.42f, w, h * 0.43f, paint)

        // Moon/sun
        paint.shader = RadialGradient(
            w * 0.75f, h * 0.15f, w * 0.06f,
            intArrayOf(Color.parseColor("#FFF9C4"), Color.TRANSPARENT),
            floatArrayOf(0f, 1f), Shader.TileMode.CLAMP
        )
        canvas.drawCircle(w * 0.75f, h * 0.15f, w * 0.08f, paint)
    }

    private fun drawStarrySky(canvas: Canvas, p: ThemePalette) {
        val w = width.toFloat()
        val h = height.toFloat()

        // Dark sky gradient
        paint.shader = LinearGradient(0f, 0f, 0f, h,
            p.dioramaSkyTop, p.dioramaSkyBottom, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w, h, paint)

        // Nebula glow
        paint.shader = RadialGradient(
            w * 0.3f, h * 0.3f, w * 0.4f,
            intArrayOf(Color.argb(30, 156, 39, 176), Color.TRANSPARENT),
            floatArrayOf(0f, 1f), Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, w, h, paint)

        paint.shader = RadialGradient(
            w * 0.7f, h * 0.6f, w * 0.3f,
            intArrayOf(Color.argb(20, 33, 150, 243), Color.TRANSPARENT),
            floatArrayOf(0f, 1f), Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, w, h, paint)

        // Stars
        paint.shader = null
        paint.color = Color.WHITE
        val starPositions = floatArrayOf(
            0.1f, 0.05f, 0.2f, 0.12f, 0.35f, 0.08f, 0.5f, 0.15f,
            0.65f, 0.06f, 0.8f, 0.18f, 0.9f, 0.1f, 0.15f, 0.22f,
            0.4f, 0.25f, 0.55f, 0.2f, 0.75f, 0.28f, 0.92f, 0.22f,
            0.05f, 0.35f, 0.3f, 0.32f, 0.6f, 0.38f, 0.85f, 0.35f
        )
        for (i in starPositions.indices step 2) {
            val sx = starPositions[i] * w
            val sy = starPositions[i + 1] * h
            val size = 1.5f + (i % 3)
            canvas.drawCircle(sx, sy, size, paint)
        }

        // Moon
        paint.shader = RadialGradient(
            w * 0.8f, h * 0.12f, w * 0.05f,
            intArrayOf(Color.parseColor("#FFFDE7"), Color.parseColor("#FFF9C4"), Color.TRANSPARENT),
            floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP
        )
        canvas.drawCircle(w * 0.8f, h * 0.12f, w * 0.06f, paint)
    }

    private fun drawCity(canvas: Canvas, p: ThemePalette) {
        val w = width.toFloat()
        val h = height.toFloat()

        // Dark sky
        paint.shader = LinearGradient(0f, 0f, 0f, h * 0.5f,
            p.dioramaSkyTop, p.dioramaSkyBottom, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w, h * 0.5f, paint)

        // Ground
        paint.shader = null
        paint.color = p.dioramaGround
        canvas.drawRect(0f, h * 0.7f, w, h, paint)

        // Buildings
        paint.color = p.dioramaAccent
        val buildings = floatArrayOf(
            0.05f, 0.35f, 0.12f, 0.7f,
            0.14f, 0.28f, 0.22f, 0.7f,
            0.24f, 0.40f, 0.32f, 0.7f,
            0.34f, 0.22f, 0.42f, 0.7f,
            0.44f, 0.32f, 0.52f, 0.7f,
            0.54f, 0.38f, 0.62f, 0.7f,
            0.64f, 0.25f, 0.72f, 0.7f,
            0.74f, 0.42f, 0.82f, 0.7f,
            0.84f, 0.30f, 0.92f, 0.7f
        )
        for (i in buildings.indices step 4) {
            canvas.drawRect(
                buildings[i] * w, buildings[i + 1] * h,
                buildings[i + 2] * w, buildings[i + 3] * h, paint
            )
        }

        // Windows (small dots)
        paint.color = p.dioramaHighlight
        for (i in buildings.indices step 4) {
            val bx = buildings[i] * w
            val by = buildings[i + 1] * h
            val bw = (buildings[i + 2] - buildings[i]) * w
            val bh = (buildings[i + 3] - buildings[i + 1]) * h
            for (row in 0..3) {
                for (col in 0..1) {
                    val wx = bx + bw * (0.25f + col * 0.4f)
                    val wy = by + bh * (0.15f + row * 0.2f)
                    canvas.drawRect(wx, wy, wx + 3f, wy + 3f, paint)
                }
            }
        }

        drawSoftLight(canvas, w, h, p)
    }

    private fun drawCyberpunk(canvas: Canvas, p: ThemePalette) {
        val w = width.toFloat()
        val h = height.toFloat()

        // Dark sky
        paint.shader = LinearGradient(0f, 0f, 0f, h,
            p.dioramaSkyTop, p.dioramaSkyBottom, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w, h, paint)

        // Neon grid ground
        paint.color = Color.parseColor("#1A0033")
        canvas.drawRect(0f, h * 0.6f, w, h, paint)

        // Grid lines
        paint.color = Color.argb(40, 255, 0, 255)
        for (i in 0..10) {
            val gy = h * 0.6f + (h * 0.4f) * i / 10f
            canvas.drawRect(0f, gy, w, gy + 1f, paint)
        }
        for (i in 0..12) {
            val gx = w * i / 12f
            canvas.drawRect(gx, h * 0.6f, gx + 1f, h, paint)
        }

        // Buildings
        paint.color = Color.parseColor("#0D001A")
        val blds = floatArrayOf(
            0.08f, 0.2f, 0.18f, 0.6f,
            0.2f, 0.15f, 0.32f, 0.6f,
            0.35f, 0.25f, 0.45f, 0.6f,
            0.48f, 0.1f, 0.58f, 0.6f,
            0.6f, 0.3f, 0.7f, 0.6f,
            0.72f, 0.18f, 0.82f, 0.6f,
            0.84f, 0.28f, 0.95f, 0.6f
        )
        for (i in blds.indices step 4) {
            canvas.drawRect(blds[i] * w, blds[i + 1] * h, blds[i + 2] * w, blds[i + 3] * h, paint)
        }

        // Neon accents
        paint.color = p.dioramaAccent
        paint.strokeWidth = 2f
        for (i in blds.indices step 4) {
            val bx = blds[i] * w
            val by = blds[i + 1] * h
            val bw = (blds[i + 2] - blds[i]) * w
            canvas.drawLine(bx, by, bx + bw, by, paint)
        }
        paint.color = p.dioramaHighlight
        for (i in blds.indices step 4) {
            val bx = blds[i] * w + 2f
            val by = blds[i + 3] * h - 2f
            canvas.drawLine(bx, by, bx + (blds[i + 2] - blds[i]) * w - 4f, by, paint)
        }
        paint.strokeWidth = 0f

        // Neon glow
        paint.shader = RadialGradient(
            w * 0.5f, h * 0.3f, w * 0.3f,
            intArrayOf(Color.argb(30, 255, 0, 255), Color.TRANSPARENT),
            floatArrayOf(0f, 1f), Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, w, h, paint)
    }

    private fun drawMinimalGeometry(canvas: Canvas, p: ThemePalette) {
        val w = width.toFloat()
        val h = height.toFloat()

        // Clean background
        paint.shader = LinearGradient(0f, 0f, w, h,
            p.dioramaSkyTop, p.dioramaSkyBottom, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w, h, paint)

        // Large circle
        paint.shader = null
        paint.color = p.dioramaAccent
        canvas.drawCircle(w * 0.7f, h * 0.3f, w * 0.25f, paint)

        // Rectangle
        paint.color = p.dioramaGround
        canvas.drawRect(w * 0.1f, h * 0.5f, w * 0.45f, h * 0.85f, paint)

        // Small circle
        paint.color = p.dioramaHighlight
        canvas.drawCircle(w * 0.3f, h * 0.25f, w * 0.08f, paint)

        // Line
        paint.color = p.dioramaAccent
        paint.strokeWidth = 2f
        canvas.drawLine(w * 0.5f, h * 0.6f, w * 0.9f, h * 0.4f, paint)
        paint.strokeWidth = 0f

        drawSoftLight(canvas, w, h, p)
    }

    private fun drawAnime(canvas: Canvas, p: ThemePalette) {
        val w = width.toFloat()
        val h = height.toFloat()

        // Soft pastel sky
        paint.shader = LinearGradient(0f, 0f, 0f, h * 0.55f,
            p.dioramaSkyTop, p.dioramaSkyBottom, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w, h * 0.55f, paint)

        // Green hills
        paint.shader = null
        paint.color = p.dioramaGround
        path.reset()
        path.moveTo(0f, h * 0.6f)
        path.quadTo(w * 0.3f, h * 0.48f, w * 0.5f, h * 0.55f)
        path.quadTo(w * 0.7f, h * 0.50f, w, h * 0.58f)
        path.lineTo(w, h)
        path.lineTo(0f, h)
        path.close()
        canvas.drawPath(path, paint)

        // Cherry blossom dots
        paint.color = p.dioramaAccent
        val blossomPositions = floatArrayOf(
            0.1f, 0.15f, 0.2f, 0.22f, 0.3f, 0.18f, 0.4f, 0.25f,
            0.55f, 0.12f, 0.65f, 0.2f, 0.75f, 0.16f, 0.85f, 0.24f,
            0.15f, 0.3f, 0.35f, 0.32f, 0.6f, 0.28f, 0.8f, 0.35f
        )
        for (i in blossomPositions.indices step 2) {
            canvas.drawCircle(blossomPositions[i] * w, blossomPositions[i + 1] * h, 3f, paint)
        }

        // Warm light
        paint.shader = RadialGradient(
            w * 0.7f, h * 0.15f, w * 0.35f,
            intArrayOf(Color.argb(35, 255, 235, 59), Color.TRANSPARENT),
            floatArrayOf(0f, 1f), Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, w, h, paint)
    }

    private fun drawIllustration(canvas: Canvas, p: ThemePalette) {
        val w = width.toFloat()
        val h = height.toFloat()

        // Soft gradient background
        paint.shader = LinearGradient(0f, 0f, 0f, h,
            p.dioramaSkyTop, p.dioramaSkyBottom, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w, h, paint)

        // Organic shapes
        paint.shader = null
        paint.color = p.dioramaAccent
        canvas.drawCircle(w * 0.2f, h * 0.3f, w * 0.15f, paint)
        canvas.drawCircle(w * 0.7f, h * 0.5f, w * 0.12f, paint)

        paint.color = p.dioramaGround
        path.reset()
        path.moveTo(0f, h * 0.7f)
        path.cubicTo(w * 0.3f, h * 0.55f, w * 0.6f, h * 0.75f, w, h * 0.65f)
        path.lineTo(w, h)
        path.lineTo(0f, h)
        path.close()
        canvas.drawPath(path, paint)

        // Highlight shape
        paint.color = p.dioramaHighlight
        canvas.drawCircle(w * 0.85f, h * 0.2f, w * 0.06f, paint)

        drawSoftLight(canvas, w, h, p)
    }

    private fun drawSoftLight(canvas: Canvas, w: Float, h: Float, p: ThemePalette) {
        paint.shader = RadialGradient(
            w * 0.75f, h * 0.1f, w * 0.5f,
            intArrayOf(Color.argb(25, 255, 255, 220), Color.TRANSPARENT),
            floatArrayOf(0f, 1f), Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, w, h, paint)
        paint.shader = null
    }
}
