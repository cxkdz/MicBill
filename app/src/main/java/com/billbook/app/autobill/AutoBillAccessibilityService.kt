package com.billbook.app.autobill

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.content.Intent
import android.content.ComponentName
import android.os.Handler
import android.os.Build
import android.os.Looper
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.billbook.app.util.AppExecutors

/** Opt-in read-only capture of completed payment result pages, scoped by package and activity. */
class AutoBillAccessibilityService : AccessibilityService() {
    companion object {
        @Volatile var isConnected = false
            private set
    }

    private val handler = Handler(Looper.getMainLooper())
    private val session = AutoBillScreenSession()
    private val activityCache = mutableMapOf<String, Boolean>()
    private val captureGate = AutoBillScreenCaptureGate()
    private val ocrGate = AutoBillScreenOcrGate()
    private val sampling = AutoBillScreenSampling()
    private var paymentOcr: AutoBillPaymentOcr? = null
    private var lastCaptured = ""
    private var sampleAttempt = 0
    private var cacheCleared = false
    private var captureDetails = ""
    private var lastDiagnostic = ""
    private var lastDiagnosticAt = 0L
    private var waitingReported = false
    private var hasPaymentEvidence = false
    private var pageAttempt = 0L
    private var destroyed = false
    private var captureScheduled = false
    private val capturePage = Runnable {
        captureScheduled = false
        runCatching { inspectPaymentPage() }.onFailure {
            val page = session.page
            if (page != null) {
                val retrying = retrySample()
                report(page.packageName, page.activity,
                    if (retrying) "支付页面读取暂时失败，正在重试"
                    else "支付页面读取失败，未生成账单；请查看页面诊断", captureDetails)
            }
            android.util.Log.w("AutoBill", "Payment page unavailable", it)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        serviceInfo = AutoBillAndroidScreenText.configure(serviceInfo)
        isConnected = true
        report(AutoBillParser.WECHAT_PKG, "", "页面识别已连接，请进入已完成付款或已收款的转账详情",
            "读取配置=0x${serviceInfo.flags.toString(16)}")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (!AutoBillStore.isEnabled(this)) {
            clearSession()
            return
        }
        // Window-change events often have no package or class. They can request
        // another read, but cannot establish or replace payment Activity evidence.
        if (event.eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED) {
            if (session.page != null) requestSample()
            return
        }
        val pkg = event.packageName?.toString().orEmpty()
        if (pkg != AutoBillParser.WECHAT_PKG && pkg != AutoBillParser.ALIPAY_PKG) {
            if (pkg.isNotBlank() && event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) clearSession()
            return
        }
        val className = event.className?.toString().orEmpty()
        val knownPayment = AutoBillScreenParser.isPaymentActivity(pkg, className)
        val candidatePayment = AutoBillScreenParser.isCandidatePaymentActivity(pkg, className)
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED || candidatePayment) {
            val previous = session.page
            val realActivity = knownPayment || isActualActivity(pkg, className)
            val current = session.observe(pkg, className, event.windowId, realActivity, System.currentTimeMillis())
            if (current != null) hasPaymentEvidence = true
            if (current != previous) {
                handler.removeCallbacks(capturePage)
                captureScheduled = false
                sampleAttempt = 0
                cacheCleared = false
                captureDetails = ""
                sampling.reset()
                lastCaptured = ""
                captureGate.clear()
                ocrGate.clear()
                if (current != null) pageAttempt++
            }
            if (current == null && realActivity) {
                if (candidatePayment) {
                    report(pkg, className, "正在等待支付窗口，请退出并重新打开转账详情")
                } else if (className.contains(".remittance.") || className.contains(".wallet") ||
                    className.contains("cashier", ignoreCase = true) || className.contains("transfer", ignoreCase = true)) {
                    pageAttempt++
                    report(pkg, className, "当前支付页面尚未适配，请打开已完成的转账详情")
                    waitingReported = true
                }
                return
            }
        }
        val page = session.page
        if (page == null) {
            // Preserve the last useful parsing result when the user returns to chat.
            if (!hasPaymentEvidence && !waitingReported) {
                report(pkg, className, "未收到支付完成页面事件，请退出并重新打开已收款或付款详情")
                waitingReported = true
            }
            return
        }
        if (page.packageName != pkg) return
        // Sample after the current layout frame. Do not restart the delay on every
        // content event: an animated success screen can emit events indefinitely.
        val semanticChanges = AccessibilityEvent.CONTENT_CHANGE_TYPE_TEXT or
            AccessibilityEvent.CONTENT_CHANGE_TYPE_CONTENT_DESCRIPTION or
            if (Build.VERSION.SDK_INT >= 30) AccessibilityEvent.CONTENT_CHANGE_TYPE_STATE_DESCRIPTION else 0
        val returnedToPayment = event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            className == page.activity
        requestSample(returnedToPayment || (event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED &&
            event.contentChangeTypes and semanticChanges != 0))
    }

