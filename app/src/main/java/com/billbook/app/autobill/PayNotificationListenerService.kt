package com.billbook.app.autobill

import android.app.Notification
import android.content.ComponentName
import android.content.Intent
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.billbook.app.util.AppExecutors

/**
 * 档 0 / 档 1 共用的捕获源。
 *
 * 只做三件事：过滤微信/支付宝包名 → 取出通知文本 → 交给 AutoBillStore。
 * 解析、去重、草稿、入账全部不在这里，将来换成无障碍读屏只需替换这个捕获源。
 *
 * 服务由系统绑定，不依赖 App 进程常驻；但厂商省电策略可能解绑，
 * 所以 onListenerConnected 会记时间戳，设置页可以据此判断「是不是被冻结了」。
 */
class PayNotificationListenerService : NotificationListenerService() {

    companion object {
        private const val TAG = "AutoBill"
        // 微信、支付宝 + 银行 App：银行推送是付款方唯一稳定的事件源
        private val WATCHED = AutoBillParser.watchedPackages()
        @Volatile var isConnected = false
            private set
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        isConnected = true
        AutoBillStore.markConnected(this)
        Log.i(TAG, "listener connected")
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        isConnected = false
        if (AutoBillStore.isListenerGranted(this)) {
            runCatching { requestRebind(ComponentName(this, javaClass)) }
                .onFailure { Log.w(TAG, "listener rebind failed", it) }
        }
    }

    override fun onDestroy() {
        isConnected = false
        super.onDestroy()
    }

    override fun onUnbind(intent: Intent?): Boolean {
        isConnected = false
        return super.onUnbind(intent)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        val notification = sbn.notification ?: return
        // Inbox/group summaries can repeat several transactions and must not create a bill.
        if (notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        // 档 0 诊断：即使包名被过滤，也留一条 debug 日志证明服务确实在收事件
        Log.d(TAG, "posted pkg=${sbn.packageName}")
        if (sbn.packageName !in WATCHED) {
            // 包名诊断：只记包名 + 摘要，不记完整正文。
            // 很多应用不填 EXTRA_TITLE，标题为空时退回正文前 30 字，否则诊断看不出内容。
            val extras = notification.extras
            val probeTitle = extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString()
                ?.takeIf { it.isNotBlank() }
                ?: extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.take(30).orEmpty()
            val appCtx = applicationContext
            AppExecutors.io {
                AutoBillStore.recordProbe(appCtx, sbn.packageName, probeTitle, sbn.postTime)
            }
            return
        }
        // 先记账「支付类通知来过」，再取内容：即便取不到 extras 也要留痕，
        // 这样才能区分「通知根本没来」和「来了但没内容」。
        AutoBillStore.markWatched(this, sbn.packageName, sbn.postTime)
        val extras = notification.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
            ?.takeIf { it.isNotBlank() }
            ?: extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
                ?.map { it.toString() }?.filter { it.isNotBlank() }?.joinToString("\n")
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()

        // 捕获回调在主线程；自动入账会写数据库，必须切到 IO。
        // 只有「拿不准」的才会拿到 draft，再由主线程决定弹面板还是发通知。
        val ctx = applicationContext
        val key = sbn.key
        val postTime = sbn.postTime
        val pkg = sbn.packageName
        AppExecutors.io {
            val draft = try {
                AutoBillStore.record(ctx, key, postTime, pkg, title, text, bigText)
            } catch (e: Exception) {
                Log.w(TAG, "record failed", e)
                null
            }
            // 正文可能含金额与商户，日志只留包名和结论
            Log.i(TAG, "captured pkg=$pkg draft=${draft?.id ?: "none"}")
            if (draft != null) {
                AppExecutors.main { AutoBillPrompter.ask(ctx, draft) }
            }
        }
    }
}
