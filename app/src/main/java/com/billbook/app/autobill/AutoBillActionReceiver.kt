package com.billbook.app.autobill

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.RemoteInput
import com.billbook.app.util.AppExecutors

/**
 * 处理兜底通知上的两个动作。
 *
 * 「填名称并记入」带回 RemoteInput 的文本；「忽略」直接标掉。
 * 记账写库是耗时操作，放到后台线程并把 [goAsync] 的结果收尾，避免广播被杀进程截断。
 */
class AutoBillActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra(AutoBillNotifier.EXTRA_DRAFT_ID) ?: return
        val appContext = context.applicationContext

        when (intent.action) {
            AutoBillNotifier.ACTION_IGNORE -> {
                val pending = goAsync()
                AppExecutors.io {
                    try {
                        val ok = runCatching { AutoBillStore.setStatus(appContext, id, AutoBillStore.STATUS_IGNORED) }.isSuccess
                        if (ok) AutoBillNotifier.cancel(appContext, id)
                        else AppExecutors.main {
                            android.widget.Toast.makeText(appContext, "忽略失败，草稿已保留，请重试", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    } finally {
                        pending.finish()
                    }
                }
            }

            AutoBillNotifier.ACTION_CONFIRM -> {
                val text = RemoteInput.getResultsFromIntent(intent)
                    ?.getCharSequence(AutoBillNotifier.KEY_TITLE_INPUT)
                    ?.toString().orEmpty()
                val pending = goAsync()
                AppExecutors.io {
                    try {
                        val ok = runCatching { AutoBillStore.confirm(appContext, id, text) }.getOrDefault(false)
                        if (ok) {
                            AutoBillNotifier.cancel(appContext, id)
                        } else {
                            val draft = AutoBillStore.pending(appContext).firstOrNull { it.id == id }
                            if (draft == null) AutoBillNotifier.cancel(appContext, id)
                            else {
                                AutoBillNotifier.post(appContext, draft)
                                AppExecutors.main {
                                    android.widget.Toast.makeText(appContext, "写入失败，草稿已保留，请重试", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    } finally {
                        pending.finish()
                    }
                }
            }
        }
    }
}
