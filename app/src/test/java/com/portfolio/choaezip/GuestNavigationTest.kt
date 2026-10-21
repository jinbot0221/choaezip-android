package com.portfolio.choaezip

import androidx.activity.ComponentActivity
import androidx.compose.runtime.remember
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = ChoaezipApplication::class)
class GuestNavigationTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private fun launch() {
        compose.setContent { MaterialTheme { ChoaezipApp(remember { AppViewModel() }) } }
    }
    @Test fun guestCanOpenRecordsButCannotWriteWithoutLogin() {
        launch()
        compose.onNodeWithText("개발 환경 · Firebase Emulator").assertDoesNotExist()
        compose.onNodeWithContentDescription("기록", useUnmergedTree = true).performClick()
        compose.onNodeWithText("소중한 순간을 남겨보세요").assertIsDisplayed()
        compose.onNodeWithText("로그인하기").performScrollTo().performClick()
        compose.onNodeWithText("Google로 계속하기").assertIsDisplayed()
        compose.onNodeWithText("카카오로 계속하기").assertDoesNotExist()
        compose.onNodeWithText("Apple로 계속하기").assertDoesNotExist()
    }
    @Test fun guestCanExploreMyPageAndReturnHome() {
        launch()
        compose.onNodeWithContentDescription("마이", useUnmergedTree = true).performClick()
        compose.onNodeWithText("좋아하는 마음의 보관함").assertIsDisplayed()
        compose.onNodeWithContentDescription("홈", useUnmergedTree = true).performClick()
        compose.onNodeWithText("좋아하는 마음을\n차곡차곡 모아봐요").assertIsDisplayed()
    }
    @Test fun signupShowsSpecificValidationAndAllowsTappingConsentText() {
        compose.setContent { MaterialTheme { ChoaezipApp(remember { AppViewModel().apply { navigate("signup") } }) } }
        compose.onNodeWithText("닉네임 (2~20자)").performScrollTo().performTextInput("테스터")
        compose.onNodeWithText("이메일", substring=false).performScrollTo().performTextInput("fan@example.com")
        compose.onNodeWithText("비밀번호 (8~64자)").performScrollTo().performTextInput("short")
        compose.onNodeWithText("비밀번호 확인").performScrollTo().performTextInput("short")
        compose.onNodeWithText("[필수] 이용약관 및 개인정보 처리에 동의").performScrollTo().performClick()
        compose.onNodeWithText("[필수] 이용약관 및 개인정보 처리에 동의").assertIsOn()
        compose.onNode(hasText("가입 완료") and hasClickAction()).performScrollTo().performClick()
        compose.onAllNodesWithText("비밀번호는 8~64자로 입력해 주세요.").assertCountEquals(2)
    }
}
