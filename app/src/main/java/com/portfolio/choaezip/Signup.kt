package com.portfolio.choaezip

import kotlinx.coroutines.CancellationException

// Only profile completion may be retried after Firebase Auth creates an account.
internal suspend fun createEmailAccount(
    createAccount: suspend () -> Unit,
    saveProfile: suspend () -> Unit,
): Exception? {
    createAccount()
    return try { saveProfile(); null }
    catch (e: CancellationException) { throw e }
    catch (e: Exception) { e }
}
