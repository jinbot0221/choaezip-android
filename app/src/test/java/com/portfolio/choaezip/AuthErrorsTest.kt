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
}