    private fun inspectPaymentPage() {
        if (!AutoBillStore.isEnabled(this)) {
            clearSession()
            return
        }
        val page = session.page ?: return
        if ((getSystemService(KEYGUARD_SERVICE) as? KeyguardManager)?.isKeyguardLocked == true) {
            // Pause without polling on the lock screen. An unlock/window event
            // can request a new series for the still-verified payment window.
            sampling.reset()
            return
        }
        sampleAttempt++
        val window = matchingRoot()
        if (window == null) {
            report(page.packageName, page.activity, "未读取到当前支付窗口，请停留在已完成的转账详情",
                "读取轮次=$sampleAttempt；读取配置=0x${serviceInfo.flags.toString(16)}")
            retrySample()
            return
        }
        val (root, rootSource) = window
        val pkg = page.packageName
        val activity = page.activity
        val sessionStarted = page.startedAt
        val snapshot = try {
            val collected = AutoBillAndroidScreenText.collect(root)
            val stats = collected.stats
            captureDetails = "窗口=$rootSource；窗口ID=${root.windowId}；根节点=${root.className}" +
                "；读取轮次=$sampleAttempt；节点=${stats.nodesVisited}；可见=${stats.visibleNodes}" +
                "；文本节点=${stats.textBearingNodes}；输入节点=${stats.editableNodes}" +
                "；密码节点=${stats.passwordNodes}；空子节点=${stats.nullChildren}" +
                "；达到上限=${stats.truncated}；读取配置=0x${serviceInfo.flags.toString(16)}"
            collected
        } finally { root.recycle() }
        val inspection = AutoBillScreenParser.inspect(pkg, activity, snapshot.texts)
        val capture = inspection.capture
        if (capture == null) {
            if (snapshot.texts.isEmpty() && !cacheCleared && Build.VERSION.SDK_INT >= 33) {
                // Invalidate an early empty cache once, then reacquire on a later
                // frame. Never combine text from different frames into a payment.
                cacheCleared = true
                runCatching { clearCache() }
            }
            val retrying = retrySample()
            val reason = if (snapshot.texts.isEmpty() && !retrying)
                "多次读取仍无页面文字，未生成账单；请手动补记并查看页面诊断"
                else inspection.reason + if (retrying) "，正在等待页面加载" else ""
            if (snapshot.texts.isEmpty() && !retrying && tryLocalWindowOcr(
                    pkg, activity, sessionStarted, session.page?.windowId ?: -1
                )) return
            report(pkg, activity, reason, captureDetails)
            return
        }
        sampling.succeeded()
        saveCapture(pkg, activity, sessionStarted, capture, captureDetails)
    }

