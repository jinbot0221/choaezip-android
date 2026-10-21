package com.portfolio.choaezip

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class FirebaseTraceTest {
    @Test fun failureIncludesCodeButNeverExceptionMessageOrRequestSecrets() {
        ShadowLog.clear()
        FirebaseTrace.failure("auth.google.firebase", com.google.firebase.auth.FirebaseAuthException("ERROR_OPERATION_NOT_ALLOWED", "password=secret token=secret email=private@example.com"))
        val logs = ShadowLog.getLogsForTag(FirebaseTrace.TAG).joinToString("\n") { it.msg }
        assertTrue(logs.contains("ERROR_OPERATION_NOT_ALLOWED"))
        assertTrue(logs.contains("status=FAIL"))
        assertFalse(logs.contains("secret"))
        assertFalse(logs.contains("private@example.com"))
    }
    @Test fun tokenRequestLogsStageWithoutTokenValue() = runBlocking {
        ShadowLog.clear()
        assertEquals("private-fcm-token", FirebaseTrace.span("fcm.token") { "private-fcm-token" })
        val logs = ShadowLog.getLogsForTag(FirebaseTrace.TAG).joinToString("\n") { it.msg }
        assertTrue(logs.contains("status=START"))
        assertTrue(logs.contains("status=OK"))
        assertFalse(logs.contains("private-fcm-token"))
    }
}
