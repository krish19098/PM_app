package com.pmbible.app

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.pmbible.app.ui.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * End-to-end smoke test on the JVM: onboarding → Today plan → Library search.
 * SDK 32 avoids the Android 13 notification-permission dialog, which Robolectric doesn't answer.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32])
class AppFlowTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun onboardingLeadsToTodayAndLibrarySearchWorks() {
        compose.waitUntilAtLeastOneExists(hasText("Welcome to PM Bible"), 10_000)
        compose.onNodeWithText("What should we call you? (optional)").performTextInput("Krish")
        repeat(5) { compose.onNodeWithText("Next").performClick() }
        compose.onNodeWithText("Start learning").performClick()

        compose.waitUntilAtLeastOneExists(hasText("Today's session"), 10_000)
        // The first lesson appears once per planned part (Quick Learn, check, exercise).
        compose.onAllNodesWithText("What a Product Manager does").onFirst().assertExists()

        compose.onNodeWithText("Library").performClick()
        compose.waitUntilAtLeastOneExists(hasText("PM Library"), 5_000)
        compose.onNodeWithText("Search any PM topic", substring = true).performTextInput("north star")
        compose.waitUntilAtLeastOneExists(hasText("The North Star Metric"), 5_000)
    }
}
