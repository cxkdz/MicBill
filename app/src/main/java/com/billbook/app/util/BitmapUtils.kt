package com.billbook.app.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri

/**
 * 大图安全解码。
 *
 * 直接 decodeStream 一张 4000x3000 的照片约占 48MB(ARGB_8888)，低端机极易 OOM。
 * 这里先只读取边界尺寸，再按目标面积计算 2 的幂采样率，最后真正解码。
 */
object BitmapUtils {

    /**
     * 纯数学运算，不引用任何 Android 类，可由 JVM 单元测试直接覆盖。
     * 取「源像素数 / 目标像素数」的平方根并向下取 2 的幂，避免解出远大于屏幕的位图。
     */
    fun calculateInSampleSize(srcWidth: Int, srcHeight: Int, reqWidth: Int, reqHeight: Int): Int {
        if (srcWidth <= 0 || srcHeight <= 0 || reqWidth <= 0 || reqHeight <= 0) return 1
        val srcPixels = srcWidth.toLong() * srcHeight.toLong()
        val reqPixels = reqWidth.toLong() * reqHeight.toLong()
        val ratio = srcPixels.toDouble() / reqPixels.toDouble()
        val maxSample = kotlin.math.sqrt(ratio).toLong()
        var sample = 1L
        while (sample * 2 <= maxSample) {
            sample *= 2
        }
        return sample.toInt().coerceAtLeast(1)
    }

    /**
     * 从 content:// Uri 解码出按屏幕尺寸采样后的 Bitmap，失败返回 null。
     * 读边界与真正解码各开一次流，InputStream 由 use 自动关闭。
     */
    fun decodeSampledUri(context: Context, uri: Uri, reqWidth: Int, reqHeight: Int): Bitmap? = try {
        val resolver = context.applicationContext.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            null
        } else {
            val options = BitmapFactory.Options().apply {
                inSampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, reqWidth, reqHeight)
            }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
        }
    } catch (_: Exception) {
        null
    }

    /** 从 drawable 资源采样解码（预设前景图用），与 Uri 版本共用同一套采样率算法 */
    fun decodeSampledResource(
        res: android.content.res.Resources,
        resId: Int,
        reqWidth: Int,
        reqHeight: Int,
        /** 在计算出的采样率基础上再乘的倍数，用于首次解码失败时降级重试 */
        extraSample: Int = 1
    ): Bitmap? = try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeResource(res, resId, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            null
        } else {
            val options = BitmapFactory.Options().apply {
                inSampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, reqWidth, reqHeight) * extraSample
            }
            BitmapFactory.decodeResource(res, resId, options)
        }
    } catch (_: Exception) {
        null
    }
}
