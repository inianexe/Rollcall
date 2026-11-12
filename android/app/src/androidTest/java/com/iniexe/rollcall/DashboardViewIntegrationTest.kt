package com.iniexe.rollcall

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.swipeDown
import android.view.View
import android.view.ViewGroup
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import android.os.SystemClock
import android.animation.ValueAnimator
import java.io.File
import java.io.FileInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DashboardViewIntegrationTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun profileCoursesInteractions() {
        val rows = (0 until 24).flatMap { index ->
            val code = "P%03d".format(index)
            listOf(testCourse(code, "Long Course Name $index Applied Computing", if (index % 3 == 0) 60f else 82f, index % 3 == 0),
                testCourse("${code}L", "Long Course Name $index Lab", if (index % 4 == 0) 55f else 80f, index % 4 == 0))
        }
        lateinit var dashboard: DashboardView
        val state = DashboardView.State().apply {
            attendanceLoaded = true
            target = 75
            courses = rows.size
            below = rows.count { it.below }
            this.rows = rows.toMutableList()
        }
        dashboard = DashboardView(compose.activity, object : DashboardView.Actions {
            override fun onSync() = Unit
            override fun onPortal() = Unit
            override fun onAcademicsPortal() = Unit
            override fun onTarget() = Unit
            override fun onTheme() = Unit
            override fun onPalette() = Unit
            override fun onAbout() = Unit
            override fun onFilter(belowOnly: Boolean) = Unit
            override fun onQuery(value: String) = Unit
            override fun onCourse(code: String) {
                state.rows.firstOrNull { it.code == code }?.let { it.expanded = !it.expanded }
                dashboard.update(state)
            }
        }, AppPalette.get(false, 0))
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            compose.activity.setContentView(dashboard)
            dashboard.update(state)
        }
        captureProfile("before")
        repeat(3) { pass ->
            clickTab(dashboard, "Courses")
            repeat(4) { compose.onNode(hasScrollAction()).performTouchInput { swipeUp() } }
            repeat(8) { compose.onNode(hasScrollAction()).performTouchInput { swipeDown() } }
            compose.onNodeWithContentDescription("Search by subject or code").performTextInput("Name 17")
            compose.onNodeWithText("Long Course Name 17 Applied Computing").assertIsDisplayed()
            compose.onNodeWithContentDescription("Clear search").performClick()
            compose.onNodeWithText("Below target · ${state.below}").performClick()
            compose.onNodeWithText("All courses · ${state.courses}").performClick()
            repeat(8) { compose.onNode(hasScrollAction()).performTouchInput { swipeDown() } }
            instrumentation.runOnMainSync { state.rows.forEach { it.expanded = false }; dashboard.update(state) }
            compose.onAllNodesWithText("LAB ·", substring = true).onFirst().performClick()
            compose.onNodeWithText("Try in What if?  →").assertIsDisplayed()
            clickTab(dashboard, "Home")
            if (pass < 2) clickTab(dashboard, "Week")
        }
        clickTab(dashboard, "Courses")
        captureProfile("after")
    }

    private fun captureProfile(stage: String) {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val directory = File(compose.activity.getExternalFilesDir("phase6b-profile"), "")
        directory.mkdirs()
        for ((name, command) in listOf(
            "memory" to "dumpsys meminfo com.iniexe.rollcall",
            "frames" to "dumpsys gfxinfo com.iniexe.rollcall framestats")) {
            automation.executeShellCommand(command).use { source ->
                File(directory, "$stage-$name.txt").outputStream().use { target ->
                    FileInputStream(source.fileDescriptor).use { it.copyTo(target) }
                }
            }
        }
    }

    @Test fun realDashboardHostsComposeCourseCardAndNavigatesAcrossTabs() {
        val clickedCourses = mutableListOf<String>()
        val dashboard = DashboardView(compose.activity, object : DashboardView.Actions {
            override fun onSync() = Unit
            override fun onPortal() = Unit
            override fun onAcademicsPortal() = Unit
            override fun onTarget() = Unit
            override fun onTheme() = Unit
            override fun onPalette() = Unit
            override fun onAbout() = Unit
            override fun onFilter(belowOnly: Boolean) = Unit
            override fun onQuery(value: String) = Unit
            override fun onCourse(code: String) { clickedCourses += code }
        }, AppPalette.get(false, 0))
        val state = DashboardView.State().apply {
            attendanceLoaded = true
            rows = mutableListOf(DashboardView.Course().apply {
                code = "CS101"
                name = "Programming Fundamentals"
                percent = 80f
                percentLabel = "80%"
                attended = 16
                total = 20
                countsKnown = true
                status = "Above target"
                guidance = "You can miss 1 more class"
                counts = "16 attended / 20 total"
                canMissText = "1"
                mustAttendText = "0"
                targetCount = "Can miss 1"
                color = 0xff176146.toInt()
            })
        }
        compose.activity.runOnUiThread {
            compose.activity.setContentView(dashboard)
            dashboard.update(state)
        }

        clickTab(dashboard, "Courses")
        assertTabSelected(dashboard, "Courses, selected")
        compose.onNodeWithText("Programming Fundamentals").assertIsDisplayed()
        compose.onNodeWithText("CAN MISS").assertIsDisplayed()
        compose.onNodeWithText("1").assertIsDisplayed()
        compose.activity.runOnUiThread {
            state.rows[0].name = "Refreshed Programming Fundamentals"
            dashboard.update(state)
        }
        compose.onNodeWithText("Refreshed Programming Fundamentals").assertIsDisplayed()
        compose.onNodeWithText("THEORY · CS101").performClick()
        compose.waitUntil(5_000) { clickedCourses.size == 1 }
        assertEquals("CS101", clickedCourses.single())
        compose.activity.runOnUiThread {
            state.rows[0].expanded = true
            dashboard.update(state)
        }
        compose.onNodeWithText("Above target. You can miss 1 more class").assertIsDisplayed()
        compose.onNodeWithText("Try in What if?  →").performClick()
        assertTabSelected(dashboard, "What if?, selected")

        clickTab(dashboard, "Week")
        assertTabSelected(dashboard, "Week, selected")
        clickTab(dashboard, "What if?")
        assertTabSelected(dashboard, "What if?, selected")
        clickTab(dashboard, "Marks and academics")
        assertTabSelected(dashboard, "Marks and academics, selected")
        clickTab(dashboard, "Home")
        assertTabSelected(dashboard, "Home, selected")

    }

    @Test fun coursesScreenKeepsJavaGroupingSearchAndFilterBehavior() {
        val dashboard = DashboardView(compose.activity, object : DashboardView.Actions {
            override fun onSync() = Unit
            override fun onPortal() = Unit
            override fun onAcademicsPortal() = Unit
            override fun onTarget() = Unit
            override fun onTheme() = Unit
            override fun onPalette() = Unit
            override fun onAbout() = Unit
            override fun onFilter(belowOnly: Boolean) = Unit
            override fun onQuery(value: String) = Unit
            override fun onCourse(code: String) = Unit
        }, AppPalette.get(false, 0))
        val state = DashboardView.State().apply {
            attendanceLoaded = true
            target = 75
            courses = 4
            below = 2
            rows = mutableListOf(
                testCourse("CS101", "Programming Fundamentals", 80f, false),
                testCourse("CS101L", "Programming Fundamentals Lab", 60f, true),
                testCourse("MA101", "Calculus", 75f, false),
                testCourse("EE201L", "Digital Circuits Lab", 50f, true),
            )
        }
        compose.activity.runOnUiThread {
            compose.activity.setContentView(dashboard)
            dashboard.update(state)
        }
        clickTab(dashboard, "Courses")

        compose.onNodeWithText("Programming Fundamentals").assertIsDisplayed()
        compose.onNodeWithText("THEORY · CS101").assertIsDisplayed()
        compose.onNodeWithText("LAB · CS101L").assertIsDisplayed()
        compose.onNodeWithText("Digital Circuits Lab").assertIsDisplayed()
        val labOnlyTop = compose.onNodeWithText("Digital Circuits Lab").fetchSemanticsNode().boundsInRoot.top
        val pairedTop = compose.onNodeWithText("Programming Fundamentals").fetchSemanticsNode().boundsInRoot.top
        assertTrue("Lower-priority lab-only group should precede the paired group ($labOnlyTop, $pairedTop)",
            labOnlyTop < pairedTop)

        compose.onNodeWithText("Below target · 2").performClick()
        compose.onNodeWithText("Programming Fundamentals").assertIsDisplayed()
        compose.onNodeWithText("Digital Circuits Lab").assertIsDisplayed()
        assertEquals(0, compose.onAllNodesWithText("Calculus").fetchSemanticsNodes().size)

        compose.onNodeWithContentDescription("Search by subject or code").performTextInput("Calculus")
        compose.onNodeWithContentDescription("Search by subject or code").performImeAction()
        compose.onNodeWithText("No courses match this search and filter.").assertIsDisplayed()
        compose.onNodeWithContentDescription("Clear search").performClick()

        compose.onNodeWithText("All courses · 4").performClick()
        compose.onNodeWithContentDescription("Search by subject or code").performTextInput("EE201L")
        compose.onNodeWithContentDescription("Search by subject or code").performImeAction()
        compose.onNodeWithText("Digital Circuits Lab").assertIsDisplayed()
        compose.onNodeWithText("LAB · EE201L").assertIsDisplayed()
        assertEquals(0, compose.onAllNodesWithText("Programming Fundamentals").fetchSemanticsNodes().size)
    }

    @Test fun coursesScreenEmptyStateStillRoutesToSyncOrReconnect() {
        var syncs = 0
        var portalOpens = 0
        val dashboard = DashboardView(compose.activity, object : DashboardView.Actions {
            override fun onSync() { syncs++ }
            override fun onPortal() { portalOpens++ }
            override fun onAcademicsPortal() = Unit
            override fun onTarget() = Unit
            override fun onTheme() = Unit
            override fun onPalette() = Unit
            override fun onAbout() = Unit
            override fun onFilter(belowOnly: Boolean) = Unit
            override fun onQuery(value: String) = Unit
            override fun onCourse(code: String) = Unit
        }, AppPalette.get(false, 0))
        val state = DashboardView.State().apply { attendanceLoaded = false }
        compose.activity.runOnUiThread {
            compose.activity.setContentView(dashboard)
            dashboard.update(state)
        }
        clickTab(dashboard, "Courses")
        compose.onNodeWithText("Your courses appear here after your first MyCamu sync. Tap to connect.").assertIsDisplayed()
        compose.onNodeWithContentDescription("Sync MyCamu to load course attendance").performClick()
        assertEquals(1, syncs)

        state.attendanceLoaded = true
        state.sessionExpired = true
        compose.activity.runOnUiThread { dashboard.update(state) }
        compose.onNodeWithText("MyCamu returned no course attendance. Tap here to sync again.").assertIsDisplayed()
        compose.onNodeWithContentDescription("Sync MyCamu to load course attendance").performClick()
        assertEquals(1, portalOpens)
    }

    @Test fun homeSyncSpinnerPausesOnHiddenTabAndResumesWhenVisible() {
        val dashboard = DashboardView(compose.activity, object : DashboardView.Actions {
            override fun onSync() = Unit
            override fun onPortal() = Unit
            override fun onAcademicsPortal() = Unit
            override fun onTarget() = Unit
            override fun onTheme() = Unit
            override fun onPalette() = Unit
            override fun onAbout() = Unit
            override fun onFilter(belowOnly: Boolean) = Unit
            override fun onQuery(value: String) = Unit
            override fun onCourse(code: String) = Unit
        }, AppPalette.get(false, 0))
        val state = DashboardView.State().apply { syncing = true }
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        lateinit var glyph: View
        instrumentation.runOnMainSync {
            compose.activity.setContentView(dashboard)
            dashboard.update(state)
            glyph = findByClassName(dashboard, "SyncGlyphView")!!
        }
        val spinField = glyph.javaClass.getDeclaredField("spin").apply { isAccessible = true }
        waitForAnimator(glyph, spinField) { it?.isRunning == true }
        instrumentation.runOnMainSync { findByDescription(dashboard, "Courses")!!.performClick() }
        waitForAnimator(glyph, spinField) { it?.isPaused == true }
        instrumentation.runOnMainSync { findByDescription(dashboard, "Home")!!.performClick() }
        waitForAnimator(glyph, spinField) { it?.isRunning == true }

        instrumentation.runOnMainSync {
            state.syncing = false
            dashboard.update(state)
        }
        waitForAnimator(glyph, spinField) { spinField.get(glyph) == null }
    }

    private fun testCourse(code: String, name: String, percent: Float, below: Boolean) =
        DashboardView.Course().apply {
            this.code = code
            this.name = name
            this.percent = percent
            percentLabel = "${percent.toInt()}%"
            attended = 16
            total = 20
            countsKnown = true
            status = if (below) "Below target" else if (percent == 75f) "At target" else "Above target"
            guidance = if (below) "Attend 12 classes to reach target" else "You can miss 1 more class"
            counts = "$attended attended / $total total"
            canMissText = if (below) "0" else "1"
            mustAttendText = if (below) "12" else "0"
            targetCount = if (below) "Attend 12 classes" else "Can miss 1"
            color = if (below) 0xffaa2921.toInt() else 0xff176146.toInt()
            this.below = below
        }

    private fun clickTab(root: View, description: String) {
        compose.activity.runOnUiThread {
            val tab = findByDescription(root, description)
            assertNotNull("Missing dashboard tab $description", tab)
            tab!!.performClick()
        }
        compose.waitForIdle()
    }

    private fun assertTabSelected(root: View, description: String) {
        compose.activity.runOnUiThread { assertNotNull(findByDescription(root, description)) }
    }

    private fun findByDescription(view: View, description: String): View? {
        if (view.contentDescription == description) return view
        if (view is ViewGroup) for (index in 0 until view.childCount) {
            findByDescription(view.getChildAt(index), description)?.let { return it }
        }
        return null
    }

    private fun findByClassName(view: View, simpleName: String): View? {
        if (view.javaClass.simpleName == simpleName) return view
        if (view is ViewGroup) for (index in 0 until view.childCount) {
            findByClassName(view.getChildAt(index), simpleName)?.let { return it }
        }
        return null
    }

    private fun waitForAnimator(
        glyph: View,
        field: java.lang.reflect.Field,
        condition: (ValueAnimator?) -> Boolean,
    ) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val deadline = SystemClock.uptimeMillis() + 5_000
        while (SystemClock.uptimeMillis() < deadline) {
            var matched = false
            instrumentation.runOnMainSync {
                matched = condition(field.get(glyph) as? ValueAnimator)
            }
            if (matched) return
            SystemClock.sleep(16)
        }
        assertTrue("Sync animator did not reach the expected state", false)
    }
}
