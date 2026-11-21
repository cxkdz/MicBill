package com.billbook.app.autobill

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Activity
import android.app.Instrumentation
import android.app.UiAutomation
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Dependency-free device probe, run with am instrument -w against this class. */
class AutoBillCaptureProbeInstrumentation : Instrumentation() {
    private var receiptImage: String? = null
    companion object {
        private const val TAG = "AutoBillCaptureProbe"
        private const val SHARED_TRANSFER = "com.tencent.mm.plugin.remittance.ui.RemittanceUI"
    }

    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        receiptImage = arguments?.getString("receipt_image")
        start()
    }

    override fun onStart() {
        super.onStart()
        val result = Bundle()
        var resultCode = Activity.RESULT_CANCELED
        val automation = uiAutomation
        val originalInfo = automation.serviceInfo
        try {
            val baselineInfo = automation.serviceInfo.apply {
                flags = flags and AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS.inv()
                eventTypes = AccessibilityEvent.TYPES_ALL_MASK
                packageNames = null
            }
            automation.serviceInfo = baselineInfo
            launch(AutoBillCaptureProbeActivity.ACCEPTED)
            val baseline = awaitSnapshot(automation, AutoBillCaptureProbeActivity.ACCEPTED)
            report("Baseline flags=${baselineInfo.flags}: ${baseline.stats}; " +
                "acceptedText=${baseline.texts.contains("对方已收款")}, " +
                "amountText=${baseline.texts.contains("￥18.00")}")

            val configured = AutoBillAndroidScreenText.configure(automation.serviceInfo).apply {
                // UiAutomation observes this test-only Activity, outside payment packages.
                packageNames = null
                eventTypes = AccessibilityEvent.TYPES_ALL_MASK
            }
            check(configured.flags and AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS != 0) {
                "Production configuration did not include nonimportant views"
            }
            automation.serviceInfo = configured
            val accepted = awaitSnapshot(automation, AutoBillCaptureProbeActivity.ACCEPTED,
                requiredTexts = setOf("对方已收款", "￥18.00"))
            report("Configured flags=${configured.flags}: ${accepted.stats}; " +
                "acceptedText=${accepted.texts.contains("对方已收款")}, " +
                "amountText=${accepted.texts.contains("￥18.00")}")
            checkExcludedSubtrees(accepted)
            val acceptedInspection = AutoBillScreenParser.inspect(
                AutoBillParser.WECHAT_PKG, SHARED_TRANSFER, accepted.texts)
            val capture = checkNotNull(acceptedInspection.capture) { acceptedInspection.reason }
            check(capture.pay.type == AutoBillParser.TYPE_EXPENSE) { "Accepted sender receipt was not expense" }
            check(capture.pay.amount == 18.0) { "Receipt amount was not 18.00" }
            check(!capture.needsDirection) { "Accepted sender receipt lost its direction" }
            report("PASS completed receipt: expense 18.00; editable/password subtrees excluded")

            launch(AutoBillCaptureProbeActivity.PENDING)
            val pending = awaitSnapshot(automation, AutoBillCaptureProbeActivity.PENDING,
                requiredTexts = setOf("待收款", "￥18.00"))
            checkExcludedSubtrees(pending)
            val pendingInspection = AutoBillScreenParser.inspect(
                AutoBillParser.WECHAT_PKG, SHARED_TRANSFER, pending.texts)
            check(pendingInspection.capture == null) { "Pending transfer incorrectly produced a capture" }
            report("PASS pending receipt rejected: ${pendingInspection.reason}; ${pending.stats}")

            val ocrCompleted = recognizeReceipt("你已收款，资金已存入零钱")
            val ocrInspection = AutoBillScreenParser.inspectOcr(AutoBillParser.WECHAT_PKG,
                "com.tencent.mm.plugin.remittance.ui.RemittanceDetailUI", ocrCompleted)
            val ocrCapture = checkNotNull(ocrInspection.capture) {
                "Completed bitmap did not parse after Chinese OCR: ${ocrInspection.reason}; texts=$ocrCompleted"
            }
            check(ocrCapture.pay.type == AutoBillParser.TYPE_INCOME) { "OCR receipt was not income" }
            check(ocrCapture.pay.amount == 18.0) { "OCR receipt amount was not 18.00" }
            report("PASS bundled Chinese bitmap OCR: completed income 18.00")

            val ocrPending = recognizeReceipt("待收款")
            val ocrPendingInspection = AutoBillScreenParser.inspectOcr(AutoBillParser.WECHAT_PKG,
                "com.tencent.mm.plugin.remittance.ui.RemittanceDetailUI", ocrPending)
            check(ocrPendingInspection.capture == null) { "Pending bitmap incorrectly produced a capture" }
            check(ocrPending.any { it.contains("待收款") }) { "Pending bitmap's state was not recognized" }
            report("PASS bundled Chinese bitmap OCR: pending transfer rejected")

            receiptImage?.let { path ->
                val bitmap = checkNotNull(BitmapFactory.decodeFile(path)) { "Receipt fixture image unavailable" }
                val texts = recognizeBitmap(bitmap)
                // Only the explicitly supplied local test fixture is printed here.
                report("Local reference receipt OCR: " + texts.joinToString(" | "))
                val reference = AutoBillScreenParser.inspectOcr(AutoBillParser.WECHAT_PKG,
                    "com.tencent.mm.plugin.remittance.ui.RemittanceDetailUI", texts)
                val parsed = checkNotNull(reference.capture) { reference.reason }
                check(parsed.pay.type == AutoBillParser.TYPE_INCOME && parsed.pay.amount == 1.0) {
                    "The supplied one-yuan income reference was parsed incorrectly"
                }
                report("PASS supplied WeChat receipt image: income 1.00")
            }

            resultCode = Activity.RESULT_OK
            val testCount = if (receiptImage != null) 6 else 5
            result.putInt("tests", testCount)
            result.putString("stream", "\nPASS AutoBill accessibility and bitmap OCR probe (" + testCount + " checks)\n")
        } catch (error: Throwable) {
            Log.e(TAG, "Capture probe failed", error)
            result.putString("stream", "\nFAIL AutoBill accessibility capture probe\n" + Log.getStackTraceString(error))
        } finally {
            runCatching { launch(AutoBillCaptureProbeActivity.FINISH) }
            runCatching { automation.serviceInfo = originalInfo }
        }
        finish(resultCode, result)
    }

    /** Raster fixture exercises the bundled model without network or a readable node tree. */
    private fun recognizeReceipt(status: String): List<String> {
        val bitmap = Bitmap.createBitmap(1_600, 700, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.WHITE)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLACK
                textSize = 72f
            }
            canvas.drawText("转账详情", 70f, 140f, paint)
            canvas.drawText(status, 70f, 300f, paint)
            canvas.drawText("￥18.00", 70f, 470f, paint)
        return recognizeBitmap(bitmap)
    }

    private fun recognizeBitmap(bitmap: Bitmap): List<String> {
        val ocr = AutoBillPaymentOcr()
        val latch = CountDownLatch(1)
        var outcome: AutoBillPaymentOcr.Result? = null
        try {
            // The production helper takes ownership and releases the bitmap once
            // its asynchronous inference completes. The latch only waits on the
            // instrumentation thread; recognition itself stays asynchronous.
            ocr.recognize(bitmap, { true }) { result -> outcome = result; latch.countDown() }
            check(latch.await(40L, TimeUnit.SECONDS)) { "Production OCR callback timed out" }
            return when (val result = checkNotNull(outcome)) {
                is AutoBillPaymentOcr.Result.Success -> result.texts
                is AutoBillPaymentOcr.Result.Failure -> error(result.reason)
            }
        } finally {
            ocr.close()
        }
    }

    private fun checkExcludedSubtrees(snapshot: AutoBillScreenTextCollector.Snapshot) {
        check(snapshot.stats.editableNodes > 0) { "Fixture did not expose an editable node: ${snapshot.stats}" }
        check(snapshot.stats.passwordNodes > 0) { "Fixture did not expose a password node: ${snapshot.stats}" }
        check(snapshot.texts.none {
            it.contains(AutoBillCaptureProbeActivity.EDITABLE_SECRET) ||
                it.contains(AutoBillCaptureProbeActivity.PASSWORD_SECRET) ||
                it.contains("888.00") || it.contains("999.00")
        }) { "Excluded subtree contributed text" }
    }

    private fun launch(mode: String) {
        context.startActivity(Intent(context, AutoBillCaptureProbeActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(AutoBillCaptureProbeActivity.EXTRA_MODE, mode)
        })
    }

    private fun awaitSnapshot(
        automation: UiAutomation,
        mode: String,
        requiredTexts: Set<String> = emptySet()
    ): AutoBillScreenTextCollector.Snapshot {
        val marker = AutoBillCaptureProbeActivity.marker(mode)
        val deadline = SystemClock.uptimeMillis() + 5_000L
        var lastStats: AutoBillScreenTextCollector.Stats? = null
        do {
            val root = automation.rootInActiveWindow
            if (root != null) {
                try {
                    if (root.packageName?.toString() == context.packageName) {
                        val snapshot = AutoBillAndroidScreenText.collect(root)
                        lastStats = snapshot.stats
                        if (marker in snapshot.texts && requiredTexts.all { it in snapshot.texts }) return snapshot
                    }
                } finally {
                    @Suppress("DEPRECATION")
                    root.recycle()
                }
            }
            SystemClock.sleep(100L)
        } while (SystemClock.uptimeMillis() < deadline)
        error("Fixture mode=$mode did not expose expected visible text; lastStats=$lastStats")
    }

    private fun report(message: String) {
        Log.i(TAG, message)
        sendStatus(0, Bundle().apply { putString("stream", "$message\n") })
    }
}
