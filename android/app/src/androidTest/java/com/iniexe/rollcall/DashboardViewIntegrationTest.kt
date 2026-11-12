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
import android.view.View
import android.view.ViewGroup
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DashboardViewIntegrationTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

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
}
