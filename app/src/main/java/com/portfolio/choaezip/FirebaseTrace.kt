package com.portfolio.choaezip

import android.util.Log
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.functions.FirebaseFunctionsException
import com.google.firebase.storage.StorageException
import kotlinx.coroutines.CancellationException
import java.util.concurrent.atomic.AtomicInteger

/** Debug diagnostics: never log SDK exception messages, request bodies or credentials. */
internal object FirebaseTrace {
    const val TAG = "ChoaezipFirebase"
    private val sequence = AtomicInteger()
    fun event(stage: String, status: String, details: String = "") {
        if (BuildConfig.DEBUG) Log.i(TAG, "stage=$stage status=$status $details")
    }
    fun failure(stage: String, error: Exception) {
        if (!BuildConfig.DEBUG) return
        val causes = generateSequence<Throwable>(error) { it.cause }.take(5).toList()
        val codes = causes.mapNotNull {
            when (it) {
                is FirebaseAuthException -> it.errorCode
                is FirebaseFunctionsException -> it.code.name
                is StorageException -> it.errorCode.toString()
                // FCM exposes these fixed error strings through IOException, not a typed code.
                else -> it.message?.takeIf { value -> value in setOf("SERVICE_NOT_AVAILABLE", "AUTHENTICATION_FAILED", "INSTANCE_ID_RESET", "MISSING_INSTANCEID_SERVICE", "TOO_MANY_REGISTRATIONS", "INVALID_SENDER") }
            }
        }
        Log.e(TAG, "stage=$stage status=FAIL types=${causes.joinToString(",") { it.javaClass.simpleName }} codes=${codes.joinToString(",").ifEmpty { "none" }}")
        // Stack locations are useful without printing exception messages containing user data.
        error.stackTrace.take(5).forEach { Log.e(TAG, "at ${it.className}.${it.methodName}(${it.fileName}:${it.lineNumber})") }
    }
    fun <T> sync(stage: String, block: () -> T): T {
        val id = sequence.incrementAndGet()
        val start = android.os.SystemClock.elapsedRealtime()
        event(stage, "START", "request=$id")
        try { return block().also { event(stage, "OK", "request=$id elapsedMs=${android.os.SystemClock.elapsedRealtime()-start}") } }
        catch (e: Exception) { failure("$stage request=$id", e); throw e }
    }
    suspend fun <T> span(stage: String, block: suspend () -> T): T {
        val id = sequence.incrementAndGet()
        val start = android.os.SystemClock.elapsedRealtime()
        event(stage, "START", "request=$id")
        try { return block().also { event(stage, "OK", "request=$id elapsedMs=${android.os.SystemClock.elapsedRealtime()-start}") } }
        catch(e: CancellationException) { event(stage, "CANCELLED", "request=$id"); throw e }
        catch(e: Exception) { failure("$stage request=$id", e); throw e }
    }
}
