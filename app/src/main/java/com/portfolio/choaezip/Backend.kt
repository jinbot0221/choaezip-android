package com.portfolio.choaezip

import android.app.Application
import android.content.Context
import android.net.Uri
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
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
    override fun onCreate() {
        super.onCreate(); PushChannels.create(this); Backend.init(this)
        if(BuildConfig.DEBUG && !BuildConfig.USE_EMULATORS) {
            kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
                // Independent of Auth and Functions: diagnose FCM connectivity on app start.
                runCatching { Backend.fcmToken() }
            }
        }
    }
}
object Backend {
    lateinit var context: Application
    lateinit var auth: FirebaseAuth
    lateinit var firestore: com.google.firebase.firestore.FirebaseFirestore
    var configured = false
    fun init(context: Application) = FirebaseTrace.sync("firebase.init") {
        this.context = context
        val app = if (BuildConfig.USE_EMULATORS) FirebaseApp.getApps(context).firstOrNull { it.name == "choaezip-emulator" } ?: FirebaseApp.initializeApp(context,
            FirebaseOptions.Builder().setProjectId("demo-choaezip")
                .setApplicationId("1:123456789:android:0000000000000000000000")
                .setApiKey("AIzaSy" + "0".repeat(33)) // Syntactically valid demo-only key; never used for a real Firebase project.
                .setStorageBucket("demo-choaezip.appspot.com").build(), "choaezip-emulator")
        else FirebaseApp.initializeApp(context)
        checkNotNull(app) { "Firebase initialization failed: missing app configuration" }
        val webClient = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
        val playServices = runCatching { context.packageManager.getPackageInfo("com.google.android.gms", 0).versionName }.getOrNull()
        FirebaseTrace.event("firebase.config", "INFO", "project=${app.options.projectId} package=${context.packageName} appName=${app.name} emulator=${BuildConfig.USE_EMULATORS} apiKeyPresent=${app.options.apiKey.isNotBlank()} webClientPresent=${webClient != 0} senderIdPresent=${!app.options.gcmSenderId.isNullOrBlank()} bucketPresent=${!app.options.storageBucket.isNullOrBlank()} playServicesVersion=${playServices ?: "missing"} backend=firestore-direct plan=Spark")
        auth = FirebaseAuth.getInstance(app)
        firestore = com.google.firebase.firestore.FirebaseFirestore.getInstance(app)
        firestore.firestoreSettings = com.google.firebase.firestore.FirebaseFirestoreSettings.Builder()
            .setLocalCacheSettings(com.google.firebase.firestore.MemoryCacheSettings.newBuilder().build()).build()
        if (BuildConfig.USE_EMULATORS) {
            auth.useEmulator(BuildConfig.EMULATOR_HOST, 9099)
            firestore.useEmulator(BuildConfig.EMULATOR_HOST, 8080)
        }
        configured = true
    }
    @Suppress("UNCHECKED_CAST")
    suspend fun call(action: String, vararg args: Pair<String, Any?>): Doc {
        check(configured) { "Firebase 설정이 필요해요. google-services.json을 확인해 주세요." }
        return FirebaseTrace.span("firestore.$action") { SparkRepository.call(action, mapOf(*args)) }
    }
    // Spark: Cloud Storage uploads disabled; original implementation retained below.
/*
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
        FirebaseTrace.span("storage.upload") { storage.reference.child(path).putBytes(bytes, metadata).await() }
        return path
    }
*/
    suspend fun google(token: String) = FirebaseTrace.span("auth.google.firebase") {
        auth.signInWithCredential(GoogleAuthProvider.getCredential(token, null)).await()
        Unit
    }
    suspend fun fcmToken(): String = FirebaseTrace.span("fcm.token") {
        check(configured && !BuildConfig.USE_EMULATORS) { "Real Firebase configuration required" }
        com.google.firebase.messaging.FirebaseMessaging.getInstance().token.await()
    }
}
