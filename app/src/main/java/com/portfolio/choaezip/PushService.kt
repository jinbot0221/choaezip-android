package com.portfolio.choaezip

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object PushChannels {
    const val CONSOLE = "announcements"
    fun create(context: Context) {
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CONSOLE, "공지와 테스트 알림", NotificationManager.IMPORTANCE_DEFAULT)
        )
    }
}

class PushService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        FirebaseTrace.event("fcm.token.refresh", "OK", "backendConfigured=${Backend.configured}")
        if (!Backend.configured || Backend.auth.currentUser == null) return
        val prefs = getSharedPreferences("choaezip", Context.MODE_PRIVATE)
        val installation = prefs.getString("installation", null) ?: return
        CoroutineScope(Dispatchers.IO).launch {
            runCatching { Backend.call("device", "installationId" to installation, "token" to token, "permission" to (Build.VERSION.SDK_INT < 33 || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)) }.onFailure { if(it is Exception) FirebaseTrace.failure("fcm.device.register", it) }
        }
    }
    override fun onMessageReceived(message: RemoteMessage) {
        FirebaseTrace.event("fcm.receive", "OK", "notificationPayload=${message.notification != null} dataPayload=${message.data.isNotEmpty()}")
        // Console notification messages have no app-specific recipient/expiry data.
        // Private data-only notifications still use the checks below.
        message.notification?.let { notice ->
            if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) { FirebaseTrace.event("fcm.display", "SKIPPED", "reason=notification_permission_denied"); return }
            PushChannels.create(this)
            val notificationId = message.messageId?.hashCode() ?: java.util.UUID.randomUUID().hashCode()
            val intent = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            val pending = PendingIntent.getActivity(this, notificationId, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val notification = NotificationCompat.Builder(this, PushChannels.CONSOLE)
                .setSmallIcon(R.drawable.ic_notification).setContentTitle(notice.title ?: "최애.zip")
                .setContentText(notice.body ?: "새 소식이 도착했어요.")
                .setStyle(NotificationCompat.BigTextStyle().bigText(notice.body))
                .setContentIntent(pending).setAutoCancel(true).build()
            val manager = getSystemService(NotificationManager::class.java)
            FirebaseTrace.event("fcm.notification.settings", "INFO", "notificationsEnabled=${manager.areNotificationsEnabled()} channelImportance=${manager.getNotificationChannel(PushChannels.CONSOLE)?.importance}")
            manager.notify(notificationId, notification)
            FirebaseTrace.event("fcm.display", "SUBMITTED", "channel=announcements")
            return
        }
        if(!Backend.configured) { FirebaseTrace.event("fcm.data", "SKIPPED", "reason=backend_not_configured"); return }
        val d=message.data
        val uid=Backend.auth.currentUser?.uid ?: run { FirebaseTrace.event("fcm.data", "SKIPPED", "reason=signed_out"); return }
        if(d["recipientRef"] != uid || (d["expiresAt"]?.toLongOrNull() ?: 0L) <= System.currentTimeMillis()) { FirebaseTrace.event("fcm.data", "SKIPPED", "reason=recipient_mismatch_or_expired"); return }
        if(Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED) return
        CoroutineScope(Dispatchers.IO).launch {
            // Recheck account and target at receipt; stale or blocked data is never displayed.
            runCatching {
                Backend.call("me")
                val type=d["entityType"] ?: ""
                if(type in listOf("post","record","schedule")) Backend.call(type,"id" to d["entityId"])
                val manager=getSystemService(NotificationManager::class.java)
                val group=when(type){"schedule"->"schedule";"post"->"community";"record"->"memory";else->"account"}
                manager.createNotificationChannel(NotificationChannel(group,when(group){"schedule"->"일정 안내";"community"->"댓글과 좋아요";"memory"->"기록 회고";else->"계정 안내"},NotificationManager.IMPORTANCE_DEFAULT))
                val intent=Intent(this@PushService,MainActivity::class.java).putExtra("notificationId",d["notificationId"]).putExtra("recipientRef",uid).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                val notificationId=d["notificationId"].hashCode()
                val pending=PendingIntent.getActivity(this@PushService,notificationId,intent,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                val notification=NotificationCompat.Builder(this@PushService,group).setSmallIcon(com.portfolio.choaezip.R.drawable.ic_notification).setContentTitle(d["title"]?:"최애.zip").setContentText(d["body"]?:"새 소식이 도착했어요.").setContentIntent(pending).setAutoCancel(true).setTimeoutAfter((d["expiresAt"]!!.toLong()-System.currentTimeMillis()).coerceAtLeast(1)).build()
                manager.notify(notificationId,notification)
                FirebaseTrace.event("fcm.display", "SUBMITTED", "channel=$group")
            }.onFailure { if(it is Exception) FirebaseTrace.failure("fcm.data.validate", it) }
        }
    }
}