    private fun tryLocalWindowOcr(
        packageName: String, activity: String, sessionStarted: Long, windowId: Int
    ): Boolean {
        if (Build.VERSION.SDK_INT < 34 || destroyed ||
            !AutoBillStore.isScreenOcrEnabled(this) || !AutoBillStore.isEnabled(this) ||
            !isConnected || !hasWindowScreenshotCapability()) return false
        val ticket = ocrGate.begin(SystemClock.elapsedRealtime()) ?: return false
        val expectedPage = session.page ?: run {
            ocrGate.finish(ticket)
            return false
        }
        if (expectedPage.packageName != packageName || expectedPage.activity != activity ||
            expectedPage.startedAt != sessionStarted || expectedPage.windowId != windowId) {
            ocrGate.finish(ticket)
            return false
        }
        val stillCurrent = {
            !destroyed && ocrGate.isCurrent(ticket) &&
                AutoBillStore.isEnabled(this) && AutoBillStore.isScreenOcrEnabled(this) &&
                !((getSystemService(KEYGUARD_SERVICE) as? KeyguardManager)?.isKeyguardLocked ?: true) &&
                session.page == expectedPage && run {
                    val root = matchingRoot()?.first
                    if (root == null) false else {
                        val matches = session.matches(root.packageName?.toString().orEmpty(), root.windowId)
                        root.recycle()
                        matches
                    }
                }
        }
        captureDetails += "；备用方式=当前支付窗口本机识图"
        AutoBillOcrDiagnostics.clear()
        report(packageName, activity, "页面文字不可读取，正在本机识别支付窗口", captureDetails)
        val serviceOcr = paymentOcr ?: AutoBillPaymentOcr().also { paymentOcr = it }
        serviceOcr.capture(this, windowId, stillCurrent) { result ->
            if (!stillCurrent() || !ocrGate.finish(ticket)) return@capture
            when (result) {
                is AutoBillPaymentOcr.Result.Failure ->
                    {
                        report(packageName, activity, result.reason, captureDetails)
                        if (!result.reason.contains("受保护")) requestSample(contentChanged = true)
                    }
                is AutoBillPaymentOcr.Result.Success -> {
                    val inspection = AutoBillScreenParser.inspectOcr(packageName, activity, result.texts)
                    AutoBillOcrDiagnostics.record(packageName, activity, result.texts, inspection.reason)
                    val parsed = inspection.capture
                    if (parsed == null) {
                        report(packageName, activity,
                            "本机识图未能确认完成状态及唯一金额；未生成账单，请手动补记",
                            "窗口文字为空；本机识图=已完成；OCR文字行数=" +
                                result.texts.size + "；原因=" + inspection.reason)
                        requestSample(contentChanged = true)
                    } else {
                        sampling.succeeded()
                        val details = captureDetails + "；识别方式=本机识图；本机OCR文字行数=" +
                            result.texts.size
                        saveCapture(packageName, activity, sessionStarted,
                            parsed.copy(captureMethod = "local_window_ocr"), details)
                    }
                }
            }
        }
        handler.postDelayed({
            if (ocrGate.isCurrent(ticket)) ocrGate.finish(ticket)
        }, 12_000L)
        return true
    }

    private fun hasWindowScreenshotCapability(): Boolean {
        if (Build.VERSION.SDK_INT < 34) return false
        val manager = getSystemService(ACCESSIBILITY_SERVICE)
            as? android.view.accessibility.AccessibilityManager ?: return false
        return runCatching {
            manager.getEnabledAccessibilityServiceList(
                android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_ALL_MASK
            ).any { info ->
                val service = info.resolveInfo.serviceInfo
                service.packageName == packageName && service.name == javaClass.name &&
                    info.capabilities and android.accessibilityservice.AccessibilityServiceInfo
                        .CAPABILITY_CAN_TAKE_SCREENSHOT != 0
            }
        }.getOrDefault(false)
    }

