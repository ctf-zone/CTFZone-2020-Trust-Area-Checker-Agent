package ctfz.trustarea.coroutines

import kotlinx.coroutines.*

import ctfz.trustarea.result.*


suspend fun<T> withTimeoutOrError(timeMillis: Long, error: String, block: suspend CoroutineScope.() -> ResultS<T>): ResultS<T> =
    withTimeoutOrNull(timeMillis, block)?: Err(error)
