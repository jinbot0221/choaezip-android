package com.portfolio.choaezip

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import org.json.JSONObject
import org.json.JSONArray
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID

data class Route(val screen: String, val id: String = "")
class AppViewModel : ViewModel() {
    private val prefs = Backend.context.getSharedPreferences("choaezip", Context.MODE_PRIVATE)
    private val stack = mutableStateListOf(Route("home"))
    val route get() = stack.last()
    var me by mutableStateOf<Doc?>(null); private set
    var busy by mutableStateOf(false); private set
    var loading by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null)
    var message by mutableStateOf<String?>(null)
    var data by mutableStateOf<Doc>(emptyMap()); private set
    var items by mutableStateOf<List<Doc>>(emptyList()); private set
    var artists by mutableStateOf<List<Doc>>(emptyList()); private set
    var artistResults by mutableStateOf<List<Doc>>(emptyList()); private set
    var schedules by mutableStateOf<List<Doc>>(emptyList()); private set
    var artistId by mutableStateOf(prefs.getString("artist", "") ?: ""); private set
    var month by mutableStateOf(YearMonth.now(java.time.ZoneId.of("Asia/Seoul")))
    var selectedDate by mutableStateOf(LocalDate.now(java.time.ZoneId.of("Asia/Seoul")))
    var filter by mutableStateOf("")
    var authorMe by mutableStateOf(false)
    var cursor by mutableStateOf<String?>(null); private set
    var pendingNickname by mutableStateOf(""); private set
    var pendingAgreement by mutableStateOf(false); private set
    var loginPrompt by mutableStateOf(false)
    var dirty by mutableStateOf(false)
    var discardPrompt by mutableStateOf(false)
    private var loadJob: Job? = null
    private var generation = 0
    val signedIn get() = me != null
    val nickname get() = me?.str("nickname") ?: "게스트"
    val favoriteName get() = artists.find { it.str("id") == artistId }?.str("name") ?: "최애를 선택해 주세요"
    val installationId: String = prefs.getString("installation", null) ?: UUID.randomUUID().toString().also { prefs.edit().putString("installation", it).apply() }
    init {
        if (Backend.configured) {
            viewModelScope.launch {
                runCatching { Backend.call("artists") }.onSuccess { artists = it.rows(); artistResults = artists }
                if (Backend.auth.currentUser != null) runCatching { Backend.call("me") }.onSuccess { applyProfile(it) }
                refresh()
            }
        } else error = "Firebase 프로젝트 설정이 필요해요."
    }
    fun navigate(screen: String, id: String = "") {
        if (screen in listOf("home", "calendar", "records", "lounge", "my")) {
            stack.clear(); filter = ""; stack.add(Route(screen))
        } else stack.add(Route(screen, id))
        dirty = false; refresh()
    }
    fun back() { if(busy) return; if (dirty) discardPrompt = true else backNow() }
    fun backNow() { discardPrompt = false; dirty = false; if (stack.size > 1) stack.removeAt(stack.lastIndex) else { stack.clear(); stack.add(Route("home")) }; refresh() }
    fun requireLogin(block: () -> Unit) { if (signedIn) block() else loginPrompt = true }
    private fun applyProfile(value: Doc) {
        me = value
        if (value.str("artistId").isNotEmpty()) artistId = value.str("artistId")
    }
    fun refresh(append: Boolean = false) {
        if (!Backend.configured) return
        val current = route; val run = ++generation
        val publicCacheKey = "public_schedules_${artistId}_${month}"
        loadJob?.cancel()
        loading = true; error = null
        if (!append) { data = emptyMap(); items = emptyList(); cursor = null }
        loadJob = viewModelScope.launch {
            try {
                val result = when (current.screen) {
                    "home", "calendar" -> Backend.call("schedules", "month" to month.toString(), "artistId" to artistId.ifBlank { null })
                    "artists" -> Backend.call("artists")
                    "schedule" -> Backend.call("schedule", "id" to current.id)
                    "records" -> if (signedIn) Backend.call("records", "artistId" to filter.ifBlank { null }, "cursor" to if (append) cursor else null) else emptyMap()
                    "record", "editRecord" -> if (current.id.isNotEmpty()) Backend.call("record", "id" to current.id) else emptyMap()
                    "lounge" -> Backend.call("posts", "artistId" to artistId.ifBlank { null }, "category" to filter.ifBlank { null }, "authorMe" to authorMe, "cursor" to if (append) cursor else null)
                    "post", "editPost" -> if (current.id.isNotEmpty()) Backend.call("post", "id" to current.id) else emptyMap()
                    "my", "profile" -> if (signedIn) Backend.call("me") else emptyMap()
                    "notifications" -> Backend.call("notifications", "cursor" to if (append) cursor else null)
                    "preferences" -> emptyMap()
                    "blocks" -> Backend.call("blocks")
                    else -> emptyMap()
                }
                if (run != generation) return@launch
                data = result
                items = if (append) items + result.rows() else result.rows()
                cursor = result["cursor"] as? String
                if (current.screen in listOf("home", "calendar")) {
                    schedules = result.rows()
                    prefs.edit().putString(publicCacheKey, JSONObject(result).toString()).apply()
                }
                if (current.screen == "artists") { artists = result.rows(); artistResults = artists }
                if (current.screen in listOf("my", "profile") && result.isNotEmpty()) applyProfile(result)
                if (current.screen in listOf("record", "post") && result.strings("media").isNotEmpty()) {
                    runCatching { Backend.call("media", "kind" to current.screen, "id" to current.id) }.onSuccess { if (run == generation) data = data + ("urls" to it["urls"]) }
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                if (run == generation) {
                    error = authErrorMessage(e)
                    if(current.screen in listOf("home", "calendar")) {
                        runCatching { parseJson(JSONObject(prefs.getString(publicCacheKey, "{}") ?: "{}")) as Doc }.onSuccess { cached ->
                            data = cached + ("cached" to true); items = cached.rows(); schedules = cached.rows()
                        }
                    }
                }
            } finally { if (run == generation) loading = false }
        }
    }
    private fun parseJson(value: Any?): Any? = when(value) {
        is JSONObject -> value.keys().asSequence().associateWith { parseJson(value.get(it)) }
        is JSONArray -> (0 until value.length()).map { parseJson(value.get(it)) }
        JSONObject.NULL -> null
        else -> value
    }
    fun work(block: suspend () -> Unit) {
        if (busy) return
        busy = true; error = null
        viewModelScope.launch { try { block() } catch(e: Exception) { if(e is kotlinx.coroutines.CancellationException) throw e; error = authErrorMessage(e) } finally { busy = false } }
    }
    fun selectArtist(id: String) = work {
        if (signedIn) Backend.call("favorite", "artistId" to id)
        artistId = id; prefs.edit().putString("artist", id).apply(); backNow()
    }
    fun login(email: String, password: String) = work {
        require(Validation.email(email.trim())) { "이메일 형식을 확인해 주세요." }
        FirebaseTrace.span("auth.email.login") { Backend.auth.signInWithEmailAndPassword(email.trim(), password).await() }; afterAuth()
    }
    fun signup(email: String, password: String, confirm: String, nickname: String, agree: Boolean) = work {
        require(Validation.signupErrors(email, password, confirm, nickname, agree).isEmpty()) { Validation.signupErrors(email, password, confirm, nickname, agree).values.first() }
        pendingNickname = nickname.trim(); pendingAgreement = agree
        val profileError = createEmailAccount(
            createAccount = { FirebaseTrace.span("auth.email.create") { Backend.auth.createUserWithEmailAndPassword(email.trim(), password).await() }; Unit },
            saveProfile = { Backend.call("completeSignup", "nickname" to pendingNickname, "terms" to agree, "privacy" to agree); Unit },
        )
        if (profileError != null) {
            navigate("completeSignup")
            throw IllegalStateException("이메일 계정은 생성됐어요. 프로필 저장에 실패했으니 연결을 확인한 뒤 가입 완료를 다시 눌러 주세요.", profileError)
        }
        afterAuth()
    }
    suspend fun googleLogin(idToken: String) {
        Backend.google(idToken)
        try { afterAuth() }
        catch(e: Exception) {
            if(e is kotlinx.coroutines.CancellationException) throw e
            throw IllegalStateException("Google 계정 인증은 완료됐지만 앱 프로필을 불러오지 못했어요. " + profileErrorMessage(e), e)
        }
    }
    suspend fun afterAuth() {
        val result = runCatching { FirebaseTrace.span("auth.profile.load") { Backend.call("me") } }
        result.onSuccess {
            FirebaseTrace.event("auth.navigation", "OK", "destination=authenticated"); applyProfile(it); clearAuthRoutes(); syncDevice(); message = "로그인했어요. 원하는 기능을 다시 선택해 주세요."
        }.onFailure { if(it is SignupRequiredException) { FirebaseTrace.event("auth.navigation", "INFO", "destination=completeSignup"); navigate("completeSignup") } else throw it }
    }
    private fun clearAuthRoutes() {
        pendingNickname = ""; pendingAgreement = false
        while(stack.size > 1 && stack.last().screen in listOf("login","signup","completeSignup","reset","newPassword")) stack.removeAt(stack.lastIndex)
        refresh()
    }
    fun completeSignup(nickname: String, agree: Boolean) = work {
        require(Validation.profileErrors(nickname, agree).isEmpty()) { Validation.profileErrors(nickname, agree).values.first() }
        Backend.call("completeSignup", "nickname" to nickname, "terms" to agree, "privacy" to agree); afterAuth()
    }
    fun resetPassword(email: String) = work {
        require(Validation.email(email.trim())) { "이메일 형식을 확인해 주세요." }
        try { Backend.auth.sendPasswordResetEmail(email.trim()).await() }
        catch(e: FirebaseAuthException) { if(e.errorCode !in listOf("ERROR_USER_NOT_FOUND","ERROR_INVALID_EMAIL")) throw e }
        message = "가입된 이메일이라면 재설정 메일을 보냈어요. 스팸함도 확인해 주세요."
    }
    fun newPassword(code: String, password: String, confirm: String) = work {
        require(Validation.password(password) && password == confirm) { "8~64자 비밀번호와 확인을 맞춰 주세요." }
        Backend.auth.verifyPasswordResetCode(code).await()
        Backend.auth.confirmPasswordReset(code, password).await(); navigate("login"); message = "비밀번호를 변경했어요."
    }
    // Spark: scheduled reminder requests disabled.
/*
    fun reminder(enabled: Boolean) = work {
        Backend.call("reminder", "id" to route.id, "enabled" to enabled, "expectedVersion" to data.num("version")); syncDevice(); refresh()
    }
*/
    fun saveContent(record: Boolean, title: String, body: String, date: String, category: String, artist: String, photos: List<Uri>, existing: List<String>, requestKey: String, version: Long) = work {
        Validation.content(title, body, record)?.let { error(it) }
        // Spark: no photo selection/upload; never issue Storage requests.
        require(photos.isEmpty() && existing.isEmpty()) { "사진 업로드는 비활성화되어 있어요." }
        val paths = emptyList<String>()
        val result = Backend.call(if(record) "saveRecord" else "savePost", "id" to route.id.ifBlank { null }, "title" to title, "body" to body, "date" to date, "category" to category, "artistId" to artist, "media" to paths, "requestKey" to requestKey, "expectedVersion" to version)
        clearDraft(); dirty = false; backNow(); navigate(if(record) "record" else "post", result.str("id")); message = "저장했어요."
    }
    fun deleteContent(record: Boolean) = work {
        Backend.call(if(record) "deleteRecord" else "deletePost", "id" to route.id, "expectedVersion" to data.num("version")); backNow(); message = "삭제했어요."
    }
    fun moreComments() = work {
        val current = data.obj("comments")
        val next = Backend.call("comments", "id" to route.id, "cursor" to current["cursor"])
        data = data + ("comments" to (next + ("items" to current.rows() + next.rows())))
    }
    fun like() = work { Backend.call("like", "id" to route.id, "enabled" to !data.bool("liked")); refresh() }
    fun comment(body: String, key: String, done: () -> Unit) = work { Backend.call("comment", "id" to route.id, "body" to body, "requestKey" to key); done(); refresh() }
    fun deleteComment(id: String) = work { Backend.call("deleteComment", "postId" to route.id, "id" to id); refresh() }
    fun profile(nickname: String, bio: String) = work { Backend.call("profile", "nickname" to nickname, "bio" to bio); dirty = false; backNow(); message = "프로필을 저장했어요." }
    fun preference(key: String, enabled: Boolean) = work { Backend.call("preferences", "values" to mapOf(key to enabled)); refresh() }
    fun block(uid: String, enabled: Boolean) = work { Backend.call("block", "userId" to uid, "enabled" to enabled); if(route.screen=="report") { backNow(); backNow() } else refresh(); message = if(enabled) "사용자를 차단했어요." else "차단을 해제했어요." }
    var reportTarget: Doc = emptyMap()
    fun report(reason: String, detail: String) = work { Backend.call("report", "targetType" to reportTarget.str("type"), "targetId" to reportTarget.str("id"), "postId" to reportTarget.str("postId"), "reason" to reason, "detail" to detail); message = "신고가 접수됐어요. 신고만으로 글이 숨겨지지는 않아요."; backNow() }
    fun openNotification(n: Doc) = work {
        Backend.call("readNotification", "id" to n.str("id"))
        navigate(n.str("entityType").let { if(it in listOf("schedule","post","record")) it else "notifications" }, n.str("entityId"))
    }
    fun logout() = work {
        runCatching { Backend.call("logout", "installationId" to installationId) }
        Backend.auth.signOut(); clearPrivate(); navigate("home"); message = "로그아웃했어요."
    }
    private fun clearPrivate() {
        pendingNickname = ""; pendingAgreement = false
        viewModelScope.launch {
            runCatching { androidx.credentials.CredentialManager.create(Backend.context).clearCredentialState(androidx.credentials.ClearCredentialStateRequest()) }
        }
        me = null; data = emptyMap(); items = emptyList()
        prefs.all.keys.filter { it.startsWith("draft_") }.forEach { prefs.edit().remove(it).apply() }
        (Backend.context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancelAll()
    }
    fun deleteAccount(password: String, googleReauth: (suspend () -> Unit)? = null) = work {
        val u = Backend.auth.currentUser ?: error("다시 로그인해 주세요.")
        if (googleReauth != null) googleReauth() else u.reauthenticate(EmailAuthProvider.getCredential(u.email ?: "", password)).await()
        u.getIdToken(true).await()
        Backend.call("deleteAccount", "confirmation" to true)
        Backend.auth.signOut(); clearPrivate(); navigate("home"); message = "탈퇴했어요. 개인 기록과 프로필이 삭제되고 게시글이 비공개 처리됐어요."
    }
    private fun draftKey() = "draft_${Backend.auth.currentUser?.uid}_${route.screen}_${route.id}"
    fun draft(): Doc = runCatching { val j=JSONObject(prefs.getString(draftKey(), "{}") ?: "{}"); j.keys().asSequence().associateWith { j.get(it) } }.getOrDefault(emptyMap())
    fun saveDraft(fields: Doc) { if(signedIn) { prefs.edit().putString(draftKey(), JSONObject(fields).toString()).apply(); dirty = true } }
    private fun clearDraft() { prefs.edit().remove(draftKey()).apply() }
    fun cancelSocial() { if(Backend.configured && me==null) Backend.auth.signOut(); backNow() }
    // Spark: no server device registration or automatic push dispatch.
    fun syncDevice() = Unit
/*
    fun syncDevice() {
        if (BuildConfig.USE_EMULATORS || !signedIn) return
        viewModelScope.launch {
            runCatching {
                val token = Backend.fcmToken()
                val allowed = Build.VERSION.SDK_INT < 33 || Backend.context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
                Backend.call("device", "installationId" to installationId, "token" to token, "permission" to allowed)
            }.onFailure { if(it is Exception) FirebaseTrace.failure("fcm.device.register", it) }
        }
    }
*/
    private var searchJob: Job? = null
    fun searchArtists(q: String) {
        searchJob?.cancel(); searchJob = viewModelScope.launch { delay(300); runCatching { Backend.call("artists", "q" to q) }.onSuccess { artistResults=it.rows() }.onFailure { error=authErrorMessage(it as Exception) } }
    }
}
