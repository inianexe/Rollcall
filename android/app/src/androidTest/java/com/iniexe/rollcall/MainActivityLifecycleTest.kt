package com.iniexe.rollcall

import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage

@RunWith(AndroidJUnit4::class)
class MainActivityLifecycleTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private var scenario: ActivityScenario<MainActivity>? = null

    @After fun cleanTestState() {
        scenario?.close()
        scenario = null
        InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("settings put system font_scale 1.0").close()
        LiveSnapshotStore.setListener(null)
        LiveSnapshotStore.clear(context)
        context.getSharedPreferences("MainActivity", 0).edit().clear().commit()
    }

    @Test fun productionActivityStartsInOnboardingWithoutCachedAttendance() {
        resetState()
        val activity = launch()
        waitForStartup(activity)

        activity.onActivity {
            assertNotNull(findByText(it.window.decorView, "A little clarity.\nA better week."))
            assertNotNull(findByText(it.window.decorView, "Get started  →"))
            assertNull(findByDescription(it.window.decorView, "Courses, selected"))
        }
    }

    @Test fun dashboardThemeDataAndLifecycleWorkInProductionActivity() {
        resetState()
        // Test-only offline route fixture. It sets no credentials, cookies, consent or portal session.
        context.getSharedPreferences("MainActivity", 0).edit()
            .putBoolean("mycamu_connected", true)
            .putBoolean("mycamu_data_access_consent", false)
            .putBoolean("torch_html_palette_applied", true)
            .putBoolean("dark_theme", true)
            .putInt("attendance_target", 75)
            .commit()
        val activity = launch()
        waitForStartup(activity)
        assertSelected(activity, "Home")

        selectTab(activity, "Week")
        compose.onNodeWithText("Your week").assertIsDisplayed()
        compose.onNodeWithText("YOUR WEEK", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Sync timetable", substring = true).assertIsDisplayed()
        saveScreenshot("phase6b-week-dark.png")
        selectTab(activity, "Home")

        selectTab(activity, "Courses")
        compose.onNodeWithText("Your courses appear here after your first MyCamu sync. Tap to connect.")
            .assertIsDisplayed()
        LiveSnapshotStore.attendance(context, listOf(Attendance("CS101", "Programming Fundamentals", 16, 20)))
        compose.onNodeWithText("Programming Fundamentals").assertIsDisplayed()
        compose.onNodeWithContentDescription(
            "THEORY attendance for Programming Fundamentals  ·  CS101: 80%, can miss 1 classes, must attend 0 classes")
            .assertIsDisplayed()

        LiveSnapshotStore.attendance(context, listOf(
            Attendance("CS101", "Updated Programming Fundamentals", 12, 20),
            Attendance("MA101", "Calculus", 18, 20),
        ))
        compose.onNodeWithText("Updated Programming Fundamentals").assertIsDisplayed()
        compose.onNodeWithText("Calculus").assertIsDisplayed()
        assertEquals(12, LiveSnapshotStore.get().attendance.first().attended)
        compose.onNodeWithText("12").assertIsDisplayed()
        LiveSnapshotStore.attendance(context, listOf(Attendance("MA101", "Calculus", 18, 20)))
        compose.onNodeWithText("Calculus").assertIsDisplayed()
        assertEquals(0, compose.onAllNodesWithText("Updated Programming Fundamentals").fetchSemanticsNodes().size)
        LiveSnapshotStore.attendance(context, listOf(
            Attendance("CS101", "Updated Programming Fundamentals", 12, 20)))
        compose.onNodeWithText("Updated Programming Fundamentals").assertIsDisplayed()
        context.getSharedPreferences("MainActivity", 0).edit().putInt("attendance_target", 85).commit()
        activity.recreate()
        waitForStartup(activity)
        selectTab(activity, "Courses")
        compose.onNodeWithText("34").assertIsDisplayed()
        for (tab in listOf("Week", "What if?", "Marks and academics", "Home", "Courses")) selectTab(activity, tab)
        saveScreenshot("phase6a-courses-dark.png")

        selectTab(activity, "Home")
        activity.onActivity {
            val modeSwitch = findByDescription(it.window.decorView, "Switch to light mode")
            assertNotNull("Home mode switch should expose its current action", modeSwitch)
            modeSwitch!!.performClick()
        }
        waitForStartup(activity)
        assertEquals(false, context.getSharedPreferences("MainActivity", 0).getBoolean("dark_theme", true))
        activity.onActivity {
            assertNotNull("Java dashboard should reflect the persisted light mode",
                findByDescription(it.window.decorView, "Switch to dark mode"))
        }

        selectTab(activity, "Courses")
        compose.onNodeWithText("Updated Programming Fundamentals").assertIsDisplayed()
        saveScreenshot("phase6a-courses-light.png")
        selectTab(activity, "Week")
        compose.onNodeWithText("Your week").assertIsDisplayed()
        saveScreenshot("phase6b-week-light.png")
        activity.recreate()
        waitForStartup(activity)
        assertEquals(false, context.getSharedPreferences("MainActivity", 0).getBoolean("dark_theme", true))
        assertSelected(activity, "Home") // Existing behavior: selected tab is not persisted across Activity recreation.
        selectTab(activity, "Week")
        compose.onNodeWithText("Your week").assertIsDisplayed()
        compose.onNodeWithContentDescription("Show Attendance").performClick()
        compose.onNodeWithContentDescription("Open Leave and On Duty details").performClick()
        InstrumentationRegistry.getInstrumentation().uiAutomation
            .performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
        compose.onNodeWithText("Attendance history").assertIsDisplayed()
        selectTab(activity, "Courses")
        compose.onNodeWithText("Updated Programming Fundamentals").assertIsDisplayed()

        activity.moveToState(androidx.lifecycle.Lifecycle.State.CREATED)
        activity.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
        waitForStartup(activity)
        selectTab(activity, "Courses")
        compose.onNodeWithText("Updated Programming Fundamentals").assertIsDisplayed()

        activity.recreate()
        waitForStartup(activity)
        activity.recreate()
        waitForStartup(activity)
        assertSelected(activity, "Home")
        selectTab(activity, "Courses")
        compose.onNodeWithText("Updated Programming Fundamentals").assertIsDisplayed()
        InstrumentationRegistry.getInstrumentation().uiAutomation
            .performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
        compose.waitUntil(5_000) {
            var mainActivityResumed = false
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                mainActivityResumed = ActivityLifecycleMonitorRegistry.getInstance()
                    .getActivitiesInStage(Stage.RESUMED).any { it is MainActivity }
            }
            !mainActivityResumed
        }
    }

    @Test fun coursesRemainAccessibleAtSystemFontScales() {
        resetState()
        context.getSharedPreferences("MainActivity", 0).edit()
            .putBoolean("mycamu_connected", true)
            .putBoolean("mycamu_data_access_consent", false)
            .putBoolean("torch_html_palette_applied", true)
            .putBoolean("dark_theme", false)
            .commit()
        val weekStartsAt = System.currentTimeMillis() + 3_600_000L
        LiveSnapshotStore.timetable(context, listOf(LiveSnapshotStore.ClassSession(
            "week-large-font", "WEEK-101", "Advanced Distributed Systems and Accessible Interface Design",
            "Faculty member", "Room 204", "lecture", weekStartsAt, weekStartsAt + 3_600_000L, false, false)))
        val activity = launch()
        waitForStartup(activity)
        LiveSnapshotStore.attendance(context, listOf(Attendance(
            "CS999", "Advanced Programming, Embedded Systems and Applied Computing", 99_999, 100_000)))
        selectTab(activity, "Courses")

        val description = "THEORY attendance for Advanced Programming, Embedded Systems and Applied Computing  ·  CS999: 100%, can miss 33332 classes, must attend 0 classes"
        try {
            for (scale in listOf(1.0f, 1.3f, 1.5f, 2.0f)) {
                InstrumentationRegistry.getInstrumentation().uiAutomation
                    .executeShellCommand("settings put system font_scale $scale").close()
                activity.recreate()
                waitForStartup(activity)
                selectTab(activity, "Week")
                compose.onNodeWithText("Advanced Distributed Systems and Accessible Interface Design  ·  WEEK-101")
                    .performScrollTo().assertIsDisplayed()
                compose.onNodeWithText("WEEK-101", substring = true).assertIsDisplayed()
                saveScreenshot("phase6b-week-font-${scale}.png")
                selectTab(activity, "Courses")
                compose.onNodeWithContentDescription("Search by subject or code").assertIsDisplayed()
                compose.onNodeWithText("All courses · 1").assertIsDisplayed()
                compose.onNodeWithContentDescription(description).performScrollTo().assertIsDisplayed()
                compose.onNodeWithText("33332").performScrollTo().assertIsDisplayed()
                saveScreenshot("phase6a-courses-font-${scale}.png")
            }
        } finally {
            InstrumentationRegistry.getInstrumentation().uiAutomation
                .executeShellCommand("settings put system font_scale 1.0").close()
            activity.recreate()
            waitForStartup(activity)
        }
    }

    private fun resetState() {
        LiveSnapshotStore.setListener(null)
        LiveSnapshotStore.clear(context)
        context.getSharedPreferences("MainActivity", 0).edit().clear().commit()
    }

    private fun launch(): ActivityScenario<MainActivity> =
        ActivityScenario.launch(MainActivity::class.java).also { scenario = it }

    private fun waitForStartup(activity: ActivityScenario<MainActivity>) {
        compose.waitUntil(12_000) {
            var finished = false
            activity.onActivity {
                finished = findByDescription(it.window.decorView,
                    "Rollcall startup animation. Tap to continue.") == null
            }
            finished
        }
        compose.waitForIdle()
    }

    private fun selectTab(activity: ActivityScenario<MainActivity>, name: String) {
        activity.onActivity {
            val tab = findByDescription(it.window.decorView, name)
                ?: findByDescription(it.window.decorView, "$name, selected")
            assertNotNull("Missing dashboard tab $name", tab)
            tab!!.performClick()
        }
        compose.waitForIdle()
        assertSelected(activity, name)
    }

    private fun assertSelected(activity: ActivityScenario<MainActivity>, name: String) {
        activity.onActivity {
            assertNotNull("$name should be the selected dashboard tab",
                findByDescription(it.window.decorView, "$name, selected"))
        }
    }

    private fun findByDescription(view: View, description: String): View? {
        if (view.contentDescription == description) return view
        if (view is ViewGroup) for (index in 0 until view.childCount) {
            findByDescription(view.getChildAt(index), description)?.let { return it }
        }
        return null
    }

    private fun findByText(view: View, text: String): View? {
        if (view is android.widget.TextView && view.text.toString() == text) return view
        if (view is ViewGroup) for (index in 0 until view.childCount) {
            findByText(view.getChildAt(index), text)?.let { return it }
        }
        return null
    }

    private fun saveScreenshot(name: String) {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        automation.executeShellCommand("mkdir -p /sdcard/Pictures/RollcallQA").close()
        android.os.ParcelFileDescriptor.AutoCloseInputStream(
            automation.executeShellCommand("screencap -p /sdcard/Pictures/RollcallQA/$name")
        ).use { it.readBytes() }
    }
}
