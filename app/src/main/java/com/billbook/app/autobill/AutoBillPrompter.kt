package com.billbook.app.autobill

import android.content.Context
import android.app.KeyguardManager
import android.os.PowerManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings

/**
 * 决定「怎么问用户」：有悬浮窗权限就弹底部面板，没有就发通知。
 *
 * 后台启动 Activity 在 Android 10+ 默认被拦，持有 SYSTEM_ALERT_WINDOW 是官方豁免之一，
 * 但厂商 ROM 仍可能静默丢掉这次启动且不抛异常。所以启动后要回查面板是否真的出现，
 * 没出现就补一条通知 —— 宁可多一条通知，也不能让账悄悄丢掉。
 */
object AutoBillPrompter {

    private const val FALLBACK_DELAY_MS = 2500L
    private val handler = Handler(Looper.getMainLooper())

    fun ask(context: Context, event: AutoBillStore.Event, preferNotification: Boolean = false) {
        val appContext = context.applicationContext
        if (AutoBillStore.pending(appContext).none { it.id == event.id }) return

        // 面板已经在显示同一条，不重复启动
        if (ConfirmAutoBillActivity.showingDraftId == event.id) return

        val keyguard = appContext.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        val power = appContext.getSystemService(Context.POWER_SERVICE) as? PowerManager
        // 连续付款时保留正在编辑的草稿；下一笔通过通知进入，避免覆盖输入。
        if (preferNotification || ConfirmAutoBillActivity.showingDraftId != null ||
            keyguard?.isKeyguardLocked == true || power?.isInteractive == false ||
            !Settings.canDrawOverlays(appContext)) {
            AutoBillNotifier.post(appContext, event)
            return
        }

        val launched = try {
            appContext.startActivity(ConfirmAutoBillActivity.intentFor(appContext, event.id))
            true
        } catch (_: Exception) {
            false
        }
        if (!launched) {
            AutoBillNotifier.post(appContext, event)
            return
        }

        handler.postDelayed({
            if (ConfirmAutoBillActivity.showingDraftId != event.id &&
                AutoBillStore.pending(appContext).any { it.id == event.id }) {
                AutoBillNotifier.post(appContext, event)
            }
        }, FALLBACK_DELAY_MS)
    }
}
