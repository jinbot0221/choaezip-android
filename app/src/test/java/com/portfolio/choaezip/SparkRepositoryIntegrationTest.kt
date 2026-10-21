package com.portfolio.choaezip

import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.net.HttpURLConnection
import java.net.URI
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Runs the actual Android repository against Auth/Firestore, with no Functions/Storage emulator. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class SparkRepositoryIntegrationTest {
    @Before fun setup() {
        assumeTrue(java.lang.Boolean.getBoolean("sparkIntegration"))
        check(BuildConfig.USE_EMULATORS && BuildConfig.EMULATOR_HOST == "127.0.0.1")
        Backend.init(ApplicationProvider.getApplicationContext())
        val url = URI("http://127.0.0.1:8080/v1/projects/demo-choaezip/databases/(default)/documents:commit").toURL()
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.setRequestProperty("Authorization", "Bearer owner") // Emulator-only administrative seed.
        connection.setRequestProperty("Content-Type", "application/json")
        connection.doOutput = true
        connection.outputStream.use { it.write("""{"writes":[{"update":{"name":"projects/demo-choaezip/databases/(default)/documents/artists/native_test","fields":{"name":{"stringValue":"Native test"},"active":{"booleanValue":true}}}}]}""".toByteArray()) }
        check(connection.responseCode == 200)
        connection.disconnect()
    }
    private fun background(block: suspend () -> Unit) {
        val executor = Executors.newSingleThreadExecutor()
        val future = executor.submit<Unit> { runBlocking { block() } }
        try {
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(90)
            while(!future.isDone && System.nanoTime() < deadline) {
                shadowOf(Looper.getMainLooper()).idle()
                Thread.sleep(10)
            }
            future.get(1, TimeUnit.SECONDS)
        } finally { executor.shutdownNow() }
    }
    @Test fun nativeSignupPostsRecordsReactionsAndAccountCleanup() = background {
        val email = "native-${UUID.randomUUID()}@example.com"
        val password = "password123!"
        Backend.auth.createUserWithEmailAndPassword(email, password).await()
        try { Backend.call("me"); fail("Incomplete profile must require signup") } catch (_: SignupRequiredException) { }
        Backend.call("completeSignup", "nickname" to "안드로이드테스터", "terms" to true, "privacy" to true)
        assertEquals("안드로이드테스터", Backend.call("me").str("nickname"))
        val fields = arrayOf("title" to "안드로이드 글", "body" to "여러 줄\n실제 저장", "artistId" to "native_test", "category" to "자유 이야기", "media" to emptyList<String>(), "requestKey" to "native-create")
        val post = Backend.call("savePost", *fields).str("id")
        assertEquals(post, Backend.call("savePost", *fields).str("id"))
        Backend.auth.signOut()
        Backend.auth.signInWithEmailAndPassword(email,password).await()
        assertEquals("여러 줄\n실제 저장", Backend.call("post", "id" to post).str("body"))
        assertTrue(Backend.call("posts").rows().any { it.str("id") == post })
        Backend.call("like", "id" to post, "enabled" to true)
        Backend.call("like", "id" to post, "enabled" to true)
        assertEquals(1L, Backend.call("post", "id" to post).num("likeCount"))
        val comment = Backend.call("comment", "id" to post, "body" to "네이티브 댓글", "requestKey" to "native-comment").str("id")
        assertTrue(Backend.call("comments", "id" to post).rows().any { it.str("id") == comment })
        Backend.call("saveRecord", "title" to "비공개 기록", "body" to "추억", "date" to "2026-10-21", "artistId" to "native_test", "category" to "일상", "media" to emptyList<String>(), "requestKey" to "native-record")
        assertEquals(1, Backend.call("records").rows().size)
        Backend.call("profile", "nickname" to "새닉네임", "bio" to "소개")
        assertEquals("새닉네임", Backend.call("post", "id" to post).obj("author").str("nickname"))
        Backend.call("favorite", "artistId" to "native_test")
        Backend.call("deleteAccount", "confirmation" to true)
        assertNull(Backend.auth.currentUser)
    }
}
