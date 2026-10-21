package com.portfolio.choaezip

import com.google.firebase.Timestamp
import com.google.firebase.firestore.*
import kotlinx.coroutines.tasks.await
import java.security.MessageDigest
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

internal class SignupRequiredException : IllegalStateException("닉네임과 필수 동의를 입력해 가입을 완료해 주세요.")

/** Spark implementation. Authorization and validation are enforced again in firestore.rules. */
internal object SparkRepository {
    val db get() = Backend.firestore
    private val stamp get() = FieldValue.serverTimestamp()
    private fun uid() = Backend.auth.currentUser?.uid ?: error("로그인이 필요해요.")
    private fun id(value: Any?) = (value as? String)?.also { require(it.matches(Regex("[A-Za-z0-9_-]{1,128}"))) { "잘못된 문서 ID예요." } } ?: error("문서 ID가 필요해요.")
    private fun key(vararg values: String) = MessageDigest.getInstance("SHA-256").digest(values.joinToString(":").toByteArray()).joinToString("") { "%02x".format(it) }
    private fun normalize(value: Any?): Any? = when(value) {
        is Timestamp -> value.toDate().time
        is Map<*, *> -> value.entries.associate { it.key.toString() to normalize(it.value) }
        is List<*> -> value.map(::normalize)
        else -> value
    }
    @Suppress("UNCHECKED_CAST") private fun DocumentSnapshot.doc(): Doc = (normalize(data ?: emptyMap<String, Any?>()) as Doc) + ("id" to this.id)
    private suspend fun get(path: String): DocumentSnapshot = db.document(path).get(Source.SERVER).await()
    private suspend fun required(path: String) = get(path).also { check(it.exists()) { "대상을 찾을 수 없어요." } }
    private suspend fun me(): Doc {
        val user = get("users/${uid()}")
        if (!user.exists() || user.getBoolean("completed") != true) throw SignupRequiredException()
        return user.doc()
    }
    private suspend fun author(owner: String): Doc {
        val profile = get("publicProfiles/$owner")
        return mapOf("id" to owner, "nickname" to (profile.getString("nickname") ?: "탈퇴한 사용자"), "bio" to (profile.getString("bio") ?: ""))
    }
    private suspend fun blocked(owner: String): Boolean {
        val viewer = Backend.auth.currentUser?.uid ?: return false
        if(viewer == owner) return false
        return get("users/$viewer/blocks/$owner").exists() || get("users/$owner/blocks/$viewer").exists()
    }
    private suspend fun page(query: Query, p: Doc): Doc {
        var q = query.orderBy(FieldPath.documentId(), Query.Direction.DESCENDING)
        val cursor = p["cursor"] as? String
        if(!cursor.isNullOrBlank()) {
            val previous = get(cursor)
            check(previous.exists()) { "목록이 변경됐어요. 새로고침해 주세요." }
            q = q.startAfter(previous)
        }
        val result = q.limit(20).get(Source.SERVER).await()
        return mapOf("items" to result.documents.map { it.doc() }, "cursor" to if(result.size() == 20) result.documents.last().reference.path else null)
    }
    private suspend fun visiblePost(postId: String): Doc {
        val post = required("posts/$postId").doc()
        check(!post.bool("hidden") && !blocked(post.str("ownerId"))) { "글을 찾을 수 없어요." }
        return post
    }
    private suspend fun withAuthor(item: Doc): Doc = item + ("author" to author(item.str("ownerId")))
    private suspend fun comments(postId: String, p: Doc): Doc {
        visiblePost(postId)
        val result = page(db.collection("posts/$postId/comments").whereEqualTo("hidden", false).orderBy("createdAt", Query.Direction.DESCENDING), p)
        val items = mutableListOf<Doc>()
        for(item in result.rows()) if(!blocked(item.str("ownerId"))) items += withAuthor(item)
        return result + ("items" to items)
    }
    private suspend fun complete(p: Doc): Doc {
        val owner = uid(); val nickname = p.str("nickname").trim()
        require(Validation.profileErrors(nickname, p.bool("terms") && p.bool("privacy")).isEmpty()) { "닉네임과 필수 동의를 확인해 주세요." }
        db.runTransaction { tx ->
            val ref = db.document("users/$owner")
            if(tx.get(ref).exists()) return@runTransaction
            tx.set(ref, mapOf("nickname" to nickname, "bio" to "", "completed" to true, "termsVersion" to "1.0", "privacyVersion" to "1.0", "createdAt" to stamp, "consentedAt" to stamp))
            tx.set(db.document("publicProfiles/$owner"), mapOf("nickname" to nickname, "bio" to ""))
        }.await()
        return mapOf("ok" to true)
    }
    private suspend fun save(record: Boolean, p: Doc): Doc {
        val owner = uid(); me()
        Validation.content(p.str("title"), p.str("body"), record)?.let { error(it) }
        require(p.strings("media").isEmpty()) { "사진 업로드는 비활성화되어 있어요." }
        val categories = if(record) listOf("공연","방송","일상","굿즈") else listOf("자유 이야기","공연 후기")
        require(p.str("category") in categories) { "분류를 선택해 주세요." }
        val artist = id(p["artistId"])
        check(artist == "general" || required("artists/$artist").getBoolean("active") == true) { "활성 아티스트를 선택해 주세요." }
        val fields = mutableMapOf<String, Any?>("title" to p.str("title").trim(), "body" to p.str("body").trim(), "artistId" to artist, "category" to p.str("category"), "media" to emptyList<String>(), "updatedAt" to stamp)
        if(record) { LocalDate.parse(p.str("date")); fields["date"] = p.str("date") }
        val collection = if(record) "records" else "posts"
        val existing = p.str("id")
        val documentId = if(existing.isNotBlank()) id(existing) else key(owner, collection, id(p["requestKey"]))
        db.runTransaction { tx ->
            val ref = db.document("$collection/$documentId"); val old = tx.get(ref)
            if(existing.isBlank() && old.exists()) return@runTransaction
            if(existing.isNotBlank()) {
                check(old.exists() && old.getString("ownerId") == owner) { "수정할 수 없는 글이에요." }
                check(old.getLong("version") == p.num("expectedVersion")) { "다른 곳에서 변경됐어요. 새로고침 후 다시 시도해 주세요." }
                tx.update(ref, fields + ("version" to p.num("expectedVersion") + 1))
            } else {
                fields += mapOf("ownerId" to owner, "createdAt" to stamp, "version" to 1)
                if(!record) fields["hidden"] = false
                tx.set(ref, fields)
            }
        }.await()
        return mapOf("id" to documentId)
    }
    private suspend fun deleteContent(record: Boolean, p: Doc): Doc {
        val ref = db.document("${if(record) "records" else "posts"}/${id(p["id"])}")
        db.runTransaction { tx ->
            val old = tx.get(ref)
            check(old.getString("ownerId") == uid() && old.getLong("version") == p.num("expectedVersion")) { "최신 내용을 확인한 뒤 다시 시도해 주세요." }
            if(record) tx.delete(ref) else tx.update(ref, mapOf("hidden" to true, "title" to "삭제된 게시글", "body" to "삭제된 내용", "updatedAt" to stamp, "version" to p.num("expectedVersion") + 1))
        }.await()
        return mapOf("ok" to true)
    }
    suspend fun call(action: String, p: Doc): Doc = when(action) {
        "artists" -> mapOf("items" to db.collection("artists").whereEqualTo("active", true).get(Source.SERVER).await().documents.map { it.doc() }.filter { p.str("q").isBlank() || it.str("name").contains(p.str("q"), true) })
        "schedules" -> {
            val month = YearMonth.parse(p.str("month")); val zone = ZoneId.of("Asia/Seoul")
            val start = month.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
            val end = month.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
            mapOf("items" to db.collection("schedules").whereGreaterThanOrEqualTo("startAt", start).whereLessThan("startAt", end).orderBy("startAt").get(Source.SERVER).await().documents.map { it.doc() }.filter { p.str("artistId").isBlank() || it.str("artistId") == p.str("artistId") }, "updatedAt" to System.currentTimeMillis())
        }
        "schedule" -> required("schedules/${id(p["id"])}").doc()
        "completeSignup" -> complete(p)
        "me" -> me()
        "profile", "favorite" -> {
            val owner = uid(); val user = me()
            if(action == "favorite") {
                val artist = id(p["artistId"]); check(artist == "general" || required("artists/$artist").getBoolean("active") == true)
                db.document("users/$owner").update("artistId", artist).await()
            } else {
                require(Validation.nickname(p.str("nickname")) && p.str("bio").trim().length <= 100)
                val fields = mapOf("nickname" to p.str("nickname").trim(), "bio" to p.str("bio").trim())
                db.runBatch { it.update(db.document("users/$owner"), fields); it.set(db.document("publicProfiles/$owner"), fields) }.await()
            }
            mapOf("ok" to true)
        }
        "records" -> {
            var q: Query = db.collection("records").whereEqualTo("ownerId", uid())
            if(p.str("artistId").isNotBlank()) q = q.whereEqualTo("artistId", id(p["artistId"]))
            page(q.orderBy("date", Query.Direction.DESCENDING).orderBy("createdAt", Query.Direction.DESCENDING), p)
        }
        "record" -> required("records/${id(p["id"])}").doc()
        "posts" -> {
            var q: Query = db.collection("posts").whereEqualTo("hidden", false)
            if(p.str("artistId").isNotBlank()) q = q.whereEqualTo("artistId", id(p["artistId"]))
            if(p.str("category").isNotBlank()) q = q.whereEqualTo("category", p.str("category"))
            if(p.bool("authorMe")) q = q.whereEqualTo("ownerId", uid())
            val result = page(q.orderBy("createdAt", Query.Direction.DESCENDING), p)
            val items = mutableListOf<Doc>()
            for(item in result.rows()) if(!blocked(item.str("ownerId"))) items += withAuthor(item) + ("likeCount" to db.collection("posts/${item.str("id")}/likes").count().get(AggregateSource.SERVER).await().count)
            result + ("items" to items)
        }
        "post" -> {
            val postId = id(p["id"]); val post = visiblePost(postId)
            withAuthor(post) + mapOf("comments" to comments(postId, emptyMap()), "liked" to (Backend.auth.currentUser?.uid?.let { get("posts/$postId/likes/$it").exists() } ?: false), "likeCount" to db.collection("posts/$postId/likes").count().get(AggregateSource.SERVER).await().count)
        }
        "comments" -> comments(id(p["id"]), p)
        "saveRecord", "savePost" -> save(action == "saveRecord", p)
        "deleteRecord", "deletePost" -> deleteContent(action == "deleteRecord", p)
        "like" -> {
            val postId = id(p["id"]); visiblePost(postId)
            val ref = db.document("posts/$postId/likes/${uid()}")
            if(p.bool("enabled")) db.runTransaction { tx -> if(!tx.get(ref).exists()) tx.set(ref, mapOf("ownerId" to uid(), "createdAt" to stamp)) }.await() else ref.delete().await()
            mapOf("ok" to true)
        }
        "comment" -> {
            val owner = uid(); val postId = id(p["id"]); visiblePost(postId)
            val body = p.str("body").trim(); require(body.length in 1..500)
            val cid = key(owner, postId, id(p["requestKey"]))
            val ref = db.document("posts/$postId/comments/$cid")
            db.runTransaction { tx -> if(!tx.get(ref).exists()) tx.set(ref, mapOf("ownerId" to owner, "body" to body, "hidden" to false, "createdAt" to stamp)) }.await()
            mapOf("id" to cid)
        }
        "deleteComment" -> { db.document("posts/${id(p["postId"])}/comments/${id(p["id"])}").delete().await(); mapOf("ok" to true) }
        "blocks" -> {
            val list = db.collection("users/${uid()}/blocks").get(Source.SERVER).await().documents
            mapOf("items" to list.map { author(it.id) })
        }
        "block" -> {
            val target = id(p["userId"]); require(target != uid())
            val ref = db.document("users/${uid()}/blocks/$target")
            if(p.bool("enabled")) ref.set(mapOf("createdAt" to stamp)).await() else ref.delete().await()
            mapOf("ok" to true)
        }
        "report" -> {
            val owner = uid(); val postId = id(p["postId"] ?: p["targetId"]); val post = visiblePost(postId)
            val target = if(p.str("targetType") == "comment") required("posts/$postId/comments/${id(p["targetId"])}").doc() else post
            require(target.str("ownerId") != owner)
            val ref = db.collection("reports").document(key(owner, p.str("targetType"), id(p["targetId"])))
            db.runTransaction { tx -> if(!tx.get(ref).exists()) tx.set(ref, mapOf("reporterId" to owner, "targetType" to p.str("targetType"), "targetId" to id(p["targetId"]), "postId" to postId, "reason" to p.str("reason"), "detail" to p.str("detail").trim(), "status" to "pending", "createdAt" to stamp)) }.await()
            mapOf("id" to ref.id, "status" to "pending")
        }
        "logout" -> mapOf("ok" to true)
        "deleteAccount" -> deleteAccount()
        // Spark: photo uploads, device registration and scheduled notifications are disabled.
        "media", "notifications" -> mapOf("items" to emptyList<Doc>(), "urls" to emptyList<String>())
        else -> error("현재 버전에서 지원하지 않는 기능이에요.")
    }
    private suspend fun deleteAccount(): Doc {
        val owner = uid()
        // Each committed page is safe to retry. Do not remove Auth until all cleanup succeeds.
        suspend fun remove(query: Query) {
            while(true) {
                val docs = query.limit(100).get(Source.SERVER).await().documents
                if(docs.isEmpty()) break
                db.runBatch { batch -> docs.forEach { batch.delete(it.reference) } }.await()
            }
        }
        remove(db.collectionGroup("comments").whereEqualTo("ownerId", owner))
        remove(db.collectionGroup("likes").whereEqualTo("ownerId", owner))
        remove(db.collection("records").whereEqualTo("ownerId", owner))
        while(true) {
            val posts = db.collection("posts").whereEqualTo("ownerId", owner).whereEqualTo("hidden", false).limit(100).get(Source.SERVER).await().documents
            if(posts.isEmpty()) break
            db.runBatch { batch -> posts.forEach { batch.update(it.reference, mapOf("hidden" to true, "title" to "삭제된 게시글", "body" to "삭제된 내용", "updatedAt" to stamp, "version" to (it.getLong("version") ?: 1) + 1)) } }.await()
        }
        remove(db.collection("users/$owner/blocks"))
        db.runBatch { it.delete(db.document("publicProfiles/$owner")); it.delete(db.document("users/$owner")) }.await()
        Backend.auth.currentUser!!.delete().await()
        return mapOf("ok" to true)
    }
}
