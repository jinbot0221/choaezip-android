package com.portfolio.choaezip

import com.google.firebase.FirebaseNetworkException
import org.junit.Assert.*
import org.junit.Test

@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [35], application = android.app.Application::class)
class AuthErrorsTest {
    @Test fun nativeFirebaseNetworkFailureGetsActionableMessage() {
        val message = authErrorMessage(FirebaseNetworkException("A network error (such as timeout, interrupted connection or unreachable host) has occurred."))
        assertTrue(message.contains("인터넷 연결"))
        assertFalse(message.contains("host"))
    }
    @Test fun disabledProviderAndDuplicateEmailHaveDistinctMessages() {
        assertTrue(authErrorMessage(com.google.firebase.auth.FirebaseAuthException("ERROR_OPERATION_NOT_ALLOWED", "disabled")).contains("비활성화"))
        assertTrue(authErrorMessage(com.google.firebase.auth.FirebaseAuthException("ERROR_EMAIL_ALREADY_IN_USE", "duplicate")).contains("이미 가입"))
    }
}
