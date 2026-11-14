package com.billbook.app.util

import android.os.Handler
import android.os.Looper
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * 全局共享的后台执行器。
 *
 * 数据库读写、Apache POI 账单解析等耗时操作统一走 [io]，结果通过 [main] 切回主线程更新 UI。
 * 进程级单例，不随 Fragment 生命周期创建/销毁；调用方在 [main] 回调中仍需自行判断
 * isAdded / _binding，因为异步任务返回时页面可能已经销毁。
 */
object AppExecutors {

    private val ioPool: ExecutorService = Executors.newFixedThreadPool(2)
    private val mainHandler = Handler(Looper.getMainLooper())

    /**
     * [onError] 在**主线程**回调，调用方应在此给出可见提示（Toast 等），
     * 否则后台异常只会留下日志、UI 会静默停在旧数据。
     * 不传 onError 的旧调用点行为不变。
     */
    fun io(onError: ((Exception) -> Unit)? = null, task: () -> Unit) {
        ioPool.execute {
            try {
                task()
            } catch (e: Exception) {
                android.util.Log.e("AppExecutors", "background task failed", e)
                main { onError?.invoke(e) }
            }
        }
    }

    fun main(task: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            task()
        } else {
            mainHandler.post(task)
        }
    }
}
