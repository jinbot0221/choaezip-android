package com.portfolio.choaezip

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CancellationException
import org.junit.Assert.*
import org.junit.Test

class SignupTest {
    @Test fun authFailureDoesNotAttemptProfileCreation() = runBlocking {
        var profileCalls = 0
        val network = IllegalStateException("auth unavailable")
        try {
            createEmailAccount({ throw network }, { profileCalls++ })
            fail("Auth failure must propagate")
        } catch (e: IllegalStateException) { assertSame(network, e) }
        assertEquals(0, profileCalls)
    }
    @Test fun profileFailureKeepsCreatedAccountForCompletion() = runBlocking {
        var accounts = 0
        val network = IllegalStateException("profile unavailable")
        val result = createEmailAccount({ accounts++ }, { throw network })
        assertEquals(1, accounts)
        assertSame(network, result)
    }
    @Test fun cancelledProfileRequestRemainsCancelled() = runBlocking {
        try {
            createEmailAccount({}, { throw CancellationException() })
            fail("Cancellation must propagate")
        } catch (_: CancellationException) { }
    }
}
