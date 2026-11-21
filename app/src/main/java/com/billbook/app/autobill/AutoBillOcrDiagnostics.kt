package com.billbook.app.autobill

/** One short-lived OCR read for a user-opened preview; never persisted or logged. */
object AutoBillOcrDiagnostics {
    private const val MAX_LINES = 60
    private const val MAX_LINE_LENGTH = 300
    private const val TTL_MS = 5 * 60_000L

    data class Snapshot(
        val packageName: String,
        val activity: String,
        val texts: List<String>,
        val reason: String,
        val time: Long
    )

    private var snapshot: Snapshot? = null

    @Synchronized
    fun record(
        packageName: String, activity: String, texts: List<String>, reason: String,
        now: Long = System.currentTimeMillis()
    ) {
        snapshot = Snapshot(
            packageName.take(150), activity.take(200),
            texts.asSequence().flatMap { it.lineSequence() }.map(String::trim)
                .filter(String::isNotEmpty).take(MAX_LINES)
                .map { it.take(MAX_LINE_LENGTH) }.toList(),
            reason.take(200), now
        )
    }

    @Synchronized
    fun latest(now: Long = System.currentTimeMillis()): Snapshot? {
        val current = snapshot ?: return null
        val age = now - current.time
        if (age < 0L || age >= TTL_MS) {
            snapshot = null
            return null
        }
        return current
    }

    @Synchronized
    fun clear() { snapshot = null }
}
