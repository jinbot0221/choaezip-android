package com.portfolio.choaezip

import android.app.Application
import android.content.Context
import android.net.Uri
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.UUID

typealias Doc = Map<String, Any?>
fun Doc.str(key: String, default: String = "") = this[key] as? String ?: default
fun Doc.num(key: String) = (this[key] as? Number)?.toLong() ?: 0L
fun Doc.bool(key: String) = this[key] == true
@Suppress("UNCHECKED_CAST") fun Doc.obj(key: String) = this[key] as? Doc ?: emptyMap()
@Suppress("UNCHECKED_CAST") fun Doc.rows(key: String = "items") = this[key] as? List<Doc> ?: emptyList()
fun Doc.strings(key: String) = (this[key] as? List<*>)?.filterIsInstance<String>() ?: emptyList()

class ChoaezipApplication : Application() {
    override fun onCreate() { super.onCreate(); Backend.init(this) }
}
object Backend {
    lateinit var context: Application
    lateinit var auth: FirebaseAuth
    lateinit var functions: FirebaseFunctions
    lateinit var storage: FirebaseStorage
    var configured = false
    fun init(context: Application) {
        this.context = context
        val app = if (BuildConfig.USE_EMULATORS) FirebaseApp.getApps(context).firstOrNull { it.name == "choaezip-emulator" } ?: FirebaseApp.initializeApp(context,
            FirebaseOptions.Builder().setProjectId("demo-choaezip")
                .setApplicationId("1:123456789:android:0000000000000000000000")
                .setApiKey("AIzaSy" + "0".repeat(33)) // Syntactically valid demo-only key; never used for a real Firebase project.
                .setStorageBucket("demo-choaezip.appspot.com").build(), "choaezip-emulator")
        else FirebaseApp.initializeApp(context)
        if (app == null) return
        auth = FirebaseAuth.getInstance(app)
        functions = FirebaseFunctions.getInstance(app, "asia-northeast3")
        storage = FirebaseStorage.getInstance(app)
        if (BuildConfig.USE_EMULATORS) {
            auth.useEmulator(BuildConfig.EMULATOR_HOST, 9099)
            functions.useEmulator(BuildConfig.EMULATOR_HOST, 5001)
            storage.useEmulator(BuildConfig.EMULATOR_HOST, 9199)
        }
        configured = true
    }
    @Suppress("UNCHECKED_CAST")
    suspend fun call(action: String, vararg args: Pair<String, Any?>): Doc {
        check(configured) { "Firebase 설정이 필요해요. google-services.json을 확인해 주세요." }
        return functions.getHttpsCallable("api").call(mapOf("action" to action, *args)).await().data as? Doc ?: emptyMap()
    }
    suspend fun upload(uri: Uri, purpose: String): String {
        val uid = auth.currentUser?.uid ?: error("로그인이 필요해요.")
        val resolver = context.contentResolver
        val mime = resolver.getType(uri) ?: error("사진 형식을 확인해 주세요.")
        require(mime in listOf("image/jpeg", "image/png", "image/webp")) { "JPEG, PNG, WebP만 올릴 수 있어요." }
        val bytes = withContext(Dispatchers.IO) {
            resolver.openInputStream(uri)?.use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                val limit = 10 * 1024 * 1024
                var total = 0
                while (true) {
                    val count = input.read(buffer, 0, minOf(buffer.size, limit + 1 - total))
                    if (count <= 0) break
                    total += count
                    require(total <= limit) { "사진은 한 장당 10MB 이하여야 해요." }
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            } ?: error("사진을 읽을 수 없어요.")
        }
        require(bytes.size <= 10 * 1024 * 1024) { "사진은 한 장당 10MB 이하여야 해요." }
        val path = "uploads/$uid/${UUID.randomUUID()}"
        val metadata = com.google.firebase.storage.StorageMetadata.Builder().setContentType(mime).setCustomMetadata("purpose", purpose).build()
        storage.reference.child(path).putBytes(bytes, metadata).await()
        return path
    }
    suspend fun google(token: String) { auth.signInWithCredential(GoogleAuthProvider.getCredential(token, null)).await() }
}
