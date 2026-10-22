package com.minimalist.launcher.system

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.time.LocalDateTime

/** Emits the current time now and then at the start of every minute. */
fun minuteTicker(): Flow<LocalDateTime> = flow {
    while (true) {
        val now = LocalDateTime.now()
        emit(now)
        delay(60_000L - (now.second * 1_000L + now.nano / 1_000_000L))
    }
}
