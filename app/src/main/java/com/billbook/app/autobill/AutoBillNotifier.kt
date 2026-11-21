package com.billbook.app.autobill

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.RemoteInput
import com.billbook.app.R
import com.billbook.app.model.Bill

/**
 * 没有悬浮窗权限时的兜底：一条可直接输入的通知。
 *
 * 用 RemoteInput 而不是「点通知打开 App」，是因为付款后用户通常不想离开当前页面。
 * 下拉通知栏、打一个词、点「填名称并记入」，整笔账就结束了。
 */
object AutoBillNotifier {

    const val ACTION_CONFIRM = "com.billbook.app.autobill.action.CONFIRM"
    const val ACTION_IGNORE = "com.billbook.app.autobill.action.IGNORE"
    const val EXTRA_DRAFT_ID = "draft_id"
    const val KEY_TITLE_INPUT = "title_input"

    const val CHANNEL_ID = "autobill_confirm"
    const val NOTIFICATION_BASE = 7300

    fun notificationIdFor(draftId: String): Int = NOTIFICATION_BASE + (draftId.hashCode() and 0x3FFFFFFF)

    fun cancel(context: Context, draftId: String) {
        try {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            manager?.cancel(notificationIdFor(draftId))
        } catch (e: Exception) {
            android.util.Log.w("AutoBill", "Unable to cancel handled draft reminder", e)
        }
    }

    fun post(context: Context, event: AutoBillStore.Event) {
        // 延迟兜底和重试都可能晚于用户确认，不再给已处理的草稿发提醒。
        if (AutoBillStore.pending(context).none { it.id == event.id }) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return
        ensureChannel(nm)

        val sign = if (event.type == Bill.TYPE_EXPENSE) "-" else "+"
        val title = if (event.needsAmount) "${AutoBillParser.channelLabel(event.packageName)} · 待补金额"
        else if (event.needsDirection) "${AutoBillParser.channelLabel(event.packageName)} · 待核对转账"
        else "%s  %s¥%.2f".format(
            AutoBillParser.channelLabel(event.packageName), sign, event.amount
        )
        val body = if (event.note.contains("合并通知")) {
            "${event.note}。请打开后逐笔核对"
        } else if (event.needsAmount) {
            "微信通知未提供金额，打开转账详情识别或补金额后记入"
        } else if (event.needsDirection) {
            "微信通知未明确收支方向，打开核对转账后记入"
        } else if (event.note.contains("重复") || event.note.contains("退款") || event.note.contains("退回")) {
            "${event.note}。点开核对后再记入"
        } else if (event.merchant.isNotBlank()) {
            "点开补个名称可自动归类，或直接记入"
        } else {
            "补个名称就能自动归类；不填记为「其他」"
        }

        val confirmPi = PendingIntent.getBroadcast(
            context,
            event.id.hashCode(),
            Intent(context, AutoBillActionReceiver::class.java)
                .setAction(ACTION_CONFIRM)
                .setData(Uri.parse("billbook://autobill/${event.id}/confirm"))
                .putExtra(EXTRA_DRAFT_ID, event.id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
        val ignorePi = PendingIntent.getBroadcast(
            context,
            event.id.hashCode() + 1,
            Intent(context, AutoBillActionReceiver::class.java)
                .setAction(ACTION_IGNORE)
                .setData(Uri.parse("billbook://autobill/${event.id}/ignore"))
                .putExtra(EXTRA_DRAFT_ID, event.id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 点通知主体应该「打开面板」，不能直接入账 —— 否则用户只想看一眼就莫名多了一笔
        val openPi = PendingIntent.getActivity(
            context,
            event.id.hashCode() + 2,
            ConfirmAutoBillActivity.intentFor(context, event.id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val remoteInput = RemoteInput.Builder(KEY_TITLE_INPUT)
            .setLabel("填名称可自动归类")
            .build()
        val confirmAction = NotificationCompat.Action.Builder(
            R.mipmap.ic_launcher, "填名称并记入", confirmPi
        ).addRemoteInput(remoteInput).build()

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setOnlyAlertOnce(true)
            .setAutoCancel(true)
            .setContentIntent(openPi)
            .addAction(R.mipmap.ic_launcher, "忽略", ignorePi)
        // 疑似重复必须打开面板核对，避免通知里的快速回复再记一次。
        if (!event.note.contains("重复") && !event.note.contains("退款") && !event.note.contains("退回") &&
            !event.note.contains("合并通知") && !event.needsAmount && !event.needsDirection) builder.addAction(confirmAction)

        try {
            // Share the status-transition lock so confirmation cannot cancel a reminder
            // between the final pending check and posting it again.
            synchronized(AutoBillStore) {
                if (AutoBillStore.pending(context).any { it.id == event.id }) {
                    nm.notify(notificationIdFor(event.id), builder.build())
                }
            }
        } catch (_: SecurityException) {
            // Android 13+ 未授予 POST_NOTIFICATIONS：静默失败，草稿仍在「待确认」里
        }
    }

    private fun ensureChannel(nm: NotificationManager) {
        val channel = NotificationChannel(
            CHANNEL_ID, "待确认记账", NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "付款后提示补名称并确认入账"
            enableVibration(false)
        }
        nm.createNotificationChannel(channel)
    }
}
