package com.iniexe.rollcall.macrobenchmark

import android.content.Intent
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
class WeekBenchmark {
    @get:Rule val rule = MacrobenchmarkRule()

    private val target = "com.iniexe.rollcall"
    private val activity = "$target.CoursesBenchmarkActivity"

    @Test fun initialRendering() = rule.measureRepeated(
        packageName = target,
        metrics = listOf(StartupTimingMetric()),
        compilationMode = CompilationMode.None(),
        startupMode = StartupMode.COLD,
        iterations = 5,
    ) { startActivityAndWait(weekIntent()) }

    @Test fun scheduleScrolling() = measure { repeat(4) { swipeUp() }; repeat(4) { swipeDown() } }

    @Test fun dayExpansion() = measure {
        find(By.descContains("collapse")).click()
        find(By.descContains("expand")).click()
    }

    @Test fun scheduleAttendanceSwitching() = measure {
        find(By.text("Attendance")).click()
        find(By.text("Schedule")).click()
        find(By.text("Attendance")).click()
        find(By.text("Schedule")).click()
    }

    @Test fun attendanceHistoryScrolling() = measure {
        find(By.text("Attendance")).click()
        repeat(8) { swipeUp() }
        repeat(8) { swipeDown() }
    }

    @Test fun historyPagination() = measure {
        find(By.text("Attendance")).click()
        repeat(10) { swipeUp() }
        find(By.textContains("Show older")).click()
        waitForIdle()
    }

    @Test fun tabNavigation() = measure {
        find(By.desc("Courses")).click()
        find(By.desc("Week")).click()
        find(By.desc("Home")).click()
        find(By.desc("Week")).click()
    }

    private fun measure(interaction: UiDevice.() -> Unit) = rule.measureRepeated(
        packageName = target,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = CompilationMode.None(),
        startupMode = null,
        iterations = 5,
        setupBlock = {
            killProcess()
            startActivityAndWait(weekIntent())
            device.waitForIdle()
        },
    ) {
        device.interaction()
        device.waitForIdle()
    }

    private fun weekIntent() = Intent().setClassName(target, activity).putExtra("week", true)

    private fun UiDevice.find(selector: androidx.test.uiautomator.BySelector): UiObject2 {
        val found = wait(Until.findObject(selector), 5_000)
        assertNotNull("Missing Week benchmark control: $selector", found)
        return found!!
    }

    private fun UiDevice.swipeUp() = swipe(displayWidth / 2, displayHeight * 3 / 4,
        displayWidth / 2, displayHeight / 3, 16)

    private fun UiDevice.swipeDown() = swipe(displayWidth / 2, displayHeight / 3,
        displayWidth / 2, displayHeight * 3 / 4, 16)
}
