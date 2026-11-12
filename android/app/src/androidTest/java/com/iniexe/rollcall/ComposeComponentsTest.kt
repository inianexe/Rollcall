package com.iniexe.rollcall

import android.animation.ValueAnimator
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.iniexe.rollcall.ui.animation.lottie.RollcallLottie
import com.iniexe.rollcall.ui.screens.home.HomeHeroIdentity
import com.iniexe.rollcall.ui.screens.home.HomeHeroIdentityState
import com.iniexe.rollcall.ui.screens.home.LatestClassAttendanceCard
import com.iniexe.rollcall.ui.screens.home.LatestClassAttendanceState
import com.iniexe.rollcall.ui.screens.home.UpcomingExamCard
import com.iniexe.rollcall.ui.screens.home.UpcomingExamCardState
import com.iniexe.rollcall.ui.theme.RollcallTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ComposeComponentsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun homeHeroRendersDynamicIdentityAndLongName() {
        val longName = "A VERY LONG STUDENT NAME THAT SHOULD ELLIPSIZE IN THE HERO"
        compose.setContent {
            RollcallTheme(AppPalette.get(false, 0).toComposeColors()) {
                HomeHeroIdentity(HomeHeroIdentityState(
                    "Good afternoon,", longName, "THU / 12 NOV", "46", "NOVEMBER", "ONE CLASS AT A TIME."))
            }
        }

        compose.onNodeWithText("Good afternoon,").assertIsDisplayed()
        compose.onNodeWithText(longName).assertIsDisplayed()
        compose.onNodeWithText("THU / 12 NOV").assertIsDisplayed()
        compose.onNodeWithText("46").assertIsDisplayed()
    }

    @Test fun upcomingExamRendersContentAndAccessibilityDescription() {
        val description = "Upcoming exam: Programming in C, theory, session AN, in 6 days, Wed 18 Nov"
        compose.setContent {
            RollcallTheme(AppPalette.get(false, 0).toComposeColors()) {
                UpcomingExamCard(examState(description))
            }
        }

        compose.onNodeWithText("Programming in C").assertIsDisplayed()
        compose.onNodeWithText("THEORY").assertIsDisplayed()
        compose.onNodeWithText("AN").assertIsDisplayed()
        compose.onNodeWithText("In 6 days").assertIsDisplayed()
        compose.onNodeWithContentDescription(description).assertIsDisplayed()
    }

    @Test fun noUpcomingExamShowsClearEmptyState() {
        compose.setContent {
            RollcallTheme(AppPalette.get(false, 0).toComposeColors()) {
                UpcomingExamCard(UpcomingExamCardState(false, "No upcoming exam found", "", "", "", "",
                    "Refresh KCT from Marks & academics to check the latest schedule.",
                    "No upcoming exam found. Refresh KCT from Marks and academics to check the latest schedule."))
            }
        }

        compose.onNodeWithText("No upcoming exam found").assertIsDisplayed()
        compose.onNodeWithText("Refresh KCT from Marks & academics to check the latest schedule.").assertIsDisplayed()
    }

    @Test fun longExamTitleAndStateChangesRender() {
        val longTitle = "Advanced Programming and Embedded Systems Practical Examination"
        var state by mutableStateOf(examState("Long exam description"))
        compose.setContent {
            RollcallTheme(AppPalette.get(false, 0).toComposeColors()) { UpcomingExamCard(state) }
        }
        compose.runOnIdle { state = state.copy(title = longTitle, countdown = "Tomorrow") }

        compose.onNodeWithText(longTitle).assertIsDisplayed()
        compose.onNodeWithText("Tomorrow").assertIsDisplayed()
    }

    @Test fun activeThemeChangeRepaintsVisibleComposeCard() {
        var colors by mutableStateOf(AppPalette.get(false, 0).toComposeColors())
        compose.setContent {
            Box(Modifier.fillMaxSize().background(Color(colors.background))) {
                RollcallTheme(colors) {
                    UpcomingExamCard(examState("Theme test"), Modifier.testTag("exam-card"))
                }
            }
        }
        val light = compose.onNodeWithTag("exam-card").captureToImage().toPixelMap()
        compose.runOnIdle { colors = AppPalette.get(true, 0).toComposeColors() }
        val dark = compose.onNodeWithTag("exam-card").captureToImage().toPixelMap()

        var changed = false
        for (y in 0 until minOf(light.height, dark.height) step 4) {
            for (x in 0 until minOf(light.width, dark.width) step 4) {
                if (light[x, y] != dark[x, y]) changed = true
            }
        }
        assertTrue("Palette update should repaint the visible card", changed)
    }

    @Test fun latestClassUsesSemanticPresentAndAbsentLabelsAndAction() {
        var state by mutableStateOf(LatestClassAttendanceState(
            "Programming in C", "Present", "present", "Latest class attendance. Programming in C: Present"))
        var clicked = false
        compose.setContent {
            RollcallTheme(AppPalette.get(false, 0).toComposeColors()) {
                LatestClassAttendanceCard(state, onClick = { clicked = true })
            }
        }
        compose.onNodeWithText("Present").assertIsDisplayed().performClick()
        assertTrue(clicked)
        compose.runOnIdle {
            state = state.copy(statusLabel = "Absent", status = "absent", accessibleDescription = "Latest class attendance: Absent")
        }
        compose.onNodeWithText("Absent").assertIsDisplayed()
        compose.onNodeWithContentDescription("Latest class attendance: Absent").assertIsDisplayed()
    }

    @Test fun latestClassCardSurvivesDisposalAndRecreation() {
        var mounted by mutableStateOf(true)
        compose.setContent {
            RollcallTheme(AppPalette.get(true, 0).toComposeColors()) {
                if (mounted) LatestClassAttendanceCard(
                    LatestClassAttendanceState("Physics", "Not marked yet", "not_taken", "Latest class attendance"), {})
            }
        }
        compose.onNodeWithText("Physics").assertIsDisplayed()
        compose.runOnIdle { mounted = false }
        compose.runOnIdle { mounted = true }
        compose.onNodeWithText("Physics").assertIsDisplayed()
    }

    @Test fun lottieLoadsPlaysPausesWhenHiddenAndRestartsAfterCompletion() {
        var visible by mutableStateOf(false)
        var playing by mutableStateOf(true)
        var mounted by mutableStateOf(true)
        var loadCount = 0
        var finishedCount = 0
        compose.setContent {
            RollcallTheme(AppPalette.get(false, 0).toComposeColors()) {
                if (mounted) RollcallLottie(
                    animationRes = R.raw.rollcall_compose_test,
                    modifier = Modifier.size(64.dp).testTag("test-lottie"),
                    isPlaying = playing,
                    isVisible = visible,
                    onCompositionLoaded = { loadCount++ },
                    onAnimationFinished = { finishedCount++ },
                )
            }
        }
        compose.waitUntil(10_000) { loadCount == 1 }
        compose.mainClock.autoAdvance = false
        val pausedImage = compose.onNodeWithTag("test-lottie").captureToImage().toPixelMap()
        val centerX = pausedImage.width / 2
        val centerY = pausedImage.height / 2
        val paused = pausedImage[centerX, centerY]
        compose.mainClock.advanceTimeBy(500)
        val stillPaused = compose.onNodeWithTag("test-lottie").captureToImage().toPixelMap()
        assertEquals(paused, stillPaused[centerX, centerY])

        compose.runOnIdle { visible = true }
        compose.mainClock.advanceTimeBy(500)
        compose.waitForIdle()
        if (ValueAnimator.areAnimatorsEnabled()) {
            val playingPixels = compose.onNodeWithTag("test-lottie").captureToImage().toPixelMap()
            assertFalse("Original test animation should move while visible", paused == playingPixels[centerX, centerY])
        }
        compose.mainClock.advanceTimeBy(800)
        compose.waitForIdle()
        assertEquals(1, finishedCount)

        compose.mainClock.autoAdvance = true
        compose.runOnIdle { mounted = false }
        compose.runOnIdle { mounted = true }
        compose.waitUntil(10_000) { loadCount == 2 }
        compose.mainClock.autoAdvance = false
        compose.mainClock.advanceTimeBy(1_100)
        compose.waitForIdle()
        assertEquals("Remounted animation should play and finish", 2, finishedCount)
    }

    @Test fun lottieMissingResourceReportsFailure() {
        var failed = false
        compose.setContent {
            RollcallLottie(animationRes = 0, isPlaying = false, onLoadError = { failed = true })
        }
        compose.waitUntil(10_000) { failed }
        assertTrue(failed)
    }

    private fun examState(description: String) = UpcomingExamCardState(
        isUpcoming = true,
        title = "Programming in C",
        type = "THEORY",
        session = "AN",
        countdown = "In 6 days",
        date = "Wed 18 Nov",
        details = "CS101 · Embedded assessment",
        accessibleDescription = description,
    )
}
