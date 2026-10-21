package com.portfolio.choaezip

import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.os.Bundle
import com.google.firebase.messaging.RemoteMessage
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32], application = Application::class)
class PushServiceTest {
    @Test fun consoleNotificationDisplaysWithoutLoginOrBackend() {
        val service = Robolectric.buildService(PushService::class.java).create().get()
        val payload = Bundle().apply {
            putString("gcm.n.e", "1")
            putString("gcm.n.title", "콘솔 테스트")
            putString("gcm.n.body", "메시지가 도착했어요")
            putString("google.message_id", "console-test")
        }
        service.onMessageReceived(RemoteMessage(payload))
        val manager = service.getSystemService(NotificationManager::class.java)
        val notification = shadowOf(manager).allNotifications.single()
        assertEquals("콘솔 테스트", notification.extras.getString(Notification.EXTRA_TITLE))
        assertEquals("메시지가 도착했어요", notification.extras.getString(Notification.EXTRA_TEXT))
        assertEquals(PushChannels.CONSOLE, notification.channelId)
        assertNotNull(manager.getNotificationChannel(PushChannels.CONSOLE))
    }
    @Test fun unaddressedPrivateDataIsNeverDisplayed() {
        val service = Robolectric.buildService(PushService::class.java).create().get()
        service.onMessageReceived(RemoteMessage.Builder("device").addData("title", "private").build())
        assertTrue(shadowOf(service.getSystemService(NotificationManager::class.java)).allNotifications.isEmpty())
    }
}
