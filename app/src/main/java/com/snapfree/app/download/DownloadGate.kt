package com.snapfree.app.download

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Limits how many downloads run at the same time. WorkManager may start more workers than the
 * user wants, so each worker waits here (shown as "queued") until a slot is free.
 * The limit is read on every attempt, so changes in settings apply to queued jobs immediately.
 */
object DownloadGate {
    private val mutex = Mutex()
    private val running = MutableStateFlow(0)

    suspend fun acquire(limit: suspend () -> Int) {
        while (true) {
            val acquired = mutex.withLock {
                if (running.value < limit()) {
                    running.value += 1
                    true
                } else {
                    false
                }
            }
            if (acquired) return
            // Wake up when a slot is released, or re-check periodically in case the limit changed.
            val snapshot = running.value
            withTimeoutOrNull(2_000) { running.first { it < snapshot } }
        }
    }

    suspend fun release() {
        mutex.withLock { running.value = (running.value - 1).coerceAtLeast(0) }
    }
}
