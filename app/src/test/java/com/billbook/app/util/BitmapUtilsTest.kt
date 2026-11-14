package com.billbook.app.util

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 纯 JVM 单元测试：不需要 Android 设备或模拟器。
 * 只覆盖不引用 Android 类的采样率算法，可在 IDE 里直接 Run。
 */
class BitmapUtilsTest {

    @Test
    fun sameSizeKeepsSampleAtOne() {
        assertEquals(1, BitmapUtils.calculateInSampleSize(1080, 1920, 1080, 1920))
    }

    @Test
    fun phonePhotoDownToScreenUsesSampleTwo() {
        assertEquals(2, BitmapUtils.calculateInSampleSize(4000, 3000, 1080, 1920))
    }

    @Test
    fun doubleResolutionUsesSampleTwo() {
        assertEquals(2, BitmapUtils.calculateInSampleSize(2160, 3840, 1080, 1920))
    }

    @Test
    fun hugeImageKeepsPowerOfTwo() {
        assertEquals(8, BitmapUtils.calculateInSampleSize(8000, 8000, 1000, 1000))
    }

    @Test
    fun invalidDimensionsFallBackToOne() {
        assertEquals(1, BitmapUtils.calculateInSampleSize(0, 0, 1080, 1920))
        assertEquals(1, BitmapUtils.calculateInSampleSize(1080, 1920, 0, 0))
        assertEquals(1, BitmapUtils.calculateInSampleSize(-1, 100, 1080, 1920))
    }
}