    private fun saveCapture(
        packageName: String, activity: String, sessionStarted: Long,
        capture: AutoBillScreenParser.Capture, details: String
    ) {
        val fingerprint = AutoBillScreenIdentity.identity(packageName, sessionStarted, capture)
        val ticket = captureGate.begin(fingerprint) ?: return
        lastCaptured = fingerprint
        val attempt = pageAttempt
        val ctx = applicationContext
        AppExecutors.io {
            val saved = runCatching { AutoBillStore.recordScreen(ctx, packageName, sessionStarted, capture) }
            AppExecutors.main {
                if (saved.isFailure) {
                    captureGate.failed(ticket)
                    if (session.page?.startedAt == sessionStarted && lastCaptured == fingerprint) lastCaptured = ""
                    if (!destroyed && attempt == pageAttempt) {
                        report(packageName, activity, "识别到交易，但草稿保存失败，请重新打开详情重试", details)
                    }
                    android.util.Log.w("AutoBill", "Payment page capture failed", saved.exceptionOrNull())
                } else {
                    val draft = saved.getOrNull()
                    if (!destroyed && attempt == pageAttempt) {
                        val method = if (capture.captureMethod == "local_window_ocr") "本机识图" else "页面文字"
                        report(packageName, activity, if (draft != null)
                            "已通过" + method + "生成待确认草稿，核对并确认后才计入账本"
                            else "这笔页面记录已处理或已在待确认中，请查看记账记录", details)
                    }
                    if (draft != null) AutoBillPrompter.ask(ctx, draft, preferNotification = true)
                }
            }
        }
    }

    private fun requestSample(contentChanged: Boolean = false) {
        if (captureScheduled) return
        val delay = sampling.request(contentChanged) ?: return
        sampleAttempt = 0
        scheduleSample(delay)
    }

    private fun scheduleSample(delay: Long) {
        if (captureScheduled) return
        captureScheduled = true
        handler.postDelayed(capturePage, delay)
    }

    private fun retrySample(): Boolean {
        if (session.page == null) return false
        val delay = sampling.retry() ?: return false
        scheduleSample(delay)
        return true
    }

    /** Never inspect a background payment window behind another foreground app. */
    private fun matchingRoot(): Pair<AccessibilityNodeInfo, String>? {
        val page = session.page ?: return null
        val active = rootInActiveWindow
        if (active != null) {
            if (session.matches(active.packageName?.toString().orEmpty(), active.windowId)) {
                return active to "当前窗口"
            }
            val samePackage = active.packageName?.toString() == page.packageName
            active.recycle()
            if (!samePackage) {
                // Package-scoped subscriptions may omit the event for leaving
                // WeChat. Pause here so returning to the same window can resume.
                sampling.reset()
                return null
            }
        }
        val paymentWindows = windows
        try {
            for (window in paymentWindows) {
                if (window.id != page.windowId || (!window.isActive && !window.isFocused)) continue
                val root = window.root ?: continue
                if (session.matches(root.packageName?.toString().orEmpty(), root.windowId)) {
                    return root to "支付窗口"
                }
                root.recycle()
            }
        } finally {
            paymentWindows.forEach { it.recycle() }
        }
        return null
    }

    private fun isActualActivity(pkg: String, className: String): Boolean {
        if (!className.startsWith("com.tencent.mm.") && !className.startsWith("com.alipay.")) return false
        val key = "$pkg/$className"
        return activityCache.getOrPut(key) {
            if (activityCache.size >= 100) activityCache.clear()
            runCatching { packageManager.getActivityInfo(ComponentName(pkg, className), 0) }.isSuccess
        }
    }

    private fun report(pkg: String, activity: String, reason: String, details: String = "") {
        val key = "$pkg|$activity|$reason|$details"
        val now = SystemClock.elapsedRealtime()
        if (key == lastDiagnostic && now - lastDiagnosticAt < 3_000L) return
        lastDiagnostic = key
        lastDiagnosticAt = now
        AutoBillStore.markScreenDiagnostic(this, pkg, activity, reason, details)
    }

    private fun clearSession() {
        handler.removeCallbacks(capturePage)
        captureScheduled = false
        session.clear()
        sampleAttempt = 0
        cacheCleared = false
        captureDetails = ""
        sampling.reset()
        lastCaptured = ""
        captureGate.clear()
        ocrGate.clear()
    }

    override fun onInterrupt() { clearSession() }

    override fun onUnbind(intent: Intent?): Boolean {
        isConnected = false
        clearSession()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        destroyed = true
        isConnected = false
        clearSession()
        AutoBillOcrDiagnostics.clear()
        paymentOcr?.close()
        paymentOcr = null
        super.onDestroy()
    }
}
