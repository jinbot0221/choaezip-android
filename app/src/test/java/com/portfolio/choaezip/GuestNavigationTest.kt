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
}
