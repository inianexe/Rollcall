package com.iniexe.rollcall

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import android.view.View
import android.view.ViewGroup
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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
