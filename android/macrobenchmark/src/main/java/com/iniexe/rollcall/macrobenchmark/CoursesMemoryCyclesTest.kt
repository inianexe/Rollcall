package com.iniexe.rollcall.macrobenchmark

import android.content.Intent
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CoursesMemoryCyclesTest {
    @Test fun repeatedWeekCyclesPss() {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        device.pressHome()
        val intent = Intent().setClassName("com.iniexe.rollcall", "com.iniexe.rollcall.CoursesBenchmarkActivity")
            .putExtra("week", true)
        InstrumentationRegistry.getInstrumentation().targetContext.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        find(device, By.text("Attendance")).click()
        find(device, By.text("Schedule")).click()
        device.waitForIdle()
        repeat(6) { cycle ->
            if (cycle > 0) {
                repeat(3) { device.swipe(device.displayWidth / 2, device.displayHeight * 3 / 4,
                    device.displayWidth / 2, device.displayHeight / 3, 16) }
                repeat(3) { device.swipe(device.displayWidth / 2, device.displayHeight / 3,
                    device.displayWidth / 2, device.displayHeight * 3 / 4, 16) }
                find(device, By.text("Attendance")).click()
                find(device, By.desc("Home")).click()
                find(device, By.desc("Week")).click()
                find(device, By.text("Schedule")).click()
                device.waitForIdle()
            }
            val pss = device.executeShellCommand("dumpsys meminfo com.iniexe.rollcall")
                .lineSequence().firstOrNull { it.contains("TOTAL PSS:") }
                ?.substringAfter("TOTAL PSS:")?.trim()?.substringBefore(' ')?.toIntOrNull()
            requireNotNull(pss) { "dumpsys meminfo did not report TOTAL PSS" }
            Log.i("RollcallWeekMemoryCycles", "sample=$cycle totalPssKb=$pss")
        }
    }

    @Test fun repeatedCourseCyclesPss() {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        device.pressHome()
        val intent = Intent().setClassName("com.iniexe.rollcall", "com.iniexe.rollcall.CoursesBenchmarkActivity")
        InstrumentationRegistry.getInstrumentation().targetContext.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        find(device, By.desc("Courses")).click()
        device.waitForIdle()
        repeat(6) { cycle ->
            if (cycle > 0) {
                repeat(3) { device.swipe(device.displayWidth / 2, device.displayHeight * 3 / 4,
                    device.displayWidth / 2, device.displayHeight / 3, 16) }
                find(device, By.desc("Home")).click()
                find(device, By.desc("Courses")).click()
                device.waitForIdle()
            }
            val pss = device.executeShellCommand("dumpsys meminfo com.iniexe.rollcall")
                .lineSequence().firstOrNull { it.contains("TOTAL PSS:") }
                ?.substringAfter("TOTAL PSS:")?.trim()?.substringBefore(' ')?.toIntOrNull()
            requireNotNull(pss) { "dumpsys meminfo did not report TOTAL PSS" }
            Log.i("RollcallMemoryCycles", "sample=$cycle totalPssKb=$pss")
        }
    }

    private fun find(device: UiDevice, selector: androidx.test.uiautomator.BySelector): UiObject2 {
        val found = device.wait(Until.findObject(selector), 5_000)
        assertNotNull("Missing memory-cycle control: $selector", found)
        return found!!
    }
}
