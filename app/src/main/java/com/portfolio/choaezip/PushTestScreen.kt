package com.portfolio.choaezip

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

@Composable fun PushTestScreen(vm: AppViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var token by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var permissionGranted by remember { mutableStateOf(Build.VERSION.SDK_INT < 33 || context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        permissionGranted = it; vm.syncDevice()
    }
    Heading("이 기기로 알림 보내기")
    Caption("Firebase 콘솔에서 테스트 메시지를 보낼 때 아래 기기 토큰을 사용하세요. 로그인하지 않아도 수신할 수 있어요.")
    Text(if(permissionGranted) "알림 권한이 허용되어 있어요." else "알림 권한을 허용해 주세요.")
    TextButton(onClick = {
        if(Build.VERSION.SDK_INT >= 33 && !permissionGranted) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        else context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
    }) { Text("알림 권한 설정") }
    Primary("기기 토큰 가져오기", !loading) {
        scope.launch {
            loading = true; error = null
            try {
                check(!BuildConfig.USE_EMULATORS) { "실제 Firebase를 사용하는 앱에서 테스트해 주세요." }
                token = FirebaseMessaging.getInstance().token.await()
                vm.syncDevice()
            } catch(e: Exception) {
                if(e is kotlinx.coroutines.CancellationException) throw e
                error = "토큰을 가져오지 못했어요. 인터넷 연결과 Google Play 서비스, Firebase 설정을 확인해 주세요."
            } finally { loading = false }
        }
    }
    error?.let { Text(it) }
    if(token.isNotEmpty()) {
        androidx.compose.foundation.text.selection.SelectionContainer { Text(token) }
        TextButton(onClick = {
            context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("FCM token", token))
            vm.message = "기기 토큰을 복사했어요."
        }) { Text("기기 토큰 복사") }
    }
    Caption("Firebase 콘솔 → Messaging → 알림 메시지 작성 → 테스트 메시지 전송에서 복사한 토큰을 입력하세요. 앱을 연 상태와 홈 화면으로 나간 상태에서 각각 확인할 수 있어요. 강제 종료한 앱은 다시 실행해야 해요.")
}
