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
class CoursesBenchmark {
    @get:Rule val rule = MacrobenchmarkRule()

    private val target = "com.iniexe.rollcall"
    private val activity = "$target.CoursesBenchmarkActivity"

    @Test fun scrolling() = measure {
        repeat(4) { swipeUp() }
        repeat(8) { swipeDown() }
    }

    @Test fun searchAndFilter() = measure {
        find(By.desc("Search by subject or code")).click()
        find(By.clazz("android.widget.EditText")).text = "Name 17"
        find(By.text("Long Course Name 17 Applied Computing"))
        find(By.desc("Clear search")).click()
        find(By.descContains("Below target")).click()
        find(By.descContains("All courses")).click()
    }

    @Test fun expansionAndNavigation() = measure {
        find(By.textContains("LAB ·")).click()
        find(By.textContains("LAB ·")).click()
        find(By.desc("Home")).click()
        find(By.desc("Week")).click()
        find(By.desc("Courses")).click()
    }

    @Test fun simpleListScrolling() = measure(simple = true) {
        repeat(4) { swipeUp() }
        repeat(8) { swipeDown() }
    }

    @Test fun coldStartup() = rule.measureRepeated(
        packageName = target,
        metrics = listOf(StartupTimingMetric()),
        compilationMode = CompilationMode.None(),
        startupMode = StartupMode.COLD,
        iterations = 5,
    ) { startActivityAndWait(fixtureIntent()) }

    private fun measure(simple: Boolean = false, interaction: UiDevice.() -> Unit) = rule.measureRepeated(
        packageName = target,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = CompilationMode.None(),
        startupMode = null,
        iterations = 5,
        setupBlock = {
            killProcess()
            startActivityAndWait(fixtureIntent(simple))
            if (!simple) device.find(By.desc("Courses")).click()
            device.waitForIdle()
        },
    ) {
        device.interaction()
        device.waitForIdle()
    }

    private fun fixtureIntent(simple: Boolean = false) = Intent().setClassName(target, activity)
        .putExtra("simple", simple)

    private fun UiDevice.find(selector: androidx.test.uiautomator.BySelector): UiObject2 {
        val found = wait(Until.findObject(selector), 5_000)
        assertNotNull("Missing benchmark control: $selector", found)
        return found!!
    }

    private fun UiDevice.swipeUp() = swipe(displayWidth / 2, displayHeight * 3 / 4,
        displayWidth / 2, displayHeight / 3, 16)

    private fun UiDevice.swipeDown() = swipe(displayWidth / 2, displayHeight / 3,
        displayWidth / 2, displayHeight * 3 / 4, 16)
}
