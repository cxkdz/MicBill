package com.billbook.app.autobill

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.os.Build
import android.os.Handler
import android.os.Looper
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Optional local recognition of one window. The caller supplies consent,
 * verified payment-window evidence and a live session check before invoking it.
 * Images stay in memory; this helper never saves or uploads screenshots.
 */
class AutoBillPaymentOcr : AutoCloseable {
    sealed class Result {
        data class Success(val texts: List<String>) : Result()
        data class Failure(val reason: String) : Result()
    }

    private val handler = Handler(Looper.getMainLooper())
    private val callbackExecutor = Executor { handler.post(it) }
    private val busy = AtomicBoolean(false)
    private val recognizerLock = Any()
    @Volatile private var closed = false
    private var recognizer: TextRecognizer? = null

    @android.annotation.TargetApi(34)
    fun capture(
        service: AccessibilityService,
        windowId: Int,
        stillCurrent: () -> Boolean,
        callback: (Result) -> Unit
    ) {
        if (!isCurrent(stillCurrent)) return
        if (Build.VERSION.SDK_INT < 34) {
            deliver(stillCurrent, callback, Result.Failure("本机系统不支持单独识别支付窗口，请手动记账"))
            return
        }
        if (windowId < 0) {
            deliver(stillCurrent, callback, Result.Failure("支付窗口已失效，请重新打开转账详情"))
            return
        }
        if (!busy.compareAndSet(false, true)) {
            deliver(stillCurrent, callback, Result.Failure("正在识别支付页面，请稍候"))
            return
        }
        try {
            // Deliberately never fall back to a full-display screenshot.
            service.takeScreenshotOfWindow(windowId, callbackExecutor,
                object : AccessibilityService.TakeScreenshotCallback {
                    override fun onSuccess(screenshot: AccessibilityService.ScreenshotResult) {
                        val bitmap = try {
                            copyScreenshot(screenshot, stillCurrent)
                        } catch (_: Exception) {
                            null
                        } catch (_: OutOfMemoryError) {
                            null
                        }
                        if (bitmap == null) {
                            busy.set(false)
                            deliver(stillCurrent, callback,
                                Result.Failure("支付窗口图像暂不可读取，请重新打开转账详情"))
                            return
                        }
                        processBitmap(bitmap, stillCurrent, callback)
                    }

                    override fun onFailure(errorCode: Int) {
                        busy.set(false)
                        deliver(stillCurrent, callback, Result.Failure(screenshotFailure(errorCode)))
                    }
                })
        } catch (_: SecurityException) {
            busy.set(false)
            deliver(stillCurrent, callback, Result.Failure("系统未授予支付窗口图像识别权限，请重新连接页面识别"))
        } catch (_: Exception) {
            busy.set(false)
            deliver(stillCurrent, callback, Result.Failure("支付窗口图像暂不可读取，请重新打开转账详情"))
        }
    }

    /**
     * Shares inference with device probes. Takes ownership of bitmap, retaining
     * it until ML Kit finishes and recycling it even when the session is stale.
     */
    fun recognize(
        bitmap: Bitmap,
        stillCurrent: () -> Boolean,
        callback: (Result) -> Unit
    ) {
        if (!isCurrent(stillCurrent)) {
            bitmap.recycle()
            return
        }
        if (!busy.compareAndSet(false, true)) {
            bitmap.recycle()
            deliver(stillCurrent, callback, Result.Failure("正在识别支付页面，请稍候"))
            return
        }
        processBitmap(bitmap, stillCurrent, callback)
    }

    @android.annotation.TargetApi(34)
    private fun copyScreenshot(
        screenshot: AccessibilityService.ScreenshotResult,
        stillCurrent: () -> Boolean
    ): Bitmap? {
        val buffer = screenshot.hardwareBuffer
        try {
            if (!isCurrent(stillCurrent)) return null
            val hardware = Bitmap.wrapHardwareBuffer(buffer, screenshot.colorSpace) ?: return null
            return try {
                hardware.copy(Bitmap.Config.ARGB_8888, false)
            } finally {
                hardware.recycle()
            }
        } finally {
            buffer.close()
        }
    }

    private fun processBitmap(
        bitmap: Bitmap,
        stillCurrent: () -> Boolean,
        callback: (Result) -> Unit
    ) {
        if (!isCurrent(stillCurrent)) {
            bitmap.recycle()
            busy.set(false)
            return
        }
        val task = try {
            val client = synchronized(recognizerLock) {
                check(!closed)
                recognizer ?: TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
                    .also { recognizer = it }
            }
            client.process(InputImage.fromBitmap(bitmap, 0))
        } catch (_: Exception) {
            bitmap.recycle()
            busy.set(false)
            deliver(stillCurrent, callback, Result.Failure("本地文字识别暂不可用，请稍后重试或手动记账"))
            return
        } catch (_: OutOfMemoryError) {
            bitmap.recycle()
            busy.set(false)
            deliver(stillCurrent, callback, Result.Failure("本机内存不足，暂时无法识别支付页面"))
            return
        }
        task.addOnCompleteListener(callbackExecutor) { completed ->
            val result = try {
                if (completed.isSuccessful) {
                    Result.Success(readingOrder(completed.result))
                } else {
                    Result.Failure("本地文字识别失败，请停留在转账详情后重试")
                }
            } finally {
                bitmap.recycle()
                busy.set(false)
            }
            deliver(stillCurrent, callback, result)
        }
    }

    private fun readingOrder(text: Text): List<String> = text.textBlocks
        .flatMap { it.lines }
        .sortedWith(compareBy<Text.Line> { it.boundingBox?.top ?: Int.MAX_VALUE }
            .thenBy { it.boundingBox?.left ?: Int.MAX_VALUE })
        .map { it.text.trim().take(300) }
        .filter { it.isNotEmpty() }
        .take(400)

    private fun isCurrent(stillCurrent: () -> Boolean): Boolean =
        !closed && runCatching(stillCurrent).getOrDefault(false)

    private fun deliver(stillCurrent: () -> Boolean, callback: (Result) -> Unit, result: Result) {
        callbackExecutor.execute {
            if (isCurrent(stillCurrent)) callback(result)
        }
    }

    @android.annotation.TargetApi(34)
    private fun screenshotFailure(errorCode: Int): String = when (errorCode) {
        AccessibilityService.ERROR_TAKE_SCREENSHOT_SECURE_WINDOW ->
            "微信当前支付窗口受保护，系统不允许识别，请手动记账"
        AccessibilityService.ERROR_TAKE_SCREENSHOT_NO_ACCESSIBILITY_ACCESS ->
            "支付窗口图像识别权限未生效，请重新连接页面识别"
        AccessibilityService.ERROR_TAKE_SCREENSHOT_INVALID_WINDOW ->
            "支付窗口已切换，请重新打开转账详情"
        AccessibilityService.ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT ->
            "支付窗口识别间隔过短，请稍候重试"
        else -> "系统暂时无法读取支付窗口图像，请重新打开转账详情"
    }

    override fun close() {
        synchronized(recognizerLock) {
            closed = true
            recognizer?.close()
            recognizer = null
        }
    }
}
