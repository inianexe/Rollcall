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
import androidx.compose.ui.test.performScrollTo
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
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
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
            repeat(4) { compose.onAllNodes(hasScrollAction())[0].performTouchInput { swipeUp() } }
            repeat(8) { compose.onAllNodes(hasScrollAction())[0].performTouchInput { swipeDown() } }
            compose.onNodeWithContentDescription("Search by subject or code").performTextInput("Name 17")
            compose.onNodeWithText("Long Course Name 17 Applied Computing").assertIsDisplayed()
            compose.onNodeWithContentDescription("Clear search").performClick()
            compose.onNodeWithText("Below target · ${state.below}").performClick()
            compose.onNodeWithText("All courses · ${state.courses}").performClick()
            repeat(8) { compose.onAllNodes(hasScrollAction())[0].performTouchInput { swipeDown() } }
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

    @Test fun weekScreenPreservesJavaScheduleExpansionHistoryAndOdNavigation() {
        var syncs = 0
        lateinit var dashboard: DashboardView
        val state = DashboardView.State().apply {
            timetableLoaded = true
            timetableUpdatedAt = System.currentTimeMillis()
            classMarksUpdatedAt = System.currentTimeMillis()
            leavesLoaded = true
            val now = System.currentTimeMillis()
            val today = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }
            val nextDay = (today.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 1) }
            val dateFormat = SimpleDateFormat("EEEE, d MMM", Locale.getDefault())
            val firstDate = dateFormat.format(today.time)
            val secondDate = dateFormat.format(nextDay.time)
            timetable = mutableListOf(
                DashboardView.ScheduleItem().apply { date = firstDate; startsAt = today.timeInMillis; allDay = true; name = "Orientation"; code = "ALLDAY"; time = "" },
                DashboardView.ScheduleItem().apply { date = firstDate; startsAt = now + 3_600_000; endsAt = now + 7_200_000; name = "Week Fixture Lecture"; code = "WK101"; time = "10:00 AM – 11:00 AM"; details = "Room 2 · Dr Tutor" },
                DashboardView.ScheduleItem().apply { date = firstDate; startsAt = now + 8_000_000; name = "Cancelled Tutorial"; code = "WK101T"; time = "12:00 PM – 1:00 PM"; cancelled = true },
                DashboardView.ScheduleItem().apply { date = secondDate; startsAt = nextDay.timeInMillis + 36_000_000; name = "Tomorrow Seminar"; code = "WK202"; time = "10:00 AM – 11:00 AM" },
            )
            classMarks = (0 until 32).map { index ->
                DashboardView.ClassMarkItem().apply {
                    day = if (index < 2) "Friday, 13 Nov 2026" else "Thursday, 12 Nov 2026"
                    time = "${9 + index % 8}:00 AM"
                    name = when (index) { 0 -> "Present Fixture"; 1 -> "Absent Fixture"; 2 -> "Not Marked Fixture"; 3 -> "Unknown Fixture"; 4 -> "Approved OD Fixture"; else -> "Older Fixture $index" }
                    code = "WK${index.toString().padStart(3, '0')}"
                    status = when (index) { 0 -> "present"; 1 -> "absent"; 2 -> "not_taken"; 3 -> "unable_to_confirm"; else -> "present" }
                    leaveType = if (index == 4) "On Duty" else ""
                    leaveStatus = if (index == 4) "Approved" else ""
                    color = when (status) { "present" -> 0xff24774f.toInt(); "absent" -> 0xffb5362a.toInt(); "not_taken" -> 0xffaa7413.toInt(); else -> 0xff73766e.toInt() }
                    startsAt = now - index * 3_600_000L
                }
            }.toMutableList()
            leaves = mutableListOf(DashboardView.LeaveItem().apply {
                id = "leave-fixture"; date = "13 Nov 2026"; type = "On Duty"; status = "Approved"; currentSemester = true
            })
        }
        dashboard = DashboardView(compose.activity, object : DashboardView.Actions {
            override fun onSync() { syncs++ }
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
        compose.activity.runOnUiThread { compose.activity.setContentView(dashboard); dashboard.update(state) }
        clickTab(dashboard, "Week")

        compose.onNodeWithText("Week Fixture Lecture", substring = true).assertIsDisplayed()
        compose.onNodeWithText("All day").assertIsDisplayed()
        compose.onNodeWithText("Cancelled").assertIsDisplayed()
        compose.onNodeWithText("Tomorrow Seminar").assertDoesNotExist()
        val tomorrowLabel = SimpleDateFormat("EEEE, d MMM", Locale.getDefault()).format(
            Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 1) }.time)
        compose.onNodeWithContentDescription("$tomorrowLabel, 1 class, expand").performClick()
        compose.onNodeWithContentDescription("$tomorrowLabel, 1 class, collapse").assertExists()
        val expanded = DashboardView::class.java.getDeclaredField("expandedScheduleDays").apply { isAccessible = true }
            .get(dashboard) as Set<*>
        assertTrue("Java should own the expanded day state", tomorrowLabel in expanded)
        val screenState = DashboardView::class.java.getDeclaredMethod("weekScreenState").apply { isAccessible = true }
            .invoke(dashboard)
        val mappedDays = screenState.javaClass.getDeclaredMethod("getScheduleDays").invoke(screenState) as List<*>
        val mappedTomorrow = mappedDays.first { it!!.javaClass.getDeclaredMethod("getDate").invoke(it) == tomorrowLabel }!!
        assertEquals(1, mappedTomorrow.javaClass.getDeclaredMethod("getClasses").invoke(mappedTomorrow).let { (it as List<*>).size })
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Tomorrow Seminar", substring = true).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Tomorrow Seminar", substring = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("$tomorrowLabel, 1 class, collapse").performClick()
        compose.onNodeWithText("Tomorrow Seminar", substring = true).assertDoesNotExist()

        compose.onNodeWithContentDescription("Show Attendance").performClick()
        compose.onNodeWithText("Attendance history").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Present Fixture, Present").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Absent Fixture, Absent").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Not Marked Fixture, Not marked").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Unknown Fixture, Unable to confirm").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("On Duty · Approved", substring = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Older Fixture 31").assertDoesNotExist()
        repeat(8) { compose.onAllNodes(hasScrollAction())[1].performTouchInput { swipeUp() } }
        compose.onNodeWithText("Show older · 2 more").assertIsDisplayed().performClick()
        compose.onNodeWithText("Older Fixture 31").performScrollTo().assertIsDisplayed()

        repeat(10) { compose.onAllNodes(hasScrollAction())[1].performTouchInput { swipeDown() } }
        compose.onNodeWithContentDescription("Open Leave and On Duty details").performClick()
        compose.activity.runOnUiThread {
            assertNotNull(findText(dashboard, "Leave & OD"))
            assertNotNull(findText(dashboard, "On Duty"))
            findText(dashboard, "←  Back")!!.performClick()
        }
        assertTabSelected(dashboard, "Week, selected")
        compose.onNodeWithText("Attendance history").assertIsDisplayed()

        compose.activity.runOnUiThread {
            state.classMarks[0].name = "Refreshed Present Fixture"
            dashboard.update(state)
        }
        compose.onNodeWithText("Refreshed Present Fixture").assertIsDisplayed()
        assertEquals(0, syncs)
    }

    @Test fun weekEmptyFailureAndExpiredStatesRemainDistinct() {
        var syncs = 0
        var portals = 0
        val dashboard = DashboardView(compose.activity, object : DashboardView.Actions {
            override fun onSync() { syncs++ }
            override fun onPortal() { portals++ }
            override fun onAcademicsPortal() = Unit
            override fun onTarget() = Unit
            override fun onTheme() = Unit
            override fun onPalette() = Unit
            override fun onAbout() = Unit
            override fun onFilter(belowOnly: Boolean) = Unit
            override fun onQuery(value: String) = Unit
            override fun onCourse(code: String) = Unit
        }, AppPalette.get(false, 0))
        val state = DashboardView.State()
        compose.activity.runOnUiThread { compose.activity.setContentView(dashboard); dashboard.update(state) }
        clickTab(dashboard, "Week")
        compose.onNodeWithText("YOUR WEEK", substring = true).assertIsDisplayed()
        compose.onNodeWithContentDescription("Sync timetable from MyCamu").performClick()
        assertEquals(1, syncs)
        fun status(name: String): String {
            val stateMethod = DashboardView::class.java.getDeclaredMethod("weekScreenState").apply { isAccessible = true }
            val uiState = requireNotNull(stateMethod.invoke(dashboard))
            val value = requireNotNull(uiState.javaClass.getDeclaredMethod("get${name}Status").invoke(uiState))
            return value.toString()
        }
        assertEquals("NOT_LOADED", status("Timetable"))

        state.syncError = true
        compose.activity.runOnUiThread { dashboard.update(state) }
        compose.onNodeWithText("NEEDS A", substring = true).assertIsDisplayed()
        compose.onNodeWithContentDescription("Sync timetable from MyCamu").assertIsDisplayed()
        assertEquals("SYNC_FAILED", status("Timetable"))

        state.sessionExpired = true
        compose.activity.runOnUiThread { dashboard.update(state) }
        compose.onNodeWithText("RECONNECT TO", substring = true).assertIsDisplayed()
        assertEquals("SESSION_EXPIRED", status("Timetable"))
        compose.onNodeWithContentDescription("Reconnect MyCamu to load the timetable").performClick()
        assertEquals(1, portals)

        compose.onNodeWithContentDescription("Show Attendance").performClick()
        compose.onNodeWithText("Reconnect MyCamu to load attendance history", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Reconnect MyCamu", substring = true).assertIsDisplayed()

        state.sessionExpired = false
        state.syncError = false
        state.timetableLoaded = true
        compose.activity.runOnUiThread { dashboard.update(state) }
        compose.onNodeWithContentDescription("Show Schedule").performClick()
        compose.onNodeWithText("NO CLASSES", substring = true).assertIsDisplayed()
        assertEquals("LOADED_EMPTY", status("Timetable"))

        state.timetable = mutableListOf(DashboardView.ScheduleItem().apply {
            date = "Tuesday, 17 Nov"; name = "Cached schedule fixture"; code = "CACHE1"; time = "9:00 AM – 10:00 AM"
        })
        state.syncError = true
        compose.activity.runOnUiThread { dashboard.update(state) }
        assertEquals("CACHED", status("Timetable"))
        compose.onNodeWithText("Cached schedule fixture", substring = true).assertIsDisplayed()
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

    private fun findText(view: View, text: String): View? {
        if (view is android.widget.TextView && view.text.toString() == text) return view
        if (view is ViewGroup) for (index in 0 until view.childCount) {
            findText(view.getChildAt(index), text)?.let { return it }
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
