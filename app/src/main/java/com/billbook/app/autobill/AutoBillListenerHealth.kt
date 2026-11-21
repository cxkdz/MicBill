package com.billbook.app.autobill

import android.content.ComponentName
import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.util.Log

/** Live connection checks are separate from historical connection timestamps. */
object AutoBillListenerHealth {
    private const val REBIND_COOLDOWN_MS = 15_000L
    private var lastRebindAttempt = -REBIND_COOLDOWN_MS

    enum class RebindResult { REQUESTED, CONNECTED, NOT_GRANTED, COOLDOWN, FAILED }

    @Synchronized
    fun requestNotificationRebind(context: Context): RebindResult {
        if (!AutoBillStore.isListenerGranted(context)) return RebindResult.NOT_GRANTED
        if (PayNotificationListenerService.isConnected) return RebindResult.CONNECTED
        val now = SystemClock.elapsedRealtime()
        if (now - lastRebindAttempt < REBIND_COOLDOWN_MS) return RebindResult.COOLDOWN
        lastRebindAttempt = now
        return try {
            NotificationListenerService.requestRebind(
                ComponentName(context.applicationContext, PayNotificationListenerService::class.java)
            )
            RebindResult.REQUESTED
        } catch (e: Exception) {
            Log.w("AutoBill", "notification listener rebind failed", e)
            RebindResult.FAILED
        }
    }

    fun isPageCaptureEnabled(context: Context): Boolean {
        val resolver = context.contentResolver
        if (Settings.Secure.getInt(resolver, Settings.Secure.ACCESSIBILITY_ENABLED, 0) != 1) return false
        val enabled = Settings.Secure.getString(resolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            .orEmpty()
        val service = ComponentName(context.applicationContext, AutoBillAccessibilityService::class.java)
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == service }
    }
}
